package com.xiddoc.androidautox;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

public class NoRootDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.root_access_warning);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        AlertDialog Alert = builder.create();
        Alert.show();
        return builder.create();
    }
}