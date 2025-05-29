package com.example.icadsapp.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.icadsapp.R;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class AlertsFragment extends Fragment {

    private LinearLayout alertsContainer;
    private TextView loadingText;
    private View alertsScrollView;
    private LinkedList<AlertData> alertsQueue; // Using LinkedList for efficient add/remove operations
    private static final int MAX_ALERTS = 15;
    private SharedViewModel sharedViewModel;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private static final String PREFS_NAME = "AlertsPrefs";
    private static final String ALERTS_KEY = "saved_alerts";

    // Data model for alerts
    public static class AlertData {
        public String destinationIp;
        public String lastDdosTime;
        public String lastNormalTime;
        public String systemStatus;
        public String sourceIp;
        public long timestamp; // For better sorting

        public AlertData() {
            this.timestamp = System.currentTimeMillis();
        }

        public AlertData(String destinationIp, String lastDdosTime, String lastNormalTime, String systemStatus, String sourceIp) {
            this.destinationIp = destinationIp;
            this.lastDdosTime = lastDdosTime;
            this.lastNormalTime = lastNormalTime;
            this.systemStatus = systemStatus;
            this.sourceIp = sourceIp;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alerts, container, false);

        // Initialize views
        alertsContainer = view.findViewById(R.id.alerts_container);
        loadingText = view.findViewById(R.id.loading_text);
        alertsScrollView = view.findViewById(R.id.alerts_scroll_view);

        // Initialize ViewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // Initialize SharedPreferences and Gson for persistent storage
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();

        // Initialize alerts queue and load saved alerts
        alertsQueue = new LinkedList<>();
        loadSavedAlerts();

        // Set up ViewModel observer
        setupViewModelObserver();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initial UI refresh if we have saved alerts
        if (!alertsQueue.isEmpty()) {
            refreshAlertsUI();
        }
    }

    private void setupViewModelObserver() {
        sharedViewModel.getAlertData().observe(getViewLifecycleOwner(), alertData -> {
            if (isAdded() && alertData != null) {
                processNewAlert(alertData);
            }
        });
    }

    private void processNewAlert(AlertData alertData) {
        // Check for duplicate before adding
        if (isDuplicateAlert(alertData)) {
            return;
        }

        // Add to the end of the queue (FIFO structure)
        alertsQueue.addLast(alertData);

        // Remove oldest if queue exceeds MAX_ALERTS
        while (alertsQueue.size() > MAX_ALERTS) {
            alertsQueue.removeFirst();
        }

        saveAlertsToPreferences();
        refreshAlertsUI();
    }

    private boolean isDuplicateAlert(AlertData newAlert) {
        for (AlertData existing : alertsQueue) {
            if (isSameAlert(existing, newAlert)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameAlert(AlertData a1, AlertData a2) {
        // Compare based on event type and timestamp
        if (isDdosAlert(a1) && isDdosAlert(a2)) {
            return a1.lastDdosTime.equals(a2.lastDdosTime) &&
                    a1.destinationIp.equals(a2.destinationIp);
        } else if (!isDdosAlert(a1) && !isDdosAlert(a2)) {
            return a1.lastNormalTime.equals(a2.lastNormalTime) &&
                    a1.destinationIp.equals(a2.destinationIp);
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAlertsUI(); // Refresh when returning to fragment
    }

    private void refreshAlertsUI() {
        if (getView() == null || !isAdded()) return; // Guard against null view

        // Hide loading text and show alerts container
        loadingText.setVisibility(View.GONE);
        alertsScrollView.setVisibility(View.VISIBLE);

        // Clear existing views
        alertsContainer.removeAllViews();

        if (alertsQueue.isEmpty()) {
            // Show empty state
            showEmptyState();
            return;
        }

        // Convert queue to list for easier iteration (most recent first)
        List<AlertData> alertsList = new ArrayList<>(alertsQueue);

        // Display alerts in reverse order (most recent on top)
        for (int i = alertsList.size() - 1; i >= 0; i--) {
            AlertData alert = alertsList.get(i);
            if (isDdosAlert(alert)) {
                addDdosCard(alert);
            } else {
                addNormalCard(alert);
            }
        }
    }

    private void showEmptyState() {
        TextView emptyText = new TextView(getContext());
        emptyText.setText("No alerts yet");
        emptyText.setTextSize(18);
        emptyText.setTextColor(ContextCompat.getColor(getContext(), R.color.dgrey));
        emptyText.setGravity(android.view.Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(50), 0, 0);
        emptyText.setLayoutParams(params);

        alertsContainer.addView(emptyText);
    }

    private boolean isDdosAlert(AlertData alert) {
        // Check if this is a DDoS alert based on which timestamp is more recent
        return isMoreRecent(alert.lastDdosTime, alert.lastNormalTime);
    }

    private boolean isMoreRecent(String ddosTime, String normalTime) {
        try {
            Date ddosDate = parseFlexibleDate(ddosTime);
            Date normalDate = parseFlexibleDate(normalTime);

            if (ddosDate == null && normalDate == null) return false;
            if (ddosDate == null) return false;
            if (normalDate == null) return true;

            return ddosDate.after(normalDate);
        } catch (Exception e) {
            return false;
        }
    }

    // Handles multiple date formats
    private Date parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;

        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
        };

        for (String format : formats) {
            try {
                return new SimpleDateFormat(format, Locale.getDefault()).parse(dateStr.trim());
            } catch (ParseException ignored) {
                // Try next format
            }
        }
        return null;
    }

    private String formatTimestamp(String isoString) {
        try {
            Date date = parseFlexibleDate(isoString);
            if (date == null) return isoString != null ? isoString : "Unknown time";

            SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault());
            return displayFormat.format(date);
        } catch (Exception e) {
            return isoString != null ? isoString : "Unknown time";
        }
    }

    // Save alerts to SharedPreferences for persistence
    private void saveAlertsToPreferences() {
        try {
            List<AlertData> alertsList = new ArrayList<>(alertsQueue);
            String json = gson.toJson(alertsList);
            sharedPreferences.edit().putString(ALERTS_KEY, json).apply();
        } catch (Exception e) {
            // Handle error silently
        }
    }

    // Load saved alerts from SharedPreferences
    private void loadSavedAlerts() {
        try {
            String json = sharedPreferences.getString(ALERTS_KEY, null);
            if (json != null) {
                Type type = new TypeToken<List<AlertData>>(){}.getType();
                List<AlertData> savedAlerts = gson.fromJson(json, type);
                if (savedAlerts != null) {
                    alertsQueue.clear();
                    alertsQueue.addAll(savedAlerts);

                    // Ensure we don't exceed MAX_ALERTS
                    while (alertsQueue.size() > MAX_ALERTS) {
                        alertsQueue.removeFirst();
                    }
                }
            }
        } catch (Exception e) {
            // Handle error silently, start with empty queue
            alertsQueue.clear();
        }
    }

    private void addDdosCard(AlertData alertData) {
        // Create MaterialCardView programmatically
        MaterialCardView cardView = new MaterialCardView(getContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dpToPx(8), 0, 0);
        cardView.setLayoutParams(cardParams);
        cardView.setRadius(dpToPx(10));
        cardView.setCardElevation(0);
        cardView.setCardBackgroundColor(ContextCompat.getColor(getContext(), R.color.lred));
        cardView.setStrokeWidth(0);

        // Create main container with RelativeLayout to position icon
        android.widget.RelativeLayout mainContainer = new android.widget.RelativeLayout(getContext());
        mainContainer.setPadding(dpToPx(15), dpToPx(15), dpToPx(15), dpToPx(15));

        // Alert text
        // Alert icon
        ImageView alertIcon = new ImageView(getContext());
        alertIcon.setImageResource(R.drawable.notification_important_outline);
        alertIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.red));

        android.widget.RelativeLayout.LayoutParams iconParams =
                new android.widget.RelativeLayout.LayoutParams(dpToPx(25), dpToPx(25));
        iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        iconParams.setMargins(0, dpToPx(0), 0, 0); // Reduced top margin for icon
        alertIcon.setLayoutParams(iconParams);

        // Timestamp
        TextView timestampText = new TextView(getContext());
        timestampText.setId(View.generateViewId());
        timestampText.setText(formatTimestamp(alertData.lastDdosTime));
        timestampText.setTextSize(13);
        timestampText.setTextColor(ContextCompat.getColor(getContext(), R.color.red));

        android.widget.RelativeLayout.LayoutParams timestampParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        timestampParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        timestampParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        timestampParams.setMargins(0, dpToPx(0), 0, 0); // Reduced top margin for timestamp
        timestampText.setLayoutParams(timestampParams);

        // Alert text
        TextView alertText = new TextView(getContext());
        alertText.setId(View.generateViewId());
        alertText.setText("DDoS detected");
        alertText.setTextSize(20);
        alertText.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
        alertText.setTypeface(getResources().getFont(R.font.redhatdisplay_bold));

        android.widget.RelativeLayout.LayoutParams alertTextParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        alertTextParams.addRule(android.widget.RelativeLayout.BELOW, timestampText.getId()); // Position below timestamp
        alertTextParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        alertTextParams.setMargins(0, dpToPx(5), 0, 0); // Adjusted top margin
        alertText.setLayoutParams(alertTextParams);

        // IP addresses container
        LinearLayout ipContainer = new LinearLayout(getContext());
        ipContainer.setOrientation(LinearLayout.HORIZONTAL);
        ipContainer.setWeightSum(2);

        android.widget.RelativeLayout.LayoutParams ipContainerParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        ipContainerParams.addRule(android.widget.RelativeLayout.BELOW, alertText.getId()); // Position below alert text
        ipContainerParams.setMargins(0, dpToPx(10), 0, 0);
        ipContainer.setLayoutParams(ipContainerParams);

        // Source IP section
        LinearLayout srcIpSection = new LinearLayout(getContext());
        srcIpSection.setOrientation(LinearLayout.VERTICAL);
        srcIpSection.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView srcIpLabel = new TextView(getContext());
        srcIpLabel.setText("Source IP");
        srcIpLabel.setTextSize(12);
        srcIpLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.dgrey));

        TextView srcIpValue = new TextView(getContext());
        srcIpValue.setText(alertData.sourceIp != null && !alertData.sourceIp.isEmpty() ?
                alertData.sourceIp : "N/A");
        srcIpValue.setTextSize(12);
        srcIpValue.setTextColor(ContextCompat.getColor(getContext(), R.color.black));

        srcIpSection.addView(srcIpLabel);
        srcIpSection.addView(srcIpValue);

        // Destination IP section
        LinearLayout destIpSection = new LinearLayout(getContext());
        destIpSection.setOrientation(LinearLayout.VERTICAL);
        destIpSection.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView destIpLabel = new TextView(getContext());
        destIpLabel.setText("Destination IP");
        destIpLabel.setTextSize(12);
        destIpLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.dgrey));

        TextView destIpValue = new TextView(getContext());
        destIpValue.setText(alertData.destinationIp != null && !alertData.destinationIp.isEmpty() ?
                alertData.destinationIp : "N/A");
        destIpValue.setTextSize(12);
        destIpValue.setTextColor(ContextCompat.getColor(getContext(), R.color.black));

        destIpSection.addView(destIpLabel);
        destIpSection.addView(destIpValue);

        ipContainer.addView(srcIpSection);
        ipContainer.addView(destIpSection);

        // Add all elements to main container
        mainContainer.addView(alertIcon);
        mainContainer.addView(timestampText);
        mainContainer.addView(alertText);
        mainContainer.addView(ipContainer);

        cardView.addView(mainContainer);
        alertsContainer.addView(cardView);
    }

    private void addNormalCard(AlertData alertData) {
        // Create MaterialCardView programmatically
        MaterialCardView cardView = new MaterialCardView(getContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dpToPx(8), 0, 0);
        cardView.setLayoutParams(cardParams);
        cardView.setRadius(dpToPx(10));
        cardView.setCardElevation(0);
        cardView.setCardBackgroundColor(ContextCompat.getColor(getContext(), R.color.blue));
        cardView.setStrokeWidth(0);

        // Create main container with RelativeLayout to position icon
        android.widget.RelativeLayout mainContainer = new android.widget.RelativeLayout(getContext());
        mainContainer.setPadding(dpToPx(15), dpToPx(15), dpToPx(15), dpToPx(15));

        // Normal text
        // Normal icon
        ImageView normalIcon = new ImageView(getContext());
        normalIcon.setImageResource(R.drawable.notifications_24px);
        normalIcon.setColorFilter(ContextCompat.getColor(getContext(), R.color.dblue));

        android.widget.RelativeLayout.LayoutParams iconParams =
                new android.widget.RelativeLayout.LayoutParams(dpToPx(25), dpToPx(25));
        iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        iconParams.setMargins(0, dpToPx(0), 0, 0); // Reduced top margin for icon
        normalIcon.setLayoutParams(iconParams);

        // Timestamp
        TextView timestampText = new TextView(getContext());
        timestampText.setId(View.generateViewId()); // Give timestamp an ID
        timestampText.setText(formatTimestamp(alertData.lastNormalTime));
        timestampText.setTextSize(13);
        timestampText.setTextColor(ContextCompat.getColor(getContext(), R.color.dblue));

        android.widget.RelativeLayout.LayoutParams timestampParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        timestampParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        timestampParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        timestampParams.setMargins(0, dpToPx(0), 0, 0); // Reduced top margin for timestamp
        timestampText.setLayoutParams(timestampParams);

        // Normal text
        TextView normalText = new TextView(getContext());
        normalText.setId(View.generateViewId());
        normalText.setText("System is normal");
        normalText.setTextSize(20);
        normalText.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
        normalText.setTypeface(getResources().getFont(R.font.redhatdisplay_bold));

        android.widget.RelativeLayout.LayoutParams normalTextParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        normalTextParams.addRule(android.widget.RelativeLayout.BELOW, timestampText.getId()); // Position below timestamp
        normalTextParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        normalTextParams.setMargins(0, dpToPx(5), 0, 0); // Adjusted top margin
        normalText.setLayoutParams(normalTextParams);

        // Add elements to main container
        mainContainer.addView(timestampText);
        mainContainer.addView(normalText);
        mainContainer.addView(normalIcon);

        cardView.addView(mainContainer);
        alertsContainer.addView(cardView);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear views to prevent memory leaks
        if (alertsContainer != null) {
            alertsContainer.removeAllViews();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Save alerts before destroying fragment
        if (alertsQueue != null && !alertsQueue.isEmpty()) {
            saveAlertsToPreferences();
        }
    }
}