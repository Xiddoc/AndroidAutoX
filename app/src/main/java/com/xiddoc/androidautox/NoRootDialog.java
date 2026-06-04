package com.xiddoc.androidautox;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

/**
 * Shown when root access is unavailable or denied. Offers a "Request again" action so
 * the user is not stuck in a dead end: tapping it re-triggers root acquisition (and thus
 * Magisk's grant prompt) via {@link SplashActivity#retryRootRequest()}.
 */
public class NoRootDialog extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.root_access_warning);
        builder.setCancelable(false);
        // Retry path: re-request root instead of just dismissing.
        builder.setPositiveButton(R.string.request_root_again, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Activity activity = getActivity();
                if (activity instanceof SplashActivity) {
                    ((SplashActivity) activity).retryRootRequest();
                }
            }
        });
        // Keep a plain dismiss option for users who simply want to close the dialog.
        builder.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        return builder.create();
    }
}
