package com.obddroid.features.copilot.data.tools;

import android.content.Context;
import android.content.Intent;

import com.obddroid.features.copilot.ui.CoPilotActivity;
import com.obddroid.features.emissions.ui.EmissionsActivity;
import com.obddroid.features.fueleconomy.ui.FuelEconomyActivity;
import com.obddroid.ui.activities.FaultCodesActivity;
import com.obddroid.ui.activities.LiveDataActivity;
import com.obddroid.ui.activities.SettingsActivity;

import org.json.JSONObject;

/**
 * Tool handler for open_screen.
 * Navigates to a specific screen in the OBD-Droid app.
 */
public class OpenScreenTool implements AgentTool {

    @Override
    public String getName() {
        return "open_screen";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        String screen = parameters.optString("screen", "");

        if (screen.isEmpty()) {
            throw new Exception("Screen parameter is required. " +
                "Available: emissions, fault_codes, fuel_economy, live_data, settings, copilot");
        }

        Intent intent;
        String screenName;

        switch (screen.toLowerCase()) {
            case "emissions":
                intent = new Intent(context, EmissionsActivity.class);
                screenName = "Emissions Monitor";
                break;
            case "fault_codes":
                intent = new Intent(context, FaultCodesActivity.class);
                screenName = "Fault Codes";
                break;
            case "fuel_economy":
                intent = new Intent(context, FuelEconomyActivity.class);
                screenName = "Fuel Economy";
                break;
            case "live_data":
                intent = new Intent(context, LiveDataActivity.class);
                screenName = "Live Data";
                break;
            case "settings":
                intent = new Intent(context, SettingsActivity.class);
                screenName = "Settings";
                break;
            case "copilot":
                intent = new Intent(context, CoPilotActivity.class);
                screenName = "CoPilot";
                break;
            default:
                throw new Exception("Unknown screen: " + screen + ". " +
                    "Available: emissions, fault_codes, fuel_economy, live_data, settings, copilot");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        return "Navigated to " + screenName + " screen.";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Screen navigation is safe
    }
}
