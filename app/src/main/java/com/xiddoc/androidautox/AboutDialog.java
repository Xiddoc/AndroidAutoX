package com.xiddoc.androidautox;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;


public class AboutDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        builder.setMessage(Html.fromHtml(getString(R.string.about_part_one)));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setNeutralButton(R.string.translators_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setMessage(Html.fromHtml("Russian: Diversant96<br>" +
                        "Dutch: Coyenzo, smit.sydney<br>" +
                        "Slovak: Jozo19<br>" +
                        "Spanish: Krilok, jdavidcs4<br>" +
                        "Czech: Martin2412, LLZN, pesekpata<br>" +
                        "Slovenian: Brubblu<br>" +
                        "French: Nova.kin, Sperafico<br>" +
                        "Brazilian Portuguese: Gsproenca<br>" +
                        "Vietnamese: Quang.chk1, votruongvu.hcm<br>" +
                        "German: Lassmiranda, cbrosius<br>" +
                        "Korean: Mabig<br>" +
                        "Catalan: rogerpi95<br>" +
                        "Polish: Nor7ovich, MarcinzSowie, Geranium743<br>" +
                        "Serbian: BojanJagodic91<br>" +
                        "Chinese (simplified): Danchunlanse<br>" +
                        "Chinese (traditional): chh2299<br>" +
                        "Hebrew: yaari302<br>" +
                        "Japanese: HHSAN<br>" +
                        "Greek: panbimis<br>" +
                        "Turkish: un4saken<br>" +
                        "Arabic: almaqhor<br>" +
                        "<br>Interested in translating AndroidAutoX in your own language? Open a translation PR on <a href=\"https://github.com/Xiddoc/AndroidAutoX\">GitHub</a>!"));
                builder.setCancelable(true);
                builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog Alert1 = builder.create();
                Alert1.show();
                ((TextView) Alert1.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

            }
        });
        AlertDialog Alert = builder.create();
        Alert.show();
        ((TextView)Alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        return Alert;
    }


}