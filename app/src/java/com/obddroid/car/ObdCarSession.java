package com.obddroid.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/**
 * Android Auto session. Opens on the vehicle status screen.
 */
public class ObdCarSession extends Session {

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        return new VehicleStatusScreen(getCarContext());
    }
}
