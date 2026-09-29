package com.obddroid.car;

import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.CommService;
import com.obddroid.services.VehicleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * Read-only access to the adapter state shared with the phone UI.
 */
final class ObdCarData {

    private ObdCarData() {
    }

    static ElmProt.STAT getAdapterStatus() {
        ElmProt.STAT status = CommService.elm.getStatus();
        return status != null ? status : ElmProt.STAT.UNDEFINED;
    }

    /**
     * Same connected states MainActivity treats as an active adapter session.
     */
    static boolean isAdapterConnected() {
        ElmProt.STAT status = getAdapterStatus();
        return status == ElmProt.STAT.CONNECTED
                || status == ElmProt.STAT.ECU_DETECTED
                || status == ElmProt.STAT.ECU_SELECTED;
    }

    /**
     * Adapter connected, but no ECU answered any detection attempt.
     */
    static boolean isVehicleNotResponding() {
        return CommService.elm.isVehicleNotResponding();
    }

    /**
     * Text explaining why there is no vehicle connection.
     */
    static String getNotConnectedText() {
        return isVehicleNotResponding()
                ? "Vehicle not responding. Make sure it is switched on (READY), then reconnect in the phone app."
                : "Not connected. Connect to your adapter in the phone app.";
    }

    static String getVehicleName() {
        return VehicleManager.getInstance().getVehicleDisplayName();
    }

    /**
     * Snapshot of the live data PIDs, ordered by PID.
     */
    static List<EcuDataPv> getLiveData() {
        List<EcuDataPv> result = new ArrayList<>();
        try {
            synchronized (ObdProt.PidPvs) {
                for (Object value : ObdProt.PidPvs.values()) {
                    if (value instanceof EcuDataPv) {
                        result.add((EcuDataPv) value);
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            // List is being rebuilt; the next refresh will pick it up.
            return Collections.emptyList();
        }
        Collections.sort(result, (a, b) -> a.toString().compareTo(b.toString()));
        return result;
    }

    static String getDescription(EcuDataPv pv) {
        Object description = pv.get(EcuDataPv.FID_DESCRIPT);
        return description != null ? String.valueOf(description) : pv.toString();
    }

    /**
     * Value with units, formatted the same way as the phone's live data list.
     */
    static String formatValue(EcuDataPv pv) {
        Object value = pv.get(EcuDataPv.FID_VALUE);
        if (value == null) {
            return "--";
        }
        String text;
        try {
            Object cnvObj = pv.get(EcuDataPv.FID_CNVID);
            if (cnvObj instanceof Conversion[] && ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem] != null) {
                Conversion cnv = ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem];
                text = cnv.physToPhysFmtString((Number) value, (String) pv.get(EcuDataPv.FID_FORMAT));
            } else {
                text = String.valueOf(value);
            }
        } catch (Exception e) {
            text = String.valueOf(value);
        }
        String units = pv.getUnits();
        return units == null || units.isEmpty() ? text : text + " " + units;
    }

    /**
     * Fault codes from the last read on the phone. The placeholder entry the
     * protocol stores when no codes are set (key 0) is left out.
     */
    static List<EcuCodeItem> getFaultCodes() {
        List<EcuCodeItem> result = new ArrayList<>();
        try {
            synchronized (ObdProt.tCodes) {
                for (Map.Entry<Integer, ?> entry : ObdProt.tCodes.entrySetTyped()) {
                    if (entry.getKey() != null && entry.getKey() == 0) {
                        continue;
                    }
                    if (entry.getValue() instanceof EcuCodeItem) {
                        result.add((EcuCodeItem) entry.getValue());
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            return Collections.emptyList();
        }
        Collections.sort(result, (a, b) ->
                String.valueOf(a.get(EcuCodeItem.FID_CODE)).compareTo(String.valueOf(b.get(EcuCodeItem.FID_CODE))));
        return result;
    }

    /**
     * True once the phone has read codes and found none.
     */
    static boolean hasNoCodesResult() {
        synchronized (ObdProt.tCodes) {
            return ObdProt.tCodes.containsKey(0);
        }
    }
}
