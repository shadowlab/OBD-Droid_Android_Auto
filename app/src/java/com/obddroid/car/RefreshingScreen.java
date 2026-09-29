package com.obddroid.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Base screen that re-renders its template on a fixed interval while visible.
 *
 * Adapter data arrives on background threads, so screens poll the shared state
 * instead of reacting to every change; this also keeps template updates within
 * the rate the car host allows.
 */
abstract class RefreshingScreen extends Screen implements DefaultLifecycleObserver {

    /** Rows to show when the host does not report its own list limit. */
    private static final int DEFAULT_LIST_LIMIT = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long refreshIntervalMs;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            invalidate();
            handler.postDelayed(this, refreshIntervalMs);
        }
    };

    RefreshingScreen(@NonNull CarContext carContext, long refreshIntervalMs) {
        super(carContext);
        this.refreshIntervalMs = refreshIntervalMs;
        getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        handler.postDelayed(refreshTask, refreshIntervalMs);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        handler.removeCallbacks(refreshTask);
    }

    /**
     * Maximum number of list rows the connected host will display.
     */
    int getListLimit() {
        try {
            return getCarContext().getCarService(ConstraintManager.class)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST);
        } catch (RuntimeException e) {
            // Hosts on Car API level 1 do not provide ConstraintManager.
            return DEFAULT_LIST_LIMIT;
        }
    }
}
