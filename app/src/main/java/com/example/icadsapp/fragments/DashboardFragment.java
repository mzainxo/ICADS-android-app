package com.example.icadsapp.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.example.icadsapp.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DashboardFragment extends Fragment {

    private TextView accuracyText, benignText, ddosPacketsText,
            totalPacketsText, systemStatusText, cpuUsageText, destIpAddressText, dateTimeText, costText;
    private SharedViewModel sharedViewModel;
    private ValueEventListener valueEventListener;
    private DatabaseReference statsRef;
    private Thread dateTimeThread;
    private SimpleDateFormat firebaseDateFormat;  // For Firebase timestamps
    private SimpleDateFormat displayDateFormat;   // For current datetime
    private static final double COST_PER_SECOND = 5800.0; // Rs. 5800 per second

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup date format for Firebase timestamps (ISO 8601)
        firebaseDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        firebaseDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Setup date format for current datetime display
        displayDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy  HH:mm:ss", Locale.US);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // Initialize ViewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // Link TextViews
        accuracyText = view.findViewById(R.id.accuracy_count);
        benignText = view.findViewById(R.id.benign_count);
        ddosPacketsText = view.findViewById(R.id.ddos_count);
        totalPacketsText = view.findViewById(R.id.tpckts_count);
        systemStatusText = view.findViewById(R.id.sysstatus_text);
        cpuUsageText = view.findViewById(R.id.cpuu_count);
        destIpAddressText = view.findViewById(R.id.ipv4_count);
        dateTimeText = view.findViewById(R.id.current_datetime_txt);
        costText = view.findViewById(R.id.downtime_count);

        // Start auto-refreshing date and time in a separate thread
        startDateTimeUpdater();

        // Initialize Firebase reference
        statsRef = FirebaseDatabase.getInstance().getReference("stats");

        setupFirebaseListener();
        // Add click listener to downtime card
        MaterialCardView downtimeCard = view.findViewById(R.id.downtime_card);
        downtimeCard.setOnClickListener(v -> openCostGraphFragment());
        return view;
    }

    private void startDateTimeUpdater() {
        dateTimeThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (dateTimeText != null) {
                                dateTimeText.setText(displayDateFormat.format(new Date()));
                            }
                        });
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Log.d("DashboardFragment", "DateTimeUpdater thread interrupted");
            }
        });
        dateTimeThread.start();
    }

    private void setupFirebaseListener() {
        valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if fragment is still attached
                if (!isAdded() || getContext() == null) return;

                if (snapshot.exists()) {
                    // Extract data
                    Double accuracy = snapshot.child("accuracy").getValue(Double.class);
                    Double cpuUsage = snapshot.child("cpu_usage").getValue(Double.class);
                    Long benign = snapshot.child("benign").getValue(Long.class);
                    Long ddosPackets = snapshot.child("ddos_packets").getValue(Long.class);
                    Long totalPackets = snapshot.child("total_packets").getValue(Long.class);
                    String systemStatus = snapshot.child("system_status").getValue(String.class);
                    String sourceIpAddress = snapshot.child("source_ip").getValue(String.class);
                    String destIpAddress = snapshot.child("destination_ip").getValue(String.class);
                    String lastDdosTime = snapshot.child("last_ddos_time").getValue(String.class);
                    String lastNormalTime = snapshot.child("last_normal_time").getValue(String.class);

                    // Update UI
                    if (accuracy != null) accuracyText.setText(String.format("%.1f%%", accuracy * 100));
                    if (cpuUsage != null) cpuUsageText.setText(String.format("%.0f%%", cpuUsage));
                    if (benign != null) benignText.setText(String.valueOf(benign));
                    if (ddosPackets != null) ddosPacketsText.setText(String.valueOf(ddosPackets));
                    if (totalPackets != null) totalPacketsText.setText(String.valueOf(totalPackets));
                    if (systemStatus != null) systemStatusText.setText(systemStatus);
                    if (destIpAddress != null) destIpAddressText.setText(destIpAddress);

                    // Set status color
                    if ("DDoS".equals(systemStatus)) {
                        systemStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
                    } else {
                        systemStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.dblue));
                    }

                    // Create and share alert data
                    AlertsFragment.AlertData alertData = new AlertsFragment.AlertData(
                            destIpAddress,
                            lastDdosTime,
                            lastNormalTime,
                            systemStatus,
                            sourceIpAddress
                    );
                    sharedViewModel.setAlertData(alertData);
                    if (lastDdosTime != null && !lastDdosTime.isEmpty()) {
                        calculateDowntimeCost(lastDdosTime, lastNormalTime);
                    } else {
                        costText.setText("Rs. 0");
                    }
                } else {
                    Log.w("DashboardFragment", "No 'stats' node found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Log.e("DashboardFragment", "Firebase error: " + error.getMessage());
                    Toast.makeText(requireContext(), "Firebase error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };

        statsRef.addValueEventListener(valueEventListener);
    }

    private void calculateDowntimeCost(String ddosStartStr, String normalEndStr) {
        if (ddosStartStr == null || ddosStartStr.isEmpty()) {
            costText.setText("Rs. 0");
            return;
        }

        try {
            // Parse start time and remove nanoseconds if present
            String cleanDdosTime = ddosStartStr.contains(".")
                    ? ddosStartStr.substring(0, ddosStartStr.indexOf('.'))
                    : ddosStartStr;

            Date ddosStart = firebaseDateFormat.parse(cleanDdosTime);
            Date normalEnd = new Date(); // Default to current time (ongoing attack)

            // Use end time if available
            if (normalEndStr != null && !normalEndStr.isEmpty()) {
                String cleanNormalTime = normalEndStr.contains(".")
                        ? normalEndStr.substring(0, normalEndStr.indexOf('.'))
                        : normalEndStr;
                normalEnd = firebaseDateFormat.parse(cleanNormalTime);
            }

            // Calculate duration in seconds
            long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(
                    normalEnd.getTime() - ddosStart.getTime()
            );

            if (durationSeconds < 0) {
                costText.setText("Rs. 0");
                return;
            }

            double totalCost = durationSeconds * COST_PER_SECOND;
            costText.setText(String.format(Locale.US, "Rs. %.0f", totalCost));
            // Save record only when attack is over
            if (normalEndStr != null && !normalEndStr.isEmpty()) {
                saveCostRecord(ddosStart, normalEnd, durationSeconds, totalCost);
            }

        } catch (ParseException e) {
            Log.e("DashboardFragment", "Date parsing error: " + e.getMessage());
            costText.setText("Rs. N/A");
        }
    }
    private void saveCostRecord(Date startTime, Date endTime, long durationSeconds, double cost) {
        // Create human-readable duration string
        String duration = String.format(Locale.US, "%dh %02dm %02ds",
                durationSeconds / 3600,
                (durationSeconds % 3600) / 60,
                durationSeconds % 60);

        // Create and save record
        CostRecord record = new CostRecord(
                endTime.getTime(),
                cost,
                duration
        );

        CostRepository.addRecord(record);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up Firebase listener
        if (statsRef != null && valueEventListener != null) {
            statsRef.removeEventListener(valueEventListener);
        }
        // Stop the date-time updater thread
        if (dateTimeThread != null && dateTimeThread.isAlive()) {
            dateTimeThread.interrupt();
            dateTimeThread = null;
        }
    }

    private void openCostGraphFragment() {
        // Create new fragment instance
        Fragment costGraphFragment = new CostGraphFragment();

        // Get the fragment manager and start a transaction
        FragmentManager fragmentManager = getParentFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Replace whatever is in the fragment_container view with this fragment
        transaction.replace(R.id.frame_layout, costGraphFragment);

        // Add the transaction to the back stack (optional)
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
    }
}
