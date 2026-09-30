package com.obddroid.features.recalls.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.features.recalls.data.RecallDataManager;
import com.obddroid.features.recalls.data.RecallExporter;
import com.obddroid.features.recalls.data.RecallService;
import com.obddroid.features.recalls.model.RecallSearchResult;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;

import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.VehicleData;

/**
 * Screen for searching and displaying NHTSA safety recalls.
 *
 * Architecture:
 * - RecallService: Handles API calls (VIN decode + recall lookup)
 * - RecallDataManager: Handles caching and state
 * - RecallExporter: Handles CSV/JSON export
 * - RecallActivity: UI-only logic
 *
 */
public class RecallActivity extends AppCompatActivity {

    private static final String TAG = "RecallActivity";

    // Services
    private RecallService recallService;
    private RecallDataManager dataManager;
    private RecallExporter exporter;

    // UI Components
    private View loadingCard;
    private TextView statusText;
    private ProgressBar loadingIndicator;
    private LinearLayout resultsContainer;
    private View emptyState;
    private View errorCard;
    private TextView errorMessage;
    private VehicleInfoFooter vehicleInfoFooter;
    private View footerOverlay;
    private androidx.coordinatorlayout.widget.CoordinatorLayout coordinatorLayout;

    // Hero card
    private androidx.cardview.widget.CardView heroCard;
    private View heroInfoState;
    private View heroResultsState;
    private TextView heroRecallCount;
    private TextView heroVehicleText;

    // State
    private RecallSearchResult currentResult;
    private String currentVin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recalls);

        // Set navigation bar to black
        if (getWindow() != null) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.safety_recalls);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize services
        recallService = new RecallService(this);
        dataManager = new RecallDataManager(this);
        exporter = new RecallExporter(this);

        bindViews();
        setupFooterOverlay();

        // Try to load cached data
        RecallSearchResult cachedResult = loadCachedDataIfAvailable();
        if (cachedResult != null) {
            currentResult = cachedResult;
            currentVin = cachedResult.getVin();
        }

        // Hide loading and error states initially
        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        // Auto-search if no cached data or cache is stale
        if (cachedResult == null || !cachedResult.isFresh()) {
            autoSearchRecalls();
        } else {
            displayResult(cachedResult);
            showSnackbar("Showing cached results (tap Refresh for latest)", SnackbarHelper.MessageType.INFO);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.recall_menu, menu);
        return true;
    }

    private void bindViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        loadingCard = findViewById(R.id.recalls_loading_card);
        statusText = findViewById(R.id.recalls_status_text);
        loadingIndicator = findViewById(R.id.recalls_loading_indicator);
        resultsContainer = findViewById(R.id.recalls_results_container);
        emptyState = findViewById(R.id.recalls_empty_state);
        errorCard = findViewById(R.id.recalls_error_card);
        errorMessage = findViewById(R.id.recalls_error_message);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        footerOverlay = findViewById(R.id.footer_overlay);

        // Hero card removed - no longer needed
        // heroCard = findViewById(R.id.recalls_hero_card);
        // heroInfoState = findViewById(R.id.hero_info_state);
        // heroResultsState = findViewById(R.id.hero_results_state);
        // heroRecallCount = findViewById(R.id.hero_recall_count);
        // heroVehicleText = findViewById(R.id.hero_vehicle_text);
    }

    private void setupFooterOverlay() {
        if (vehicleInfoFooter != null && footerOverlay != null) {
            vehicleInfoFooter.setOverlayView(footerOverlay);
        }
    }

    /**
     * Load cached data for the current VIN if available.
     */
    private RecallSearchResult loadCachedDataIfAvailable() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                return dataManager.getCurrentResult();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached data", e);
        }
        return null;
    }

    /**
     * Auto-search for recalls using connected vehicle VIN.
     */
    private void autoSearchRecalls() {
        try {
            VehicleManager vm = VehicleManager.getInstance();
            String vin = vm.getCurrentVIN();
            if (!TextUtils.isEmpty(vin)) {
                Log.d(TAG, "Auto-searching recalls for VIN: " + vin);
                searchRecalls(vin);
            } else {
                Log.d(TAG, "No VIN available from connected vehicle");
                showNoVehicleMessage();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting VIN from VehicleManager", e);
            showNoVehicleMessage();
        }
    }

    /**
     * Show message when no vehicle is connected.
     */
    private void showNoVehicleMessage() {
        if (heroInfoState != null) {
            for (int i = 0; i < ((LinearLayout) heroInfoState).getChildCount(); i++) {
                View child = ((LinearLayout) heroInfoState).getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setText("Connect to a vehicle to check for safety recalls");
                    break;
                }
            }
        }
    }

    /**
     * Perform recall search for the given VIN.
     */
    private void searchRecalls(String vin) {
        currentVin = vin;
        currentResult = null;

        recallService.searchRecallsByVin(vin, new RecallService.RecallSearchCallback() {
            @Override
            public void onSearchStarted() {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.VISIBLE);
                    if (statusText != null) statusText.setText("Searching for recalls...");
                    resultsContainer.removeAllViews();
                    resultsContainer.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                    if (errorCard != null) errorCard.setVisibility(View.GONE);
                });
            }

            @Override
            public void onVinDecoded(VehicleData vehicleData) {
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("Looking up recalls for " + vehicleData.getDisplayName() + "...");
                    }
                });
            }

            @Override
            public void onSearchCompleted(RecallSearchResult result) {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.GONE);

                    currentResult = result;

                    // Cache the result
                    dataManager.cacheResult(result);

                    // Display the result
                    displayResult(result);

                    // Show appropriate message
                    if (result.hasRecalls()) {
                        showSnackbar(result.getRecallCount() + " recall" +
                            (result.getRecallCount() == 1 ? "" : "s") + " found",
                            SnackbarHelper.MessageType.WARNING);
                    } else {
                        showSnackbar("No recalls found - vehicle is safe!",
                            SnackbarHelper.MessageType.SUCCESS);
                    }
                });
            }

            @Override
            public void onSearchFailed(String error) {
                runOnUiThread(() -> {
                    if (loadingCard != null) loadingCard.setVisibility(View.GONE);
                    dataManager.clearCurrentResult();
                    currentResult = null;
                    updateHeroCard(null, false);
                    if (errorCard != null) {
                        errorCard.setVisibility(View.VISIBLE);
                        if (errorMessage != null) {
                            errorMessage.setText(error);
                        }
                    }
                    resultsContainer.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                });
            }
        });
    }

    /**
     * Display recall search result in UI.
     */
    private void displayResult(RecallSearchResult result) {
        if (result == null) {
            return;
        }

        currentResult = result;

        if (loadingCard != null) loadingCard.setVisibility(View.GONE);
        if (errorCard != null) errorCard.setVisibility(View.GONE);

        if (resultsContainer != null) {
            resultsContainer.removeAllViews();
        }

        if (result.hasRecalls()) {
            updateHeroCard(result, true);

            if (resultsContainer != null) {
                for (RecallRecord recall : result.getRecalls()) {
                    View recallView = createRecallView(recall);
                    resultsContainer.addView(recallView);
                }
            }

            if (resultsContainer != null) {
                resultsContainer.setVisibility(View.VISIBLE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            }
        } else {
            updateHeroCard(null, false);
            if (resultsContainer != null) {
                resultsContainer.setVisibility(View.GONE);
            }
            if (emptyState != null) {
                emptyState.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Update hero card display.
     */
    private void updateHeroCard(RecallSearchResult result, boolean hasRecalls) {
        if (heroCard == null) {
            Log.e(TAG, "heroCard is null!");
            return;
        }

        if (hasRecalls && result != null) {
            // Switch to results state - orange warning
            if (heroInfoState != null) heroInfoState.setVisibility(View.GONE);
            if (heroResultsState != null) heroResultsState.setVisibility(View.VISIBLE);

            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_warning, null));

            if (heroRecallCount != null) {
                String text = String.format(Locale.US, "%d Recall%s Detected",
                    result.getRecallCount(), result.getRecallCount() == 1 ? "" : "s");
                heroRecallCount.setText(text);
            }

            if (heroVehicleText != null) {
                heroVehicleText.setText(result.getVehicleDisplayName());
            }
        } else {
            // Switch to info state - green
            if (heroInfoState != null) heroInfoState.setVisibility(View.VISIBLE);
            if (heroResultsState != null) heroResultsState.setVisibility(View.GONE);

            heroCard.setCardBackgroundColor(getResources().getColor(R.color.fault_success, null));
        }
    }

    /**
     * Create a recall card view.
     */
    private View createRecallView(RecallRecord recall) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_recall, resultsContainer, false);

        TextView campaignTitle = view.findViewById(R.id.recall_campaign_title);
        TextView component = view.findViewById(R.id.recall_component);
        TextView makeModel = view.findViewById(R.id.recall_make_model);
        TextView reportDate = view.findViewById(R.id.recall_report_date);
        TextView summary = view.findViewById(R.id.recall_summary);
        TextView remedy = view.findViewById(R.id.recall_remedy);
        TextView openLink = view.findViewById(R.id.recall_open_link);

        // Campaign title
        String campaignNumber = recall.getNhtsaCampaignNumber();
        if (!TextUtils.isEmpty(campaignNumber)) {
            campaignTitle.setText("Campaign #" + campaignNumber);
        }

        // Component
        if (!TextUtils.isEmpty(recall.getComponent())) {
            String componentText = recall.getComponent().replace(":", " - ");
            component.setText("Component: " + componentText);
            component.setVisibility(View.VISIBLE);
        } else {
            component.setVisibility(View.GONE);
        }

        // Make/Model
        String makeModelText = String.format("%s %s %s",
            recall.getModelYear() != null ? recall.getModelYear() : "",
            recall.getMake() != null ? recall.getMake() : "",
            recall.getModel() != null ? recall.getModel() : "").trim();
        if (!TextUtils.isEmpty(makeModelText)) {
            makeModel.setText(makeModelText);
            makeModel.setVisibility(View.VISIBLE);
        } else {
            makeModel.setVisibility(View.GONE);
        }

        // Report date
        if (!TextUtils.isEmpty(recall.getReportReceivedDate())) {
            try {
                String formattedDate = formatDate(recall.getReportReceivedDate());
                reportDate.setText("Reported: " + formattedDate);
                reportDate.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                reportDate.setVisibility(View.GONE);
            }
        } else {
            reportDate.setVisibility(View.GONE);
        }

        // Summary
        if (!TextUtils.isEmpty(recall.getSummary())) {
            summary.setText(recall.getSummary());
            summary.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
            view.findViewById(R.id.recall_summary_heading).setVisibility(View.GONE);
        }

        // Remedy
        if (!TextUtils.isEmpty(recall.getRemedy())) {
            remedy.setText(recall.getRemedy());
            remedy.setVisibility(View.VISIBLE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.VISIBLE);
        } else {
            remedy.setVisibility(View.GONE);
            view.findViewById(R.id.recall_remedy_heading).setVisibility(View.GONE);
        }

        // Open link
        openLink.setOnClickListener(v -> {
            String url = "https://www.nhtsa.gov/recalls?nhtsaId=" + campaignNumber;
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                showSnackbar("Unable to open recall details", SnackbarHelper.MessageType.ERROR);
            }
        });

        return view;
    }

    /**
     * Show save report dialog.
     */
    private void showSaveReportDialog() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("Search for recalls before saving a report", SnackbarHelper.MessageType.INFO);
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) dialogTitle.setText("Save Report");

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportCsv();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportJson();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Export recalls to CSV.
     */
    private void exportCsv() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("No recall data available to export", SnackbarHelper.MessageType.INFO);
            return;
        }

        try {
            String filename = exporter.exportToCsv(result);
            showSnackbar("Recall report saved: " + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException e) {
            showSnackbar("Failed to export CSV: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Export recalls to JSON.
     */
    private void exportJson() {
        RecallSearchResult result = dataManager.getCurrentResult();
        if (result == null || !result.hasRecalls()) {
            showSnackbar("No recall data available to export", SnackbarHelper.MessageType.INFO);
            return;
        }

        try {
            String filename = exporter.exportToJson(result);
            showSnackbar("Recall report saved: " + filename, SnackbarHelper.MessageType.SUCCESS);
        } catch (IOException | JSONException e) {
            showSnackbar("Failed to export JSON: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /**
     * Format date string for display.
     */
    private String formatDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (ParseException e) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(dateString);
                return outputFormat.format(date);
            } catch (ParseException e2) {
                return dateString;
            }
        }
    }

    /**
     * Show a snackbar properly positioned above footer.
     */
    private void showSnackbar(String message, SnackbarHelper.MessageType type) {
        if (coordinatorLayout == null) {
            SnackbarHelper.showSnackbar(this, message, type);
            return;
        }

        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.parseColor("#757575"));

        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(Color.WHITE);
        }

        // Set bottom margin to appear above footer (56dp)
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params =
            (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) snackbarView.getLayoutParams();
        params.setMargins(0, 0, 0, (int) (56 * getResources().getDisplayMetrics().density));
        snackbarView.setLayoutParams(params);

        snackbar.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh_recalls) {
            autoSearchRecalls();
            return true;
        } else if (id == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
