package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.interfaces.TelegramWriter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ECU detection against simulated adapters, including vehicles that only
 * answer OBD on UDS (SAE J1979-2) requests, like the Toyota bZ / Subaru
 * Solterra (2026+) traced with Car Scanner.
 */
public class ElmProtEcuDetectTest {

    /**
     * Simulated ELM327: answers AT commands with OK (ATZ with its model),
     * tracks the selected protocol and TX header, and answers vehicle
     * requests with NO DATA unless the answering rule returns response lines.
     */
    private static class SimAdapter implements TelegramWriter {
        interface Vehicle {
            /** response lines for a request, or null for NO DATA */
            String[] answer(String request, String protocol, String header);
        }

        final List<String> sent = new ArrayList<>();
        final ConcurrentLinkedQueue<String> replies = new ConcurrentLinkedQueue<>();
        final Vehicle vehicle;
        String protocol = "0";
        String header = "";
        boolean headersOn = false;

        SimAdapter(Vehicle vehicle) {
            this.vehicle = vehicle;
        }

        @Override
        public int writeTelegram(char[] buffer) {
            return writeTelegram(buffer, 0, null);
        }

        @Override
        public synchronized int writeTelegram(char[] buffer, int type, Object id) {
            String cmd = new String(buffer).trim();
            sent.add(cmd);
            if (cmd.equals("ATZ")) {
                protocol = "0";
                header = "";
                replies.add("ELM327v2.3");
            } else if (cmd.startsWith("ATSP")) {
                protocol = cmd.substring(4);
                replies.add("OK");
            } else if (cmd.startsWith("ATSH")) {
                header = cmd.substring(4);
                replies.add("OK");
            } else if (cmd.startsWith("ATH")) {
                headersOn = cmd.equals("ATH1");
                replies.add("OK");
            } else if (cmd.startsWith("AT")) {
                replies.add("OK");
            } else {
                String[] lines = vehicle.answer(cmd, protocol, headersOn ? header : null);
                if (lines == null) {
                    replies.add("NODATA");
                } else {
                    for (String line : lines) {
                        replies.add(line);
                    }
                }
            }
            replies.add(">");
            return buffer.length;
        }

        synchronized long count(String cmd) {
            return sent.stream().filter(cmd::equals).count();
        }

        synchronized boolean sentAny(String cmd) {
            return sent.contains(cmd);
        }
    }

    /** Vehicle that never answers (e.g. EV without OBD-II, or switched off). */
    private static final SimAdapter.Vehicle SILENT = (request, protocol, header) -> null;

    /** Deliver queued adapter replies until the protocol is idle for idleMs (at most 20 s). */
    private static void pump(ElmProt elm, SimAdapter adapter, long idleMs) throws InterruptedException {
        pump(elm, adapter, idleMs, 20000);
    }

    /** Deliver queued adapter replies until idle for idleMs or maxMs has passed. */
    private static void pump(ElmProt elm, SimAdapter adapter, long idleMs, long maxMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        long idleSince = start;
        while (System.currentTimeMillis() - idleSince < idleMs
                && System.currentTimeMillis() - start < maxMs) {
            String reply = adapter.replies.poll();
            if (reply != null) {
                elm.handleTelegram(reply.toCharArray());
                idleSince = System.currentTimeMillis();
            } else {
                Thread.sleep(20);
            }
        }
    }

    /** Start a session as after connecting: adapter reports its model. */
    private static void connect(ElmProt elm) {
        elm.handleTelegram("ELM327v2.3".toCharArray());
        elm.handleTelegram(">".toCharArray());
    }

    @Test
    public void silentVehicleTriesAllStagesThenReportsNotResponding() throws Exception {
        ElmProt elm = new ElmProt();
        SimAdapter adapter = new SimAdapter(SILENT);
        elm.addTelegramWriter(adapter);

        connect(elm);
        // idle window longer than the restart delay, so every cycle gets to run
        pump(elm, adapter, 3000);

        // 3 cycles: classic 0100, then 22F400 on CAN 11 bit, CAN 29 bit, Toyota EV address
        assertEquals("classic detection requests", 3, adapter.count("0100"));
        assertEquals("OBD on UDS detection requests", 9, adapter.count("22F400"));
        assertTrue(elm.isVehicleNotResponding());
        assertFalse(elm.isObdOnUds());
        assertEquals(ElmProt.STAT.NODATA, elm.getStatus());

        // no further attempts once the vehicle is flagged as not responding
        pump(elm, adapter, 2500);
        assertEquals("no retries after giving up", 3, adapter.count("0100"));
    }

    @Test
    public void reinitializingClearsNotRespondingFlag() throws Exception {
        ElmProt elm = new ElmProt();
        SimAdapter adapter = new SimAdapter(SILENT);
        elm.addTelegramWriter(adapter);

        connect(elm);
        pump(elm, adapter, 3000);
        assertTrue(elm.isVehicleNotResponding());

        // reconnect: adapter reports its model again
        elm.handleTelegram("ELM327v2.3".toCharArray());
        assertFalse(elm.isVehicleNotResponding());
    }

    @Test
    public void classicVehicleStaysOnService01() throws Exception {
        ElmProt elm = new ElmProt();
        SimAdapter adapter = new SimAdapter((request, protocol, header) ->
                request.equals("0100") ? new String[]{"7E8064100BE3EB811"} : null);
        elm.addTelegramWriter(adapter);

        connect(elm);
        pump(elm, adapter, 500);

        assertEquals(ElmProt.STAT.ECU_DETECTED, elm.getStatus());
        assertFalse(elm.isObdOnUds());
        assertEquals("no OBD on UDS requests", 0, adapter.count("22F400"));
    }

    @Test
    public void functionalObdOnUdsVehicleIsDetectedOnCan29() throws Exception {
        ElmProt elm = new ElmProt();
        // answers 22F400 only as a 29 bit functional request (18DB33F1)
        SimAdapter adapter = new SimAdapter((request, protocol, header) ->
                request.equals("22F400") && protocol.equals("7") && header.equals("DB33F1")
                        ? new String[]{"18DAF1620762F400BE3EB811"} : null);
        elm.addTelegramWriter(adapter);
        AtomicReference<Object> ecus = captureEcuAddresses(elm);

        connect(elm);
        pump(elm, adapter, 500);

        assertEquals(ElmProt.STAT.ECU_DETECTED, elm.getStatus());
        assertTrue(elm.isObdOnUds());
        assertFalse(elm.isVehicleNotResponding());
        assertEcuDetected(ecus, 0x18DAF162);

        // service 01 requests are now sent as DIDs F4xx
        elm.sendTelegram("010D".toCharArray());
        assertTrue(adapter.sentAny("22F40D"));
    }

    @Test
    public void toyotaEvIsDetectedWithPhysicalAddressing() throws Exception {
        ElmProt elm = new ElmProt();
        // answers only requests addressed to ECU 0x5A from tester 0xE0, like the traced bZ
        SimAdapter adapter = new SimAdapter((request, protocol, header) ->
                request.equals("22F400") && protocol.equals("7") && header.equals("DA5AE0")
                        ? new String[]{"18DAE05A0762F400BE3EB811"} : null);
        elm.addTelegramWriter(adapter);
        AtomicReference<Object> ecus = captureEcuAddresses(elm);

        connect(elm);
        pump(elm, adapter, 500);

        assertEquals(ElmProt.STAT.ECU_DETECTED, elm.getStatus());
        assertTrue(elm.isObdOnUds());
        assertEcuDetected(ecus, 0x18DAE05A);
        // the header set for detection stays in place for data requests
        assertEquals("DA5AE0", adapter.header);
    }

    @Test
    public void obdOnUdsLiveDataIsDecodedAsService01() throws Exception {
        ElmProt elm = new ElmProt();
        // 29 bit functional OBD on UDS vehicle supporting only PID 0D (vehicle speed = 42 km/h);
        // every other speed request gets NO DATA
        java.util.concurrent.atomic.AtomicInteger speedRequests = new java.util.concurrent.atomic.AtomicInteger();
        SimAdapter adapter = new SimAdapter((request, protocol, header) -> {
            if (!protocol.equals("7")) {
                return null;
            }
            String payload;
            if (request.equals("22F400")) {
                payload = "62F40000080000";
            } else if (request.equals("22F40D")) {
                if (speedRequests.incrementAndGet() % 2 == 0) {
                    return null;
                }
                payload = "62F40D2A";
            } else {
                return new String[]{"7F2231"};
            }
            // headers on: 29 bit header + single frame PCI, as the traced vehicle sends
            return new String[]{header != null
                    ? String.format("18DAF162%02X%s", payload.length() / 2, payload)
                    : payload};
        });
        elm.addTelegramWriter(adapter);

        connect(elm);
        pump(elm, adapter, 500);
        assertTrue(elm.isObdOnUds());

        elm.setService(ObdProt.OBD_SVC_DATA);
        // live data polls continuously, so run for a fixed time
        pump(elm, adapter, 500, 1500);
        elm.setService(ObdProt.OBD_SVC_NONE);

        assertTrue("speed requested as DID F40D", adapter.sentAny("22F40D"));
        assertTrue("polling continued after NO DATA", speedRequests.get() > 2);
        // NO DATA recovery restores the detected protocol, not the preferred (automatic) one
        assertEquals("7", adapter.protocol);
        Object speed = null;
        for (Object pv : ObdProt.PidPvs.values()) {
            if (pv instanceof com.obddroid.ecu.EcuDataPv
                    && Integer.valueOf(0x0D).equals(((com.obddroid.ecu.EcuDataPv) pv).get(com.obddroid.ecu.EcuDataPv.FID_PID))) {
                speed = ((com.obddroid.ecu.EcuDataPv) pv).get(com.obddroid.ecu.EcuDataPv.FID_VALUE);
            }
        }
        assertNotNull("vehicle speed decoded " + ObdProt.PidPvs.values(), speed);
        assertEquals(42.0, ((Number) speed).doubleValue(), 0.01);
    }

    /** ELM327 output of an ISO-TP response with headers off: single line, or length + indexed lines */
    private static String[] isoTpLines(String payload) {
        int len = payload.length() / 2;
        if (len <= 7) {
            return new String[]{payload};
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%03X", len));
        lines.add("0:" + payload.substring(0, 12));
        int idx = 1;
        for (int pos = 12; pos < payload.length(); pos += 14, idx++) {
            String chunk = payload.substring(pos, Math.min(pos + 14, payload.length()));
            // pad the last frame like a vehicle would
            while (chunk.length() < 14) {
                chunk += "55";
            }
            lines.add(String.format("%X:", idx % 16) + chunk);
        }
        return lines.toArray(new String[0]);
    }

    @Test
    public void obdOnUdsFaultCodesAndVinAreDecodedAsClassicServices() throws Exception {
        final String vin = "JTMAB3FV3RD123456";
        StringBuilder vinHex = new StringBuilder();
        for (char c : vin.toCharArray()) {
            vinHex.append(String.format("%02X", (int) c));
        }
        ElmProt elm = new ElmProt();
        // 29 bit functional OBD on UDS vehicle with stored DTCs P0301, P0420 and a VIN
        SimAdapter adapter = new SimAdapter((request, protocol, header) -> {
            if (!protocol.equals("7")) {
                return null;
            }
            switch (request) {
                case "22F400":
                    return new String[]{header != null ? "18DAF1620762F40000080000" : "62F40000080000"};
                case "19423308FF":
                    // 59 42 33 DSAM DSevAM DFI + 2 records (severity, 3 byte DTC, status)
                    return isoTpLines("594233FF1E04" + "2003010008" + "2004200008");
                case "22F800":
                    // info type 02 (VIN) supported
                    return isoTpLines("62F80040000000");
                case "22F802":
                    return isoTpLines("62F802" + vinHex);
                default:
                    return new String[]{"7F" + request.substring(0, 2) + "31"};
            }
        });
        elm.addTelegramWriter(adapter);

        connect(elm);
        pump(elm, adapter, 500);
        assertTrue(elm.isObdOnUds());

        // stored fault codes (service 03 -> 19 42 33 08 FF)
        ObdProt.tCodes.clear();
        elm.setService(ObdProt.OBD_SVC_READ_CODES);
        pump(elm, adapter, 500, 1500);
        elm.setService(ObdProt.OBD_SVC_NONE);
        assertTrue("fault codes requested via ReadDTCInformation", adapter.sentAny("19423308FF"));
        assertTrue("P0301 decoded " + ObdProt.tCodes.keySet(), ObdProt.tCodes.containsKey(0x0301));
        assertTrue("P0420 decoded " + ObdProt.tCodes.keySet(), ObdProt.tCodes.containsKey(0x0420));

        // VIN (service 09 info type 02 -> 22 F8 02)
        elm.setService(ObdProt.OBD_SVC_VEH_INFO);
        pump(elm, adapter, 500, 1500);
        elm.setService(ObdProt.OBD_SVC_NONE);
        assertTrue("VIN requested as DID F802", adapter.sentAny("22F802"));
        boolean vinFound = false;
        for (Object pv : ObdProt.VidPvs.values()) {
            if (pv instanceof com.obddroid.ecu.EcuDataPv
                    && String.valueOf(((com.obddroid.ecu.EcuDataPv) pv).get(com.obddroid.ecu.EcuDataPv.FID_VALUE)).contains(vin)) {
                vinFound = true;
            }
        }
        assertTrue("VIN decoded " + ObdProt.VidPvs.values(), vinFound);
    }

    @Test
    public void obdOnUdsFreezeFrameIsDecodedAsService02() throws Exception {
        ElmProt elm = new ElmProt();
        // 29 bit functional OBD on UDS vehicle with a freeze frame for P0301 (speed 0x38 = 56 km/h)
        SimAdapter adapter = new SimAdapter((request, protocol, header) -> {
            if (!protocol.equals("7")) {
                return null;
            }
            switch (request) {
                case "22F400":
                    return new String[]{header != null ? "18DAF1620762F40000080000" : "62F40000080000"};
                case "1903":
                    return isoTpLines("5903" + "03010000");
                case "190403010000":
                    return isoTpLines("5904" + "030100" + "08" + "00" + "04"
                            + "F40464" + "F405B4" + "F40C1AF8" + "F40D38");
                default:
                    return new String[]{"7F" + request.substring(0, 2) + "31"};
            }
        });
        elm.addTelegramWriter(adapter);

        connect(elm);
        pump(elm, adapter, 500);
        assertTrue(elm.isObdOnUds());

        elm.setService(ObdProt.OBD_SVC_FREEZEFRAME);
        pump(elm, adapter, 500, 1500);
        elm.setService(ObdProt.OBD_SVC_NONE);

        assertTrue("snapshot identified", adapter.sentAny("1903"));
        assertTrue("snapshot record read", adapter.sentAny("190403010000"));
        Object speed = null;
        for (Object pv : ObdProt.PidPvs.values()) {
            if (pv instanceof com.obddroid.ecu.EcuDataPv
                    && Integer.valueOf(0x0D).equals(((com.obddroid.ecu.EcuDataPv) pv).get(com.obddroid.ecu.EcuDataPv.FID_PID))) {
                speed = ((com.obddroid.ecu.EcuDataPv) pv).get(com.obddroid.ecu.EcuDataPv.FID_VALUE);
            }
        }
        assertNotNull("freeze frame speed decoded " + ObdProt.PidPvs.values(), speed);
        assertEquals(56.0, ((Number) speed).doubleValue(), 0.01);
    }

    private static AtomicReference<Object> captureEcuAddresses(ElmProt elm) {
        AtomicReference<Object> ecus = new AtomicReference<>();
        elm.addPropertyChangeListener(evt -> {
            if (ElmProt.PROP_ECU_ADDRESS.equals(evt.getPropertyName())) {
                ecus.set(evt.getNewValue());
            }
        });
        return ecus;
    }

    private static void assertEcuDetected(AtomicReference<Object> ecus, int address) {
        assertNotNull("ECU addresses reported", ecus.get());
        assertTrue("ECU 0x" + Integer.toHexString(address) + " detected",
                ((Set<?>) ecus.get()).contains(address));
    }
}
