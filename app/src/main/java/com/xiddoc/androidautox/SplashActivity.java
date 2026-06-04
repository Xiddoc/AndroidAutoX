package com.xiddoc.androidautox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.appcompat.app.AppCompatActivity;
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

import com.xiddoc.androidautox.Utils.Version;

import static com.xiddoc.androidautox.MainActivity.runSuWithCmd;

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

    // Tri-state result of the asynchronous root request, read across threads:
    //   null  = request not finished yet (pending)
    //   TRUE  = root granted
    //   FALSE = root denied / unavailable
    volatile Boolean isDeviceRooted; // package-private for unit tests (RootRequester injection)

    // Single-flight guard: true while a root request thread is running so taps / onResume
    // re-entries don't spawn duplicate, uncancellable background threads.
    volatile boolean rootRequestInFlight = false; // package-private: unit tests await it settling

    // Set in onDestroy so background threads know to drop their result instead of posting
    // work (e.g. startActivity / showing a dialog) onto a finishing/destroyed activity.
    private volatile boolean destroyed = false;

    // True once the user has expressed intent to proceed (tapped Proceed / "Request again",
    // or the skip-warning fast path is active). Until then a freshly-arrived root result is
    // just cached -- we must NOT auto-navigate past the disclaimer the user hasn't accepted.
    private boolean userRequestedProceed = false;

    // The libsu boundary, behind an interface so unit tests can stub it without a device.
    // Defaults to the real MainActivity/RootDb implementation.
    interface RootRequester {
        /** Optionally re-issues su (re-prompting Magisk) and reports whether root was granted. */
        boolean acquireRoot(boolean forceReprompt);
        /** Pre-binds the root SQLite service once root is confirmed. */
        void onRootGranted();
    }

    // package-private + non-final so unit tests can inject a stub before onResume.
    RootRequester rootRequester = new RootRequester() {
        @Override
        public boolean acquireRoot(boolean forceReprompt) {
            if (forceReprompt) {
                // Close the cached libsu shell so the next acquisition builds a fresh one
                // and genuinely re-runs su (see MainActivity.resetRootShell()).
                MainActivity.resetRootShell();
            }
            return MainActivity.hasRootAccess();
        }

        @Override
        public void onRootGranted() {
            try {
                RootDb.get();
            } catch (Throwable t) {
                Log.e("com.xiddoc.androidautox", "Failed to bind root service", t);
            }
        }
    };

    private static final String actualVersion = BuildConfig.VERSION_NAME;
    private static final String BASE_URL = "https://api.github.com/repos/Xiddoc/AndroidAutoX/releases/latest";

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

        // The update check is best-effort; never let a transport/network setup problem
        // (e.g. a missing HTTP stack under unit tests) take down the splash screen.
        try {
            requestLatest();
        } catch (Throwable t) {
            Log.w("com.xiddoc.androidautox", "Update check unavailable", t);
        }

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
        Log.v("com.xiddoc.androidautox", "Engaging countdown");
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
    //
    // Uses the pure RootGate decision logic so the three states are handled
    // distinctly and we never falsely report "no root" while the async request is
    // still pending:
    //   PROCEED    -> enter the app
    //   WAIT       -> the async root request hasn't finished; show a loading state.
    //                 We do NOT spawn a polling thread here: the request thread itself
    //                 posts back to the UI thread (see onRootResultArrived) and re-runs
    //                 this method as soon as the result is known (event-driven).
    //   SHOW_RETRY -> request finished without root; offer a retry path
    // Never NPEs and never blocks the main thread on su.
    void proceedIfRooted() {
        // Record intent so a later async result can auto-advance this same flow.
        userRequestedProceed = true;
        switch (RootGate.decide(isDeviceRooted)) {
            case PROCEED:
                if (newVersionName != null) {
                    mainActivityIntent.putExtra("NewVersionName", newVersionName);
                }
                startActivity(mainActivityIntent);
                finish();
                break;
            case WAIT:
                // Async root request still in flight: don't dead-end to NoRootDialog.
                // Show a brief loading toast; onRootResultArrived() will re-enter this
                // method once the request completes.
                Toast.makeText(this, R.string.requesting_root, Toast.LENGTH_SHORT).show();
                break;
            case SHOW_RETRY:
            default:
                noRootDialog.show(getSupportFragmentManager(), "NoRootDialog");
                break;
        }
    }

    // Re-triggers root acquisition. Called by NoRootDialog's "Request again" action so a
    // denied/unavailable result is not a dead end. Clears the previous result, then kicks
    // off a fresh request that first closes the cached libsu shell so su is genuinely
    // re-issued (re-prompting Magisk) instead of reusing the warm/denied shell.
    void retryRootRequest() {
        isDeviceRooted = null;
        Toast.makeText(this, R.string.requesting_root, Toast.LENGTH_SHORT).show();
        requestRootAsync(true);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Kick off root acquisition once the window is up so Magisk's grant dialog
        // surfaces over the visible splash rather than before it is drawn.
        requestRootAsync(false);
    }

    @Override
    protected void onDestroy() {
        // Tell any in-flight request thread to drop its result rather than posting work
        // (startActivity / dialog) onto a destroyed activity.
        destroyed = true;
        super.onDestroy();
    }

    // Requests root off the main thread (avoids an ANR — acquisition blocks on the Magisk
    // prompt). When the result lands it posts back to the UI thread to re-evaluate via
    // RootGate (event-driven; no busy-poll). At most one request runs at a time.
    //
    // @param forceReprompt when true, close the cached libsu shell first so a fresh su is
    //                      issued (used by the "Request again" retry); the initial onResume
    //                      request passes false.
    private void requestRootAsync(final boolean forceReprompt) {
        // Single-flight: a request is already running, let it finish and post its result.
        if (rootRequestInFlight) {
            return;
        }
        rootRequestInFlight = true;

        new Thread() {
            @Override
            public void run() {
                // Acquire root via libsu; this is what surfaces Magisk's grant prompt.
                // We check the SHELL's real root status -- a non-root fallback shell would
                // still echo "1", so command output can't be trusted as a root signal.
                Log.v("com.xiddoc.androidautox", "Requesting root (su), forceReprompt=" + forceReprompt);
                boolean rooted = rootRequester.acquireRoot(forceReprompt);
                Log.v("com.xiddoc.androidautox", "Root request result: rooted=" + rooted);

                // Pre-bind the root SQLite service now (off the main thread) so later DB
                // work -- including UI screens that read on the main thread -- finds it
                // already connected.
                if (rooted) {
                    rootRequester.onRootGranted();
                }

                final Boolean result = rooted ? Boolean.TRUE : Boolean.FALSE;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rootRequestInFlight = false;
                        // Drop stale results if the activity went away while we worked.
                        if (destroyed || isFinishing()) {
                            return;
                        }
                        isDeviceRooted = result;
                        onRootResultArrived();
                    }
                });
            }
        }.start();
    }

    // Called on the UI thread once an async root request has stored its result. Re-runs
    // the proceed gate. For the future-launch fast path (skipStartupWarning) this auto-
    // proceeds; otherwise it advances any user who is already waiting (WAIT toast) or who
    // just tapped "Request again". A user still on the disclaimer simply has a fresh
    // result ready for when they tap Proceed.
    private void onRootResultArrived() {
        // skipStartupWarning is a standing intent to proceed; otherwise only advance once
        // the user has actually tapped Proceed / "Request again". Until then we just keep
        // the result cached so we don't auto-skip the disclaimer the user hasn't accepted.
        if (skipStartupWarning || userRequestedProceed) {
            proceedIfRooted();
        }
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