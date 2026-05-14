package com.camera.VendorTag.ui;

import android.os.Bundle;

import androidx.activity.ComponentActivity;

public final class VendorTagSettingsActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LumoMaterial3Ui.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LumoMaterial3Ui.refresh(this);
    }
}
