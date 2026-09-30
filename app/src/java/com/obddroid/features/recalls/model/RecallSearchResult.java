package com.obddroid.features.recalls.model;

import java.util.ArrayList;
import java.util.List;

import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.VehicleData;

/**
 * Model representing the result of a recall search.
 *
 * Encapsulates both the vehicle information (decoded from VIN) and the
 * list of recalls found for that vehicle.
 *
 */
public class RecallSearchResult {

    private final String vin;
    private final VehicleData vehicleData;
    private final List<RecallRecord> recalls;
    private final long timestamp;

    public RecallSearchResult(String vin, VehicleData vehicleData, List<RecallRecord> recalls) {
        this.vin = vin;
        this.vehicleData = vehicleData;
        this.recalls = recalls != null ? new ArrayList<>(recalls) : new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get the VIN this search was performed for
     */
    public String getVin() {
        return vin;
    }

    /**
     * Get the decoded vehicle information
     */
    public VehicleData getVehicleData() {
        return vehicleData;
    }

    /**
     * Get the list of recalls found
     * @return immutable copy of recalls list
     */
    public List<RecallRecord> getRecalls() {
        return new ArrayList<>(recalls);
    }

    /**
     * Check if any recalls were found
     */
    public boolean hasRecalls() {
        return recalls != null && !recalls.isEmpty();
    }

    /**
     * Get the number of recalls found
     */
    public int getRecallCount() {
        return recalls != null ? recalls.size() : 0;
    }

    /**
     * Get the timestamp when this search was performed
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get vehicle display name (Year Make Model)
     */
    public String getVehicleDisplayName() {
        if (vehicleData == null) {
            return "";
        }

        String displayName = vehicleData.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        // Fallback: construct from parts
        StringBuilder sb = new StringBuilder();
        if (vehicleData.getModelYear() != null) {
            sb.append(vehicleData.getModelYear()).append(" ");
        }
        if (vehicleData.getMake() != null) {
            sb.append(vehicleData.getMake()).append(" ");
        }
        if (vehicleData.getModel() != null) {
            sb.append(vehicleData.getModel());
        }

        return sb.toString().trim();
    }

    /**
     * Get age of this search result in minutes
     */
    public long getAgeMinutes() {
        return (System.currentTimeMillis() - timestamp) / (60 * 1000);
    }

    /**
     * Check if this result is considered fresh (less than 1 hour old)
     */
    public boolean isFresh() {
        return getAgeMinutes() < 60;
    }
}
