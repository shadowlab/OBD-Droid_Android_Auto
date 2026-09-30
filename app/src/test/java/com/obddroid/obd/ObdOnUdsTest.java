package com.obddroid.obd;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Response lines are taken from a Car Scanner trace of a 2026 Subaru/Toyota
 * BEV (OBD on UDS, CAN 29 bit, ECUs 0x5A and 0x62, tester 0xE0).
 */
public class ObdOnUdsTest {

    @Test
    public void translatesService01Requests() {
        assertEquals("22F400", ObdOnUds.toUdsRequest("0100"));
        assertEquals("22F40D", ObdOnUds.toUdsRequest("010D"));
        assertEquals("22F45B", ObdOnUds.toUdsRequest("015b"));
    }

    @Test
    public void leavesOtherRequestsAlone() {
        assertNull(ObdOnUds.toUdsRequest("ATSP6"));
        assertNull(ObdOnUds.toUdsRequest("0902"));
        assertNull(ObdOnUds.toUdsRequest("03"));
        assertNull(ObdOnUds.toUdsRequest("22F40D"));
        assertNull(ObdOnUds.toUdsRequest("0100FF"));
    }

    @Test
    public void translatesSingleFrameWith29BitHeader() {
        // vehicle speed 0 km/h
        assertEquals("18DAE05A03410D00", ObdOnUds.fromUdsResponse("18DAE05A0462F40D00"));
        // ambient air temperature 0x44 - 40 = 28 degC
        assertEquals("18DAE05A03414644", ObdOnUds.fromUdsResponse("18DAE05A0462F44644"));
        // distance since codes cleared 0x0480 = 1152 km
        assertEquals("18DAE0620441310480", ObdOnUds.fromUdsResponse("18DAE0620562F4310480"));
    }

    @Test
    public void translatesSingleFrameWith11BitHeader() {
        assertEquals("7E803410D00", ObdOnUds.fromUdsResponse("7E80462F40D00"));
    }

    @Test
    public void translatesSingleFrameWithoutHeader() {
        assertEquals("410D00", ObdOnUds.fromUdsResponse("62F40D00"));
        assertEquals("415B7F", ObdOnUds.fromUdsResponse("62F45B7F"));
    }

    @Test
    public void translatesMultiFrameResponses() {
        // with header: first frame length 0x009 -> 0x008, consecutive frames unchanged
        assertEquals("18DAE05A1008419A0E0060", ObdOnUds.fromUdsResponse("18DAE05A100962F49A0E0060"));
        assertNull(ObdOnUds.fromUdsResponse("18DAE05A21C00000CCCCCCCC"));
        // without header: length line and first data line
        assertEquals("008", ObdOnUds.fromUdsResponse("009"));
        assertEquals("0:419A0E0060", ObdOnUds.fromUdsResponse("0:62F49A0E0060"));
        assertNull(ObdOnUds.fromUdsResponse("1:C00000CCCCCCCC"));
    }

    @Test
    public void translatesNegativeResponses() {
        // request out of range
        assertEquals("7C8027F0131", ObdOnUds.fromUdsResponse("7C8037F2231"));
        assertEquals("7F0131", ObdOnUds.fromUdsResponse("7F2231"));
    }

    @Test
    public void leavesManufacturerDidsAlone() {
        assertNull(ObdOnUds.fromUdsResponse("18DAE05A056211187DCF"));
        assertNull(ObdOnUds.fromUdsResponse("7C80462102175"));
        assertNull(ObdOnUds.fromUdsResponse("NODATA"));
        assertNull(ObdOnUds.fromUdsResponse("OK"));
    }

    // --- fault codes, clear codes and vehicle information ---

    /** "JTMAB3FV3RD123456" as hex */
    private static final String VIN_HEX = "4A544D41423346563352443132333435 36".replace(" ", "");

    @Test
    public void translatesMessageRequests() {
        assertEquals("19423308FF", ObdOnUds.toUdsMessageRequest("03"));
        assertEquals("19423304FF", ObdOnUds.toUdsMessageRequest("07"));
        assertEquals("195533", ObdOnUds.toUdsMessageRequest("0A"));
        assertEquals("14FFFF33", ObdOnUds.toUdsMessageRequest("04"));
        assertEquals("22F802", ObdOnUds.toUdsMessageRequest("0902"));
        assertEquals("22F800", ObdOnUds.toUdsMessageRequest("0900"));
        assertNull(ObdOnUds.toUdsMessageRequest("0100"));
        assertNull(ObdOnUds.toUdsMessageRequest("ATZ"));
        assertNull(ObdOnUds.toUdsMessageRequest("02"));
    }

    @Test
    public void translatesStoredAndPendingDtcs() {
        // 59 42 33 DSAM DSevAM DFI + {severity, DTC P0301 FTB 00, status}, {severity, DTC P0420 FTB 00, status}
        String response = "594233FF1E04" + "2003010008" + "2004200008";
        assertEquals("430203010420", ObdOnUds.fromUdsMessage(response, "03"));
        assertEquals("470203010420", ObdOnUds.fromUdsMessage(response.replace("08", "04"), "07"));
    }

    @Test
    public void translatesNoDtcs() {
        assertEquals("4300", ObdOnUds.fromUdsMessage("594233FF1E04", "03"));
    }

    @Test
    public void translatesPermanentDtcs() {
        // 59 55 33 DSAM DFI + {DTC P0301 FTB 7B, status}
        assertEquals("4A010301", ObdOnUds.fromUdsMessage("595533FF04" + "03017B08", "0A"));
    }

    @Test
    public void translatesClearAndNegativeResponses() {
        assertEquals("44", ObdOnUds.fromUdsMessage("54", "04"));
        assertEquals("7F0331", ObdOnUds.fromUdsMessage("7F1931", "03"));
        assertEquals("7F0931", ObdOnUds.fromUdsMessage("7F2231", "0902"));
    }

    @Test
    public void translatesVin() {
        // no message count in the UDS response: classic count 01 is added
        assertEquals("490201" + VIN_HEX, ObdOnUds.fromUdsMessage("62F802" + VIN_HEX, "0902"));
        // count already present: kept as is
        assertEquals("490201" + VIN_HEX, ObdOnUds.fromUdsMessage("62F80201" + VIN_HEX, "0902"));
        // supported info types bitmap
        assertEquals("490055400000", ObdOnUds.fromUdsMessage("62F80055400000", "0900"));
    }

    @Test
    public void leavesUnrelatedMessagesAlone() {
        assertNull(ObdOnUds.fromUdsMessage("NODATA", "03"));
        assertNull(ObdOnUds.fromUdsMessage("62F40D00", "03"));
        assertNull(ObdOnUds.fromUdsMessage("5942", "03"));
    }

    @Test
    public void assemblesRawMultiFrameDtcResponse() {
        // 16 bytes: length line, 6 + 7 + 3 bytes (padding cut)
        String raw = "010 0:594233FF1E04 1:20030100082004 2:20000855555555";
        assertEquals("430203010420", ObdOnUds.rawResponseToClassic(raw, "03"));
    }

    @Test
    public void rawResponseKeepsOtherLines() {
        assertEquals("44", ObdOnUds.rawResponseToClassic("54", "04"));
        assertEquals("NODATA", ObdOnUds.rawResponseToClassic("NODATA", "03"));
    }

    // --- freeze frames ---

    /**
     * Snapshot for P0301 (record 00): load 04=64, coolant 05=B4, RPM 0C=1AF8,
     * speed 0D=38 and odometer A6=00012345 (PID without a known length).
     */
    private static final String SNAPSHOT = "5904" + "030100" + "08" + "00" + "05"
            + "F40464" + "F405B4" + "F40C1AF8" + "F40D38" + "F4A600012345";

    @Test
    public void recognizesFreezeFrameRequests() {
        assertTrue(ObdOnUds.isFreezeFrameRequest("020000"));
        assertTrue(ObdOnUds.isFreezeFrameRequest("0204"));
        assertFalse(ObdOnUds.isFreezeFrameRequest("0100"));
        assertFalse(ObdOnUds.isFreezeFrameRequest("02"));
        assertEquals("190403010000", ObdOnUds.snapshotRequest("030100", "00"));
    }

    @Test
    public void parsesSnapshotIdentification() {
        assertArrayEquals(new String[]{"030100", "00"},
                ObdOnUds.parseSnapshotIdentification("5903" + "03010000"));
        // record 00 preferred over earlier entries
        assertArrayEquals(new String[]{"030100", "00"},
                ObdOnUds.parseSnapshotIdentification("5903" + "04200001" + "03010000"));
        assertArrayEquals(new String[]{"042000", "01"},
                ObdOnUds.parseSnapshotIdentification("5903" + "04200001"));
        assertNull(ObdOnUds.parseSnapshotIdentification("5903"));
    }

    @Test
    public void parsesSnapshotRecord() {
        ObdOnUds.Snapshot snapshot = ObdOnUds.parseSnapshot(SNAPSHOT);
        assertEquals("030100", snapshot.dtc);
        assertEquals("64", snapshot.pids.get(0x04));
        assertEquals("B4", snapshot.pids.get(0x05));
        assertEquals("1AF8", snapshot.pids.get(0x0C));
        assertEquals("38", snapshot.pids.get(0x0D));
        assertEquals("00012345", snapshot.pids.get(0xA6));
        assertEquals(5, snapshot.pids.size());
    }

    @Test
    public void answersFreezeFrameRequestsFromSnapshot() {
        ObdOnUds.Snapshot snapshot = ObdOnUds.parseSnapshot(SNAPSHOT);
        // PIDs 02, 04, 05, 0C, 0D, and 20 (higher PIDs follow)
        assertEquals("42000058180001", ObdOnUds.freezeFrameResponse(snapshot, "020000"));
        assertEquals("42200000000001", ObdOnUds.freezeFrameResponse(snapshot, "022000"));
        assertEquals("42A00004000000", ObdOnUds.freezeFrameResponse(snapshot, "02A000"));
        assertEquals("420C001AF8", ObdOnUds.freezeFrameResponse(snapshot, "020C00"));
        assertEquals("420C001AF8", ObdOnUds.freezeFrameResponse(snapshot, "020C"));
        assertEquals("4202000301", ObdOnUds.freezeFrameResponse(snapshot, "020200"));
        assertNull(ObdOnUds.freezeFrameResponse(snapshot, "021100"));
    }

    @Test
    public void snapshotWithoutRecordHasNoData() {
        ObdOnUds.Snapshot snapshot = ObdOnUds.parseSnapshot("5904" + "030100" + "08");
        assertTrue(snapshot.pids.isEmpty());
        assertNull(ObdOnUds.freezeFrameResponse(snapshot, "020000"));
    }
}
