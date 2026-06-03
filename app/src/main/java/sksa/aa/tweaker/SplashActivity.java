package sksa.aa.tweaker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import sksa.aa.tweaker.Utils.Version;

import static sksa.aa.tweaker.MainActivity.runSuWithCmd;

public class SplashActivity extends AppCompatActivity {


    Context context;
    String newVersionName;

    // Shared state used by the proceed helper so both the proceed button and the
    // "don't show again" button can reuse the exact same root-gated flow.
    private Intent mainActivityIntent;
    private NoRootDialog noRootDialog;

    // Persisted flag (in the existing "MainActivity" prefs file). When true, future
    // launches skip the disclaimer/countdown UX. Default false. NOTE: this key is
    // deliberately NOT part of the tweak-default reset block in onCreate.
    private static final String SKIP_STARTUP_WARNING_KEY = "skip_startup_warning";

    // When true, the disclaimer UX is bypassed and we auto-proceed (root-gated) as
    // soon as the async root result arrives, instead of waiting for a button tap.
    private boolean skipStartupWarning = false;

    // Result of the asynchronous root request. null = not-yet/unknown, non-null = completed.
    private volatile StreamLogs isDeviceRooted;

    // Guards against kicking off the root request more than once (e.g. onResume re-entry).
    private boolean rootRequestStarted = false;

    private static final String actualVersion = BuildConfig.VERSION_NAME;
    private static final String BASE_URL = "https://api.github.com/repos/Xiddoc/AA-Tweaker/releases/latest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        mainActivityIntent = new Intent(this, MainActivity.class);

        noRootDialog = new NoRootDialog();

        final SharedPreferences sharedPreferences = getSharedPreferences("MainActivity", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("aa_speed_hack", false);
        editor.putBoolean("aa_six_tap", false);
        editor.putBoolean("aa_startup_policy", false);
        editor.putBoolean("aa_patched_apps", false);
        editor.putBoolean("aa_message_autoread", false);
        editor.putBoolean("aa_battery_outline", false);
        editor.putBoolean("force_ws", false);
        editor.putBoolean("force_no_ws", false);
        editor.putBoolean("force_portrait", false);
        editor.putBoolean("aa_hun_ms", false);
        editor.putBoolean("aa_media_hun", false);
        editor.putBoolean("multi_display", false);
        editor.putBoolean("battery_saver_warning", false);
        editor.putBoolean("kill_telemetry", false);
        editor.putBoolean("aa_inertial_scroll", false);
        editor.putBoolean("aa_bitrate_usb", false);
        editor.putBoolean("aa_bitrate_wireless", false);
        editor.putBoolean("coolwalk_daynight_tweak", false);
        editor.putBoolean("aa_activate_coolwalk", false);
        editor.putBoolean("aa_deactivate_coolwalk", false);
        editor.putBoolean("aa_activate_declinesms", false);
        editor.putBoolean("aa_activate_assistant_tips", false);
        editor.putBoolean("aa_new_seekbar", false);
        editor.putBoolean("uxprototype_tweak", false);
        editor.putBoolean("aa_material_you", false);
        editor.putBoolean("aa_vertical_bar", false);
        editor.commit();

        // Read the persisted skip flag AFTER the tweak-default reset block above
        // (which deliberately does not touch this key).
        skipStartupWarning = sharedPreferences.getBoolean(SKIP_STARTUP_WARNING_KEY, false);

        requestLatest();

        final Button continueButton = findViewById(R.id.proceed_button);
        final Button disableWarningButton = findViewById(R.id.disable_warning_button);

        if (skipStartupWarning) {
            // Future-launch fast path: hide the disclaimer UX entirely and let the
            // async root result drive an auto-proceed (see requestRootAsync()). We do
            // NOT start either countdown and we do NOT touch su on the main thread.
            findViewById(R.id.warning_text).setVisibility(View.GONE);
            findViewById(R.id.warning_content).setVisibility(View.GONE);
            continueButton.setVisibility(View.GONE);
            disableWarningButton.setVisibility(View.GONE);
            return;
        }

        continueButton.setEnabled(false);
        Log.v("sksa.aa.tweaker", "Engaging countdown");
        new CountDownTimer(5000, 10) {
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) ( 1 + (millisUntilFinished/1000));
                continueButton.setText(getString(R.string.proceed) + " (" + secondsRemaining + ")");
            }

            @Override
            public void onFinish() {
                continueButton.setEnabled(true);
                continueButton.setText(R.string.proceed);
            }
        }.start();

        continueButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        proceedIfRooted();
                    }
                });

        // Bottom "don't show this warning again" button. Starts disabled (set in XML)
        // and runs its OWN 10s countdown, mirroring the proceed button's idiom.
        disableWarningButton.setEnabled(false);
        new CountDownTimer(10000, 10) {
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) ( 1 + (millisUntilFinished/1000));
                disableWarningButton.setText(getString(R.string.disable_startup_warning) + " (" + secondsRemaining + ")");
            }

            @Override
            public void onFinish() {
                disableWarningButton.setEnabled(true);
                disableWarningButton.setText(R.string.disable_startup_warning);
            }
        }.start();

        disableWarningButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Persist the flag so future launches skip the disclaimer,
                        // then continue exactly like the proceed button (root-gated).
                        sharedPreferences.edit().putBoolean(SKIP_STARTUP_WARNING_KEY, true).commit();
                        Toast.makeText(SplashActivity.this, R.string.startup_warning_disabled, Toast.LENGTH_SHORT).show();
                        proceedIfRooted();
                    }
                });
    }

    // Shared root-gated proceed flow used by both bottom buttons.
    // Treats null (async root result not-yet-arrived) or non-"1" as not-rooted,
    // so it never NPEs and never blocks the main thread on su.
    private void proceedIfRooted() {
        StreamLogs rootResult = isDeviceRooted;
        if (rootResult != null && "1".equals(rootResult.getInputStreamLog())) {
            if (newVersionName != null) {
                mainActivityIntent.putExtra("NewVersionName", newVersionName);
            }
            startActivity(mainActivityIntent);
            finish();
        } else {
            noRootDialog.show(getSupportFragmentManager(), "NoRootDialog");
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Kick off root acquisition once the window is up so Magisk's grant dialog
        // surfaces over the visible splash rather than before it is drawn.
        requestRootAsync();
    }

    // Requests root off the main thread (matching MainActivity's new Thread() idiom) and then
    // runs copyAssets(), which depends on root for its chmod. Avoids ANR by keeping
    // su.waitFor() off the main thread. The 5s countdown gives this time to complete and the
    // user time to tap "Grant". Runs at most once.
    private void requestRootAsync() {
        if (rootRequestStarted) {
            return;
        }
        rootRequestStarted = true;

        new Thread() {
            @Override
            public void run() {
                // Explicit early su request so Magisk shows the prompt unmistakably.
                Log.v("sksa.aa.tweaker", "Requesting root (su)");
                isDeviceRooted = runSuWithCmd("echo 1");
                Log.v("sksa.aa.tweaker", "Root request result: " + isDeviceRooted.getInputStreamLog());

                copyAssets();

                // Future-launch fast path: now that the async root result is in,
                // auto-proceed (root-gated) on the UI thread instead of waiting for a tap.
                if (skipStartupWarning) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                proceedIfRooted();
                            }
                        }
                    });
                }
            }
        }.start();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    private void copyAssets() {
        String path = getApplicationInfo().dataDir;
        boolean sqlite3 = new File(path, "sqlite3").exists();

        if (sqlite3) {
            return;
        }

        File file = new File(path, "sqlite3");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.v("sksa.aa.tweaker", "\n--  Copy sqlite3 to data directory  --");
                }
            });
            InputStream in;
            OutputStream out;
            try {
                in = this.getResources().openRawResource(R.raw.sqlite3);

                String outDir = getApplicationInfo().dataDir;

                File outFile = new File(outDir, "sqlite3");

                out = new FileOutputStream(outFile);
                copyFile(in, out);
                in.close();
                out.flush();
                out.close();
            } catch (IOException e) {
                Log.e("sksa.aa.tweaker", "Failed to copy asset file: sqlite3", e);
            }
            Log.v("sksa.aa.tweaker", runSuWithCmd("chmod 777 " + path + "/sqlite3").getStreamLogsWithLabels());

    }

    public String requestLatest() {

        RequestQueue queue = Volley.newRequestQueue(this.getApplicationContext());

        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, BASE_URL, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String fetchedVersion = response.getString("tag_name");
                            Version newCheck = new Version(fetchedVersion.substring(1));
                            Version actualCheck = null;
                            try {
                                actualCheck = new Version(actualVersion);
                                if (actualCheck.compareTo(newCheck) == -1) {
                                    newVersionName = fetchedVersion.substring(1);
                                }
                            } catch (IllegalArgumentException e) {
                                Toast.makeText(SplashActivity.this, "Debug build: could not check latest version", Toast.LENGTH_LONG).show();
                                newVersionName = null;
                                e.printStackTrace();
                            }


                        } catch  (JSONException e) {
                            newVersionName = null;
                            Toast.makeText(SplashActivity.this, "Attention: could not check latest version. Ensure you have internet connectin.", Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {


                    @Override
                    public void onErrorResponse(VolleyError error) {
                        newVersionName = null;
                    }
                });


        queue.add(jsonObjectRequest);
        return this.newVersionName;
    }




}