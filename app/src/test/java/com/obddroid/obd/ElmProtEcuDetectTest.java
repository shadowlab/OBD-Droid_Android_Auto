package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.obddroid.interfaces.TelegramWriter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ECU detection when the vehicle bus is reachable but no ECU answers 0100
 * (seen on EVs without generic OBD-II support): the adapter replies NO DATA.
 */
public class ElmProtEcuDetectTest {

    /**
     * Simulated adapter: answers every command with OK, and the ECU
     * detection request 0100 with NO DATA. Each reply ends with a prompt.
     */
    private static class NoDataAdapter implements TelegramWriter {
        final List<String> sent = new ArrayList<>();
        final ConcurrentLinkedQueue<String> replies = new ConcurrentLinkedQueue<>();

        @Override
        public int writeTelegram(char[] buffer) {
            return writeTelegram(buffer, 0, null);
        }

        @Override
        public synchronized int writeTelegram(char[] buffer, int type, Object id) {
            String cmd = new String(buffer).trim();
            sent.add(cmd);
            replies.add(cmd.equals("0100") ? "NODATA" : "OK");
            replies.add(">");
            return buffer.length;
        }

        synchronized long count(String cmd) {
            return sent.stream().filter(cmd::equals).count();
        }
    }

    /** Deliver queued adapter replies until the protocol is idle for idleMs. */
    private static void pump(ElmProt elm, NoDataAdapter adapter, long idleMs) throws InterruptedException {
        long idleSince = System.currentTimeMillis();
        while (System.currentTimeMillis() - idleSince < idleMs) {
            String reply = adapter.replies.poll();
            if (reply != null) {
                elm.handleTelegram(reply.toCharArray());
                idleSince = System.currentTimeMillis();
            } else {
                Thread.sleep(20);
            }
        }
    }

    @Test
    public void noDataOnDetectionRetriesThenReportsVehicleNotResponding() throws Exception {
        ElmProt elm = new ElmProt();
        NoDataAdapter adapter = new NoDataAdapter();
        elm.addTelegramWriter(adapter);

        // adapter reset reply starts initialization and ECU detection
        elm.handleTelegram("ELM327v2.3".toCharArray());
        elm.handleTelegram(">".toCharArray());

        // idle window longer than the retry delay, so every retry gets to run
        pump(elm, adapter, 3000);

        assertEquals("ECU detection attempts", 3, adapter.count("0100"));
        assertTrue(elm.isVehicleNotResponding());
        assertEquals(ElmProt.STAT.NODATA, elm.getStatus());

        // no further attempts once the vehicle is flagged as not responding
        pump(elm, adapter, 2500);
        assertEquals("no retries after giving up", 3, adapter.count("0100"));
    }

    @Test
    public void reinitializingClearsNotRespondingFlag() throws Exception {
        ElmProt elm = new ElmProt();
        NoDataAdapter adapter = new NoDataAdapter();
        elm.addTelegramWriter(adapter);

        elm.handleTelegram("ELM327v2.3".toCharArray());
        elm.handleTelegram(">".toCharArray());
        pump(elm, adapter, 3000);
        assertTrue(elm.isVehicleNotResponding());

        // reconnect: adapter reports its model again
        elm.handleTelegram("ELM327v2.3".toCharArray());
        assertFalse(elm.isVehicleNotResponding());
    }
}
