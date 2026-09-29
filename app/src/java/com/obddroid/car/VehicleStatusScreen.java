package com.obddroid.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

/**
 * Root Android Auto screen: adapter connection, vehicle, and entry points to
 * live data and fault codes.
 */
public class VehicleStatusScreen extends RefreshingScreen {

    private static final long REFRESH_INTERVAL_MS = 2000;

    public VehicleStatusScreen(@NonNull CarContext carContext) {
        super(carContext, REFRESH_INTERVAL_MS);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        boolean connected = ObdCarData.isAdapterConnected();

        Row adapterRow = new Row.Builder()
                .setTitle("Adapter")
                .addText(connected
                        ? ObdCarData.getAdapterStatus().toString()
                        : "Not connected. Connect to your adapter in the phone app.")
                .build();

        Row vehicleRow = new Row.Builder()
                .setTitle("Vehicle")
                .addText(ObdCarData.getVehicleName())
                .build();

        Row liveDataRow = new Row.Builder()
                .setTitle("Live data")
                .addText("Sensor readings from the engine")
                .setBrowsable(true)
                .setOnClickListener(() -> getScreenManager().push(new LiveDataScreen(getCarContext())))
                .build();

        int codeCount = ObdCarData.getFaultCodes().size();
        Row faultCodesRow = new Row.Builder()
                .setTitle("Fault codes")
                .addText(codeCount > 0
                        ? codeCount + (codeCount == 1 ? " code from last read" : " codes from last read")
                        : "Codes read on the phone")
                .setBrowsable(true)
                .setOnClickListener(() -> getScreenManager().push(new FaultCodesScreen(getCarContext())))
                .build();

        ItemList list = new ItemList.Builder()
                .addItem(adapterRow)
                .addItem(vehicleRow)
                .addItem(liveDataRow)
                .addItem(faultCodesRow)
                .build();

        return new ListTemplate.Builder()
                .setTitle("OBD-Droid")
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(list)
                .build();
    }
}
