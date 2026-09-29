package com.obddroid.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.LifecycleOwner;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.CommService;

import java.util.List;
import java.util.logging.Logger;

/**
 * Live PID values. Starts live data polling when the adapter is idle, and stops
 * it again when leaving the screen if it was started here.
 */
public class LiveDataScreen extends RefreshingScreen {

    private static final Logger log = Logger.getLogger("LiveDataScreen");

    private static final long REFRESH_INTERVAL_MS = 1000;

    /** True while this screen owns the OBD_SVC_DATA polling it started. */
    private boolean startedPolling = false;

    public LiveDataScreen(@NonNull CarContext carContext) {
        super(carContext, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        super.onStop(owner);
        if (startedPolling && CommService.elm.getService() == ObdProt.OBD_SVC_DATA) {
            CommService.elm.setService(ObdProt.OBD_SVC_NONE);
            log.info("Stopped live data polling started from Android Auto");
        }
        startedPolling = false;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (!ObdCarData.isAdapterConnected()) {
            return message(ObdCarData.getNotConnectedText());
        }

        int service = CommService.elm.getService();
        if (service == ObdProt.OBD_SVC_NONE) {
            // Adapter idle: start polling, as LiveDataActivity does on the phone.
            CommService.elm.setService(ObdProt.OBD_SVC_DATA);
            startedPolling = true;
            log.info("Started live data polling from Android Auto");
        } else if (service != ObdProt.OBD_SVC_DATA) {
            return message("The phone app is using the adapter for another task. "
                    + "Live data will appear when it finishes.");
        }

        List<EcuDataPv> pids = ObdCarData.getLiveData();
        if (pids.isEmpty()) {
            return new ListTemplate.Builder()
                    .setTitle("Live data")
                    .setHeaderAction(Action.BACK)
                    .setLoading(true)
                    .build();
        }

        int limit = Math.min(pids.size(), getListLimit());
        ItemList.Builder list = new ItemList.Builder();
        for (int i = 0; i < limit; i++) {
            EcuDataPv pv = pids.get(i);
            list.addItem(new Row.Builder()
                    .setTitle(ObdCarData.getDescription(pv))
                    .addText(ObdCarData.formatValue(pv))
                    .build());
        }

        return new ListTemplate.Builder()
                .setTitle("Live data")
                .setHeaderAction(Action.BACK)
                .setSingleList(list.build())
                .build();
    }

    private Template message(String text) {
        return new MessageTemplate.Builder(text)
                .setTitle("Live data")
                .setHeaderAction(Action.BACK)
                .build();
    }
}
