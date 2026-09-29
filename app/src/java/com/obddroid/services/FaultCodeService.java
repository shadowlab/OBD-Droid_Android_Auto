package com.obddroid.services;

import android.os.SystemClock;

import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.ecu.ObdCodeList;
import com.obddroid.interfaces.RawTelegramListener;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdOnUds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Isolated fault code scanning service using Mode 03 (current codes) and Mode 07 (pending codes).
 *
 * DESIGN PRINCIPLE: This service is completely isolated from the rest of the app.
 * - Does NOT modify ObdProt.tCodes or existing fault code flow
 * - Only used by Fault Codes page (FaultCodesActivity)
 * - Can be disabled without affecting any other functionality
 *
 * HOW IT WORKS:
 * 1. Sends Mode 03 request (read current/confirmed fault codes)
 * 2. Sends Mode 07 request (read pending fault codes)
 * 3. Parses DTC responses (e.g., "43 02 01 33 00 00" → "P0133")
 * 4. Returns list of fault codes via CompletableFuture
 * 5. Can clear codes using Mode 04
 */
public class FaultCodeService implements RawTelegramListener {

    private static final Logger log = Logger.getLogger(FaultCodeService.class.getSimpleName());
    private static final String PROMPT = ">";
    private static final long SCAN_TIMEOUT_MS = 5_000L;  // Increased for slower adapters & multiline responses
    private static final long CLEAR_TIMEOUT_MS = 2_500L;  // Increased for reliability

    private enum ScanMode { CONFIRMED, PENDING, PERMANENT }

    // Execution and state management
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FaultCodeService");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile CompletableFuture<List<FaultCodeInfo>> currentScan;

    // Response synchronization
    private final Object responseLock = new Object();
    private final StringBuilder responseBuffer = new StringBuilder();
    private final AtomicBoolean expectingResponse = new AtomicBoolean(false);
    private boolean waitingForPrompt;

    /**
     * Fault code type
     */
    public enum CodeType {
        CONFIRMED,    // Mode 03 - Active codes that triggered MIL (Stored)
        PENDING,      // Mode 07 - Potential issues not yet confirmed
        PERMANENT     // Mode 0A - Emissions-related codes that can't be cleared
    }

    /**
     * Fault code information
     */
    public static class FaultCodeInfo {
        public final String code;
        public final String description;
        public final boolean isPending;
        public final boolean hasFreeze;
        public final int dtcNumber;
        public final CodeType type;

        public FaultCodeInfo(String code, String description, boolean isPending, boolean hasFreeze, int dtcNumber) {
            this(code, description, isPending, hasFreeze, dtcNumber,
                 isPending ? CodeType.PENDING : CodeType.CONFIRMED);
        }

        public FaultCodeInfo(String code, String description, boolean isPending, boolean hasFreeze, int dtcNumber, CodeType type) {
            this.code = code;
            this.description = description;
            this.isPending = isPending;
            this.hasFreeze = hasFreeze;
            this.dtcNumber = dtcNumber;
            this.type = type;
        }
    }

    /**
     * Scan for confirmed (Mode 03) fault codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanFaultCodes() {
        return startScan(EnumSet.of(ScanMode.CONFIRMED));
    }

    /**
     * Scan for pending (Mode 07) fault codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanPendingCodes() {
        return startScan(EnumSet.of(ScanMode.PENDING));
    }

    /**
     * Scan for permanent (Mode 0A) fault codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanPermanentCodes() {
        return startScan(EnumSet.of(ScanMode.PERMANENT));
    }

    /**
     * Scan for all types of fault codes in a single request chain.
     * Includes confirmed (Mode 03), pending (Mode 07), and permanent (Mode 0A) codes.
     */
    public CompletableFuture<List<FaultCodeInfo>> scanAllCodes() {
        return startScan(EnumSet.of(ScanMode.CONFIRMED, ScanMode.PENDING, ScanMode.PERMANENT));
    }

    /**
     * Clear all fault codes using Mode 04.
     *
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> clearFaultCodes() {
        if (CommService.elm == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Clearing fault codes (Mode 04)");
                CommService.elm.addRawTelegramListener(this);
                try {
                    String response = sendAndAwait("04", CLEAR_TIMEOUT_MS);
                    log.info("Clear response: " + response);
                    String upper = response.toUpperCase(Locale.US);
                    // Accept various success responses:
                    // - "44" (standard positive response = 0x40 + 0x04)
                    // - "STOPPED" (ECU reset after clearing)
                    // - "OK", "NODATA" (some adapters)
                    // - Empty response (some adapters)
                    return upper.contains("44")
                        || upper.contains("STOPPED")
                        || upper.contains("OK")
                        || upper.contains("NODATA")
                        || response.trim().isEmpty();
                } finally {
                    CommService.elm.removeRawTelegramListener(this);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (TimeoutException e) {
                throw new CompletionException(e);
            }
        }, scanExecutor);
    }

    /**
     * Start scanning based on the requested modes.
     */
    private CompletableFuture<List<FaultCodeInfo>> startScan(EnumSet<ScanMode> modes) {
        if (CommService.elm == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ELM not available"));
        }

        // Check connection state before scanning
        ElmProt.STAT status = CommService.elm.getStatus();
        if (status != ElmProt.STAT.CONNECTED && status != ElmProt.STAT.ECU_DETECTED && status != ElmProt.STAT.ECU_SELECTED) {
            log.warning("Cannot scan - connection not stable. Current status: " + status);
            return CompletableFuture.failedFuture(
                new IllegalStateException("Connection not ready for scanning. Status: " + status)
            );
        }

        if (!isScanning.compareAndSet(false, true)) {
            log.warning("Scan already in progress – returning existing future");
            return currentScan != null
                ? currentScan
                : CompletableFuture.failedFuture(new IllegalStateException("Scan already in progress"));
        }

        CompletableFuture<List<FaultCodeInfo>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeScan(modes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (TimeoutException e) {
                throw new CompletionException(e);
            }
        }, scanExecutor);

        currentScan = future;
        future.whenComplete((result, throwable) -> {
            isScanning.set(false);
            expectingResponse.set(false);
            synchronized (responseLock) {
                waitingForPrompt = false;
                responseBuffer.setLength(0);
                responseLock.notifyAll();
            }
        });

        return future;
    }

    /**
     * Execute the actual scan sequence synchronously on the scan executor thread.
     */
    private List<FaultCodeInfo> executeScan(EnumSet<ScanMode> modes) throws InterruptedException, TimeoutException {
        log.info(() -> "Starting fault code scan for modes: " + modes);

        // CRITICAL: Save current service and disable OBD polling before querying fault codes
        // Fault code commands (Mode 03/07/0A) are DIRECT commands that conflict with
        // active polling services (Mode 01/09). If a service is active, the adapter
        // returns "STOPPED" when we try to send fault code commands.
        int previousService = CommService.elm.getService();
        boolean serviceWasSwitched = previousService != com.obddroid.obd.ObdProt.OBD_SVC_NONE;

        if (serviceWasSwitched) {
            log.info("Temporarily disabling OBD service " + com.obddroid.obd.ObdProt.getServiceName(previousService) +
                     " to query fault codes");
            CommService.elm.setService(com.obddroid.obd.ObdProt.OBD_SVC_NONE);
            // Give adapter time to stop polling before sending direct commands
            Thread.sleep(100);
        }

        List<FaultCodeInfo> results = new ArrayList<>();
        CommService.elm.addRawTelegramListener(this);
        try {
            if (modes.contains(ScanMode.CONFIRMED)) {
                String response = sendAndAwaitWithRetry("03", SCAN_TIMEOUT_MS, 2);
                log.info("Mode 03 RAW response: [" + response + "]");
                List<FaultCodeInfo> confirmed = parseFaultCodes(response, CodeType.CONFIRMED);
                log.info("Mode 03 parsed " + confirmed.size() + " codes");
                results.addAll(confirmed);
            }
            if (modes.contains(ScanMode.PENDING)) {
                String response = sendAndAwaitWithRetry("07", SCAN_TIMEOUT_MS, 2);
                log.info("Mode 07 RAW response: [" + response + "]");
                List<FaultCodeInfo> pending = parseFaultCodes(response, CodeType.PENDING);
                log.info("Mode 07 parsed " + pending.size() + " codes");
                results.addAll(pending);
            }
            if (modes.contains(ScanMode.PERMANENT)) {
                String response = sendAndAwaitWithRetry("0A", SCAN_TIMEOUT_MS, 2);
                log.info("Mode 0A RAW response: [" + response + "]");
                List<FaultCodeInfo> permanent = parseFaultCodes(response, CodeType.PERMANENT);
                log.info("Mode 0A parsed " + permanent.size() + " codes");
                results.addAll(permanent);
            }
        } finally {
            CommService.elm.removeRawTelegramListener(this);

            // Restore previous service if we switched it
            if (serviceWasSwitched) {
                log.info("Restoring OBD service " + com.obddroid.obd.ObdProt.getServiceName(previousService));
                CommService.elm.setService(previousService);
            }
        }

        // Sort by type priority (permanent > confirmed > pending), then alphabetically
        results.sort(Comparator
            .comparing((FaultCodeInfo code) -> getTypePriority(code.type))
            .thenComparing(code -> code.code));

        log.info(() -> "Scan complete. Found " + results.size() + " codes.");
        return results;
    }

    private int getTypePriority(CodeType type) {
        switch (type) {
            case PERMANENT: return 0;  // Highest priority
            case CONFIRMED: return 1;
            case PENDING: return 2;    // Lowest priority
            default: return 3;
        }
    }

    /**
     * Send a raw command and wait for the trailing prompt.
     */
    private String sendAndAwait(String command, long timeoutMs) throws InterruptedException, TimeoutException {
        synchronized (responseLock) {
            responseBuffer.setLength(0);
            waitingForPrompt = true;
            expectingResponse.set(true);
        }

        sendRawCommand(command);

        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        synchronized (responseLock) {
            while (waitingForPrompt) {
                long remaining = deadline - SystemClock.uptimeMillis();
                if (remaining <= 0) {
                    waitingForPrompt = false;
                    expectingResponse.set(false);
                    throw new TimeoutException("Timed out waiting for response to command " + command);
                }
                responseLock.wait(remaining);
            }
            expectingResponse.set(false);
            String response = responseBuffer.toString().trim();
            // OBD on UDS vehicle: raw lines answer the translated UDS request, convert to classic format
            if (CommService.elm != null && CommService.elm.isObdOnUds()) {
                String classic = ObdOnUds.rawResponseToClassic(response, command);
                log.fine(() -> "OBD on UDS response [" + response + "] -> [" + classic + "]");
                return classic;
            }
            return response;
        }
    }

    /**
     * Send a raw command with retry logic for invalid responses.
     * Retries when adapter returns "STOPPED", "?", or empty responses (adapter transitioning modes).
     */
    private String sendAndAwaitWithRetry(String command, long timeoutMs, int maxRetries) throws InterruptedException, TimeoutException {
        int attempt = 0;
        String response;

        while (true) {
            try {
                response = sendAndAwait(command, timeoutMs);
                String cleanResponse = response.replaceAll("[\\s\\[\\]]", "").toUpperCase(Locale.US);

                // Check if response is invalid (adapter transitioning/not ready)
                // Use contains() to handle responses like "[STOPPED]", "STOPPED ?", etc.
                boolean isInvalid = cleanResponse.isEmpty()
                    || cleanResponse.contains("STOPPED")
                    || cleanResponse.equals("?")
                    || cleanResponse.contains("ERROR");

                if (isInvalid && attempt < maxRetries) {
                    attempt++;
                    log.warning(String.format("Got invalid response '%s' for command %s (attempt %d/%d), retrying after delay...",
                        response, command, attempt, maxRetries + 1));
                    // Wait 500ms for adapter to stabilize before retrying
                    Thread.sleep(500);
                    continue;
                }

                // Either got valid response or exhausted retries
                if (isInvalid && attempt >= maxRetries) {
                    log.warning(String.format("Exhausted %d retries for command %s, last response: '%s'",
                        maxRetries + 1, command, response));
                }

                return response;

            } catch (TimeoutException e) {
                if (attempt < maxRetries) {
                    attempt++;
                    log.warning(String.format("Timeout for command %s (attempt %d/%d), retrying...",
                        command, attempt, maxRetries + 1));
                    Thread.sleep(500);
                    continue;
                }
                throw e;
            }
        }
    }

    /**
     * Send raw command to ELM adapter.
     */
    private void sendRawCommand(String command) {
        if (CommService.elm != null) {
            log.fine(() -> "TX: " + command);
            CommService.elm.sendTelegram(command.toCharArray());
        }
    }

    /**
     * Handle incoming RAW telegram responses from ELM adapter.
     */
    @Override
    public int handleRawTelegram(char[] buffer) {
        String response = new String(buffer).trim();
        if (response.isEmpty()) {
            return buffer.length;
        }

        log.finest(() -> "RX: " + response);

        if (!expectingResponse.get()) {
            return buffer.length;
        }

        if (response.startsWith("AT") || response.startsWith("SEARCHING") || response.startsWith("OK")) {
            return buffer.length;
        }

        synchronized (responseLock) {
            if (PROMPT.equals(response)) {
                waitingForPrompt = false;
                responseLock.notifyAll();
            } else {
                responseBuffer.append(response).append(' ');
            }
        }

        return buffer.length;
    }

    /**
     * Parse fault codes from a response string.
     */
    private List<FaultCodeInfo> parseFaultCodes(String response, CodeType codeType) {
        List<FaultCodeInfo> codes = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        log.info("parseFaultCodes called for " + codeType + " with response: [" + response + "]");

        if (response == null || response.isEmpty()) {
            log.info("Response is null or empty, returning 0 codes");
            return codes;
        }

        String cleanData = response.replaceAll("\\s+", "").toUpperCase(Locale.US);
        log.info("Cleaned data: [" + cleanData + "]");

        if (cleanData.contains("NODATA") || cleanData.length() < 4) {
            log.info("Response contains NODATA or too short, returning 0 codes");
            return codes;
        }

        // Determine mode response prefix based on code type
        // NOTE: Some vehicles respond to all modes with 43 prefix, so try multiple formats
        String[] possiblePrefixes;
        switch (codeType) {
            case CONFIRMED:
                possiblePrefixes = new String[]{"43"};
                break;
            case PENDING:
                possiblePrefixes = new String[]{"47", "43"};  // Try 47 first, fallback to 43
                break;
            case PERMANENT:
                possiblePrefixes = new String[]{"4A", "4C", "43"};  // Try multiple formats
                break;
            default:
                possiblePrefixes = new String[]{"43"};
        }

        boolean isPending = (codeType == CodeType.PENDING);

        // Try each possible prefix
        int cursor = -1;
        String foundPrefix = null;
        for (String prefix : possiblePrefixes) {
            cursor = cleanData.indexOf(prefix);
            if (cursor >= 0) {
                foundPrefix = prefix;
                log.info("Found response with prefix: " + prefix);
                break;
            }
        }

        while (cursor >= 0 && cursor + 4 <= cleanData.length()) {
            int count;
            try {
                // Parse count byte: bits 0-6 = DTC count, bit 7 = MIL status
                int countByte = Integer.parseInt(cleanData.substring(cursor + 2, cursor + 4), 16);
                count = countByte & 0x7F;  // Mask off MIL bit (bit 7) to get actual DTC count
                boolean milOn = (countByte & 0x80) != 0;
                log.fine(() -> String.format("DTC count byte: 0x%02X (count=%d, MIL=%s)", countByte, count, milOn ? "ON" : "OFF"));
            } catch (NumberFormatException ex) {
                log.log(Level.WARNING, "Invalid DTC count in response segment: " + cleanData, ex);
                break;
            }

            int index = cursor + 4;
            for (int i = 0; i < count && index + 4 <= cleanData.length(); i++) {
                String dtcHex = cleanData.substring(index, index + 4);
                index += 4;

                try {
                    int dtcValue = Integer.parseInt(dtcHex, 16);
                    if (dtcValue == 0 || !seen.add(dtcValue)) {
                        continue;
                    }
                    String dtcCode = convertToDtcCode(dtcValue);
                    String description = getDtcDescription(dtcValue);
                    boolean hasFreeze = (codeType == CodeType.CONFIRMED); // Only confirmed codes have freeze frame
                    codes.add(new FaultCodeInfo(dtcCode, description, isPending, hasFreeze, dtcValue, codeType));
                } catch (NumberFormatException ex) {
                    log.log(Level.WARNING, "Failed to parse DTC value: " + dtcHex, ex);
                }
            }

            cursor = foundPrefix != null ? cleanData.indexOf(foundPrefix, index) : -1;
        }

        return codes;
    }

    /**
     * Convert DTC value to standard code format.
     */
    private String convertToDtcCode(int dtcValue) {
        int prefix = (dtcValue >> 14) & 0x03;
        char prefixChar;
        switch (prefix) {
            case 0: prefixChar = 'P'; break;
            case 1: prefixChar = 'C'; break;
            case 2: prefixChar = 'B'; break;
            case 3: prefixChar = 'U'; break;
            default: prefixChar = 'P'; break;
        }

        int codeValue = dtcValue & 0x3FFF;
        return String.format(Locale.US, "%c%04X", prefixChar, codeValue);
    }

    /**
     * Get human-readable description for DTC code from the database.
     */
    private String getDtcDescription(int dtcValue) {
        try {
            ObdCodeList codeList = ObdCodeList.getInstance();
            if (codeList != null) {
                EcuCodeItem item = codeList.get(dtcValue);
                if (item != null) {
                    Object description = item.get(EcuCodeItem.FID_DESCRIPT);
                    if (description != null && !description.toString().isEmpty()) {
                        return description.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to lookup DTC description for: " + dtcValue, e);
        }
        return "Unknown fault code";
    }

    /**
     * Check if scan is currently in progress.
     */
    public boolean isScanning() {
        return isScanning.get();
    }
}
