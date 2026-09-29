package com.obddroid.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.obddroid.ecu.EcuCodeItem;

import java.util.List;

/**
 * Fault codes from the most recent read on the phone. Reading or clearing codes
 * stays on the phone, where the user can review the details first.
 */
public class FaultCodesScreen extends RefreshingScreen {

    private static final long REFRESH_INTERVAL_MS = 5000;

    public FaultCodesScreen(@NonNull CarContext carContext) {
        super(carContext, REFRESH_INTERVAL_MS);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        List<EcuCodeItem> codes = ObdCarData.getFaultCodes();
        if (codes.isEmpty()) {
            String text = ObdCarData.hasNoCodesResult()
                    ? "No trouble codes set."
                    : "No fault codes yet. Read fault codes in the phone app to show them here.";
            return new MessageTemplate.Builder(text)
                    .setTitle("Fault codes")
                    .setHeaderAction(Action.BACK)
                    .build();
        }

        int limit = Math.min(codes.size(), getListLimit());
        ItemList.Builder list = new ItemList.Builder();
        for (int i = 0; i < limit; i++) {
            EcuCodeItem code = codes.get(i);
            Object description = code.get(EcuCodeItem.FID_DESCRIPT);
            list.addItem(new Row.Builder()
                    .setTitle(String.valueOf(code.get(EcuCodeItem.FID_CODE)))
                    .addText(description != null ? String.valueOf(description) : "")
                    .build());
        }

        String title = codes.size() > limit
                ? "Fault codes (" + limit + " of " + codes.size() + ")"
                : "Fault codes";
        return new ListTemplate.Builder()
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setSingleList(list.build())
                .build();
    }
}
