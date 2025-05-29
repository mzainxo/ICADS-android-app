package com.example.icadsapp.fragments;

import java.util.ArrayList;
import java.util.List;

public class CostRepository {
    private static final List<CostRecord> records = new ArrayList<>();

    public static void addRecord(CostRecord record) {
        records.add(record);
    }

    public static List<CostRecord> getAllRecords() {
        return new ArrayList<>(records); // Return copy for safety
    }

    public static void clearRecords() {
        records.clear();
    }
}