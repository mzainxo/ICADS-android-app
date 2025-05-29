package com.example.icadsapp.fragments;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.icadsapp.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class CostGraphFragment extends Fragment {
    private LineChart chart;
    private Button clearButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cost_graph, container, false);
        chart = view.findViewById(R.id.cost_chart);
        clearButton = view.findViewById(R.id.clear_button);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupChart();
        loadData();

        clearButton.setOnClickListener(v -> {
            CostRepository.clearRecords();
            loadData(); // Refresh with empty data
            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new DateAxisValueFormatter());
        xAxis.setLabelCount(6);
        xAxis.setGranularity(86400000); // 1 day in milliseconds

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setValueFormatter(new CostAxisValueFormatter());
    }

    private void loadData() {
        List<CostRecord> records = CostRepository.getAllRecords();
        List<Entry> entries = new ArrayList<>();

        for (CostRecord record : records) {
            entries.add(new Entry(record.timestamp, (float) record.cost));
        }

        if (entries.isEmpty()) {
            // Show empty state
            chart.clear();
            chart.setNoDataText("No cost records available");
            chart.setNoDataTextColor(Color.GRAY);
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Downtime Costs");
        dataSet.setColor(Color.BLUE);
        dataSet.setValueTextColor(Color.DKGRAY);
        dataSet.setCircleColor(Color.RED);
        dataSet.setCircleRadius(4f);
        dataSet.setLineWidth(2f);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate(); // Refresh chart
    }

    // Custom value formatters
    private static class DateAxisValueFormatter extends ValueFormatter {
        private final SimpleDateFormat mFormat = new SimpleDateFormat("MMM dd", Locale.US);

        @Override
        public String getFormattedValue(float value) {
            return mFormat.format(new Date((long) value));
        }
    }

    private static class CostAxisValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            if (value >= 1000000) {
                return String.format(Locale.US, "₹%.1fM", value / 1000000);
            } else if (value >= 1000) {
                return String.format(Locale.US, "₹%.0fK", value / 1000);
            } else {
                return String.format(Locale.US, "₹%.0f", value);
            }
        }
    }
}
