package com.obddroid.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

import com.obddroid.BuildConfig;

/**
 * Entry point for Android Auto.
 *
 * The car screens mirror the connection owned by the phone app: the user connects
 * to the adapter on the phone, and Android Auto shows status, live data and fault
 * codes from that same session. No second adapter connection is opened here.
 */
public class ObdCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Debug builds accept any host so the Desktop Head Unit (DHU) can connect.
        if (BuildConfig.DEBUG) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
        }
        return new HostValidator.Builder(getApplicationContext())
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build();
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new ObdCarSession();
    }
}
