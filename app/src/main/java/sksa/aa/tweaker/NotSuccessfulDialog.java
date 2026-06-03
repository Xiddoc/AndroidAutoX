package sksa.aa.tweaker;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

public class NotSuccessfulDialog extends DialogFragment {

    private static final int MAX_LOG_CHARS = 5000;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.warning_title));
        builder.setMessage(R.string.error_generic);
        builder.setNeutralButton( getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton( R.string.open_issue_button
                ,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Bundle args = getArguments();
                        String tweak = args != null ? args.getString("tweak") : null;
                        String log = args != null ? args.getString("log") : null;

                        String title = (tweak != null && !tweak.isEmpty())
                                ? "Bug report: " + tweak
                                : "Bug report";

                        if (log == null) {
                            log = "";
                        }
                        if (log.length() > MAX_LOG_CHARS) {
                            log = log.substring(0, MAX_LOG_CHARS)
                                    + "\n...(log truncated, please paste the full log from the app's log screen)";
                        }

                        StringBuilder body = new StringBuilder();
                        body.append("Tweak applied: ")
                                .append(tweak != null && !tweak.isEmpty() ? tweak : "(unknown)")
                                .append("\n\n")
                                .append("Describe the problem here.\n\n")
                                .append("Recent log:\n")
                                .append("```\n")
                                .append(log)
                                .append("\n```\n");

                        Uri uri = new Uri.Builder()
                                .scheme("https")
                                .authority("github.com")
                                .appendPath("Xiddoc")
                                .appendPath("AA-Tweaker")
                                .appendPath("issues")
                                .appendPath("new")
                                .appendQueryParameter("title", title)
                                .appendQueryParameter("body", body.toString())
                                .build();

                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        } catch (ActivityNotFoundException e) {
                            e.printStackTrace();
                            Toast.makeText(getActivity(),
                                    getString(R.string.no_browser_found),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
        return builder.create();
    }
}
