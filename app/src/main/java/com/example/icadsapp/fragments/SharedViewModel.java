package com.example.icadsapp.fragments;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    private final MutableLiveData<AlertsFragment.AlertData> alertData = new MutableLiveData<>();

    public void setAlertData(AlertsFragment.AlertData data) {
        alertData.setValue(data);
    }

    public LiveData<AlertsFragment.AlertData> getAlertData() {
        return alertData;
    }
}