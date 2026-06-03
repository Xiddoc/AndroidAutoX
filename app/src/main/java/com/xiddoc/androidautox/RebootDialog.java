package com.xiddoc.androidautox;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

public class RebootDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.reboot_prompt)
                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(getContext(), getString(R.string.rebooting_action), Toast.LENGTH_LONG).show();
                        MainActivity.runSuWithCmd("reboot now");
                    }
                })
                .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        return builder.create();
    }
}