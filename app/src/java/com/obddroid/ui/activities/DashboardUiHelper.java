package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.utils.SnackbarHelper;

import java.util.logging.Logger;

/**
 * Helper utilities for configuring the dashboard cards shown on the main screen.
 */
final class DashboardUiHelper {
    private static final Logger log = Logger.getLogger(DashboardUiHelper.class.getName());

    private DashboardUiHelper() {
        // Utility class
    }

    static void setupDashboardCards(MainActivity activity) {
        // Live Data card
        View liveDataCard = activity.findViewById(R.id.card_live_data);
        if (liveDataCard != null) {
            addCardPressAnimation(liveDataCard);
            liveDataCard.setOnClickListener(v -> {
                log.info("Live Data card clicked!");
                activity.launchLiveDataActivity();
            });
            liveDataCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Live Data",
                "Monitor real-time sensor values from the vehicle, including GPS and motion telemetry."
            ));
        } else {
            log.warning("Live Data card NOT found!");
        }

        // Test Control card
        View testControlCard = activity.findViewById(R.id.card_test_control);
        if (testControlCard != null) {
            addCardPressAnimation(testControlCard);
            testControlCard.setOnClickListener(v -> {
                log.info("Test Control card clicked!");
                ElmProt.STAT ecuState = activity.getEcuConnectionState();
                if (ecuState == ElmProt.STAT.ECU_DETECTED ||
                    ecuState == ElmProt.STAT.CONNECTED ||
                    ecuState == ElmProt.STAT.ECU_SELECTED) {
                    UnsupportedModeHelper helper = activity.getUnsupportedModeHelper();
                    if (helper != null) {
                        helper.recordPreUnsupportedState(ecuState);
                    }
                    activity.setObdService(ObdProt.OBD_SVC_CTRL_MODE, "Test Control");
                } else {
                    SnackbarHelper.showWarning(activity, "Please connect to vehicle first");
                }
            });
            testControlCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Test Control",
                "Run diagnostic tests and actuator controls supported by the vehicle."
            ));
        } else {
            log.warning("Test Control card NOT found!");
        }

        // Fault Codes card
        View faultCodesCard = activity.findViewById(R.id.card_fault_codes);
        if (faultCodesCard != null) {
            addCardPressAnimation(faultCodesCard);
            faultCodesCard.setOnClickListener(v -> {
                log.info("Fault Codes card clicked!");
                activity.launchFaultCodesActivity();
            });
            faultCodesCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Fault Codes",
                "Read, decode, and clear diagnostic trouble codes stored by the vehicle."
            ));
        } else {
            log.warning("Fault Codes card NOT found!");
        }

        // Fuel Economy card
        View fuelEconomyCard = activity.findViewById(R.id.card_fuel_economy);
        if (fuelEconomyCard != null) {
            addCardPressAnimation(fuelEconomyCard);
            fuelEconomyCard.setOnClickListener(v -> {
                log.info("Fuel Economy card clicked!");
                activity.launchFuelEconomyActivity();
            });
            fuelEconomyCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Fuel Economy",
                "Track MPG, fuel level, and driving range statistics."
            ));
        } else {
            log.warning("Fuel Economy card NOT found!");
        }

        // Emissions card
        View emissionsCard = activity.findViewById(R.id.card_emissions);
        if (emissionsCard != null) {
            addCardPressAnimation(emissionsCard);
            emissionsCard.setOnClickListener(v -> {
                log.info("Emissions card clicked!");
                activity.launchEmissionsActivity();
            });
            emissionsCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Emissions Diagnostics",
                "Check monitor readiness status and IUMPR performance data. Verify if your vehicle is ready for emissions testing."
            ));
        } else {
            log.warning("Emissions card NOT found!");
        }

        // Safety Recalls card
        View recallsCard = activity.findViewById(R.id.card_vehicle_recalls);
        if (recallsCard != null) {
            addCardPressAnimation(recallsCard);
            recallsCard.setOnClickListener(v -> {
                log.info("Safety Recalls card clicked!");
                activity.launchRecallActivity();
            });
            recallsCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                activity.getString(R.string.safety_recalls),
                "Look up open safety recalls using the official NHTSA database."
            ));
        } else {
            log.warning("Safety Recalls card NOT found!");
        }

        // ECU List card
        View ecuListCard = activity.findViewById(R.id.card_ecu_list);
        if (ecuListCard != null) {
            addCardPressAnimation(ecuListCard);
            ecuListCard.setOnClickListener(v -> {
                log.info("ECU List card clicked!");
                activity.launchEcuListActivity();
            });
            ecuListCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                activity.getString(R.string.ecu_list_title),
                "Discover and view all Electronic Control Units (ECUs) in your vehicle. Shows ECU names, addresses, calibration IDs, and supported features."
            ));
        } else {
            log.warning("ECU List card NOT found!");
        }

        // Vehicle Info card
        View vehicleInfoCard = activity.findViewById(R.id.card_vehicle_info);
        if (vehicleInfoCard != null) {
            addCardPressAnimation(vehicleInfoCard);
            vehicleInfoCard.setOnClickListener(v -> {
                log.info("Vehicle Info card clicked!");
                activity.launchVehicleInfoActivity();
            });
            vehicleInfoCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "VIN Decoder",
                "View comprehensive decoded VIN information from the NHTSA vPIC database. See all available details about your vehicle including specifications, safety features, and manufacturing data."
            ));
        } else {
            log.warning("Vehicle Info card NOT found!");
        }

        // CoPilot card
        View copilotCard = activity.findViewById(R.id.card_copilot);
        if (copilotCard != null) {
            addCardPressAnimation(copilotCard);
            copilotCard.setOnClickListener(v -> {
                log.info("CoPilot card clicked!");
                activity.launchCoPilotActivity();
            });
            copilotCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "CoPilot AI Assistant",
                "Get intelligent assistance with vehicle diagnostics. Ask questions about scan results, fault codes, repair recommendations, and get guided troubleshooting help."
            ));
        } else {
            log.warning("CoPilot card NOT found!");
        }

        // Full Scan card
        View fullScanCard = activity.findViewById(R.id.card_full_scan);
        if (fullScanCard != null) {
            addCardPressAnimation(fullScanCard);
            fullScanCard.setOnClickListener(v -> {
                log.info("Full Scan card clicked!");
                activity.launchFullScanActivity();
            });
            fullScanCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Full Vehicle Scan",
                "Perform a comprehensive scan of all vehicle systems. Reads live data, fault codes, test results, and vehicle information in one operation."
            ));
        } else {
            log.warning("Full Scan card NOT found!");
        }

        // Track Mode card
        View trackModeCard = activity.findViewById(R.id.card_track_mode);
        if (trackModeCard != null) {
            addCardPressAnimation(trackModeCard);
            trackModeCard.setOnClickListener(v -> {
                log.info("Track Mode card clicked!");
                activity.launchTrackModeActivity();
            });
            trackModeCard.setOnLongClickListener(v -> showCardInfoDialog(
                activity,
                "Track Mode",
                "Racing lap timer with telemetry recording. Track your performance on race circuits with automatic lap timing, sector splits, G-force monitoring, and detailed telemetry analysis."
            ));
        } else {
            log.warning("Track Mode card NOT found!");
        }
    }

    private static void addCardPressAnimation(View card) {
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                    break;
            }
            return false;
        });
    }

    private static boolean showCardInfoDialog(MainActivity activity, String title, String message) {
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show();
        return true;
    }
}
