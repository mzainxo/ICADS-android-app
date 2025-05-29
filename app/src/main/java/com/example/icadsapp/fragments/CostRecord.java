package com.example.icadsapp.fragments;

public class CostRecord {
    public long timestamp; // Attack end time in milliseconds
    public double cost;
    public String duration; // Human-readable duration

    public CostRecord(long timestamp, double cost, String duration) {
        this.timestamp = timestamp;
        this.cost = cost;
        this.duration = duration;
    }
}
