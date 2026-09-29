package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
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
}
