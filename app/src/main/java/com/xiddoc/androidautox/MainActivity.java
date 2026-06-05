package com.xiddoc.androidautox;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xiddoc.androidautox.CarRemoverActivity.CarRemover;
import com.xiddoc.androidautox.Utils.BottomDialog;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

    public static String appDirectory = new String();

    boolean suitableMethodFound;

    private boolean temp;

    private static Context mContext;
    private ImageView noSpeedRestrictionsStatus;
    private ImageView taplimitstatus;
    private ImageView navstatus;
    private ImageView patchappstatus;
    private ImageView messageAutoReadStatus;
    private ImageView batteryOutlineStatus;
    private ImageView forceWideScreenStatus;
    private ImageView forcePortraitStatus;
    private ImageView messagesHunStatus;
    private ImageView mediaHunStatus;
    private ImageView intertialScrollStatus;
    private ImageView btstatus;
    private ImageView mdstatus;
    private ImageView batteryWarningStatus;
    private ImageView verticalBarStatus;
    private ImageView telemetryStatus;
    private ImageView forceNoWideScreenStatus;
    private ImageView usbBitrateStatus;
    private ImageView wifiBitrateStatus;
    private ImageView newSeekbarTweakStatus;
    private ImageView coolwalkTweakStatus;
    private ImageView nocoolwalkTweakStatus;
    private ImageView assistantTipsTweakStatus;
    private ImageView declineSmsTweakStatus;
    private ImageView uxprototypeTweakStatus;
    private ImageView materialYouTweakStatus;
    private TextView currentlySetHun;
    private TextView currentlySetMediaHun;
    private TextView currentlySetUSBSeekbar;
    private TextView currentlySetWiFiSeekbar;
    private Button rebootButton;
    private View rebootContainer;

    /**
     * Registry of flag-mapped tweak keys → the UI views that show their status, populated as
     * the views are looked up in {@link #onCreate}. After root is available a single background
     * pass ({@link #reconcileTweakStatusesInBackground}) reads the real phenotype-DB state for
     * each key and repaints the icon (and button label) from DB-truth rather than the stored
     * boolean alone. This fixes status icons resetting to red on a plain app restart while the
     * flags are still applied. Keyed by tweak key so a key is only registered once.
     */
    private final java.util.LinkedHashMap<String, ReconcileTarget> reconcileTargets =
            new java.util.LinkedHashMap<String, ReconcileTarget>();

    /** Set in {@link #onDestroy} so a late background reconcile result is dropped. */
    private volatile boolean activityDestroyed;

    /**
     * Single per-tweak state store (enabled / reboot-pending markers), initialized in
     * {@link #onCreate}. Shared by the reconcile pass, revert and apply paths so we don't
     * re-allocate a thin prefs wrapper on every call.
     */
    private TweakStateStore tweakStateStore;

    /**
     * One UI target for a flag-mapped tweak: the status icon plus, optionally, the toggle
     * button and the labels to show for the applied/disabled states. Dynamic-value tweaks
     * (HUN / bitrate / patched-apps) register with {@code button == null} so only the icon is
     * repainted — their button text reflects seekbar state and must not be clobbered here.
     */
    private static final class ReconcileTarget {
        final ImageView statusView;
        final Button button;
        final String enabledLabel;   // label to show when applied / reboot-pending
        final String disabledLabel;  // label to show when disabled

        ReconcileTarget(ImageView statusView, Button button,
                        String enabledLabel, String disabledLabel) {
            this.statusView = statusView;
            this.button = button;
            this.enabledLabel = enabledLabel;
            this.disabledLabel = disabledLabel;
        }
    }

    /**
     * Registers a flag-mapped tweak's status view (and optional toggle button labels) so the
     * post-root background pass can repaint it from DB-truth. Safe to call with a null view
     * (e.g. a commented-out tweak) — it is simply skipped.
     */
    private void registerReconcileTarget(String key, ImageView statusView, Button button,
                                         String enabledLabel, String disabledLabel) {
        if (key == null || statusView == null) return;
        reconcileTargets.put(key, new ReconcileTarget(statusView, button, enabledLabel, disabledLabel));
    }

    /** Convenience overload for icon-only targets (dynamic-value tweaks). */
    private void registerReconcileTarget(String key, ImageView statusView) {
        registerReconcileTarget(key, statusView, null, null, null);
    }

    /**
     * After root is available, reconcile every registered flag-mapped tweak's status icon (and
     * button label) against the real phenotype-DB state on ONE background thread.
     *
     * <p>The synchronous {@code load(key)}-based paint in {@link #onCreate} already drew an
     * optimistic icon so the UI is never blank; this pass corrects it from DB-truth. For each
     * key it reads {@code appliedInDb} via {@link TweakAppliedChecker} (blocking root IPC — off
     * the main thread only) and feeds it to {@link TweakReconciler#reconcile}, which also heals a
     * lost enabled-boolean when the DB confirms the flags are live. The resolved status (and the
     * matching button label) is then posted back to the UI thread.
     *
     * <p>Root gating: this only does real DB reads when root is available. If root is not (yet)
     * available {@link TweakAppliedChecker#appliedState} returns {@code null} (UNKNOWN), and the
     * resolver's optimistic-null path preserves today's behaviour (no false reds). We still run
     * the pass in that case because reconcile is a no-op write-wise on null.
     *
     * <p>Possible follow-up: the per-key probes open/scan the DB independently; a batch
     * "applied-state for many keys in one DB pass" API on {@link TweakAppliedChecker} /
     * {@link PhixitEngine} would cut redundant work. Not done here to keep this change focused.
     */
    private void reconcileTweakStatusesInBackground() {
        if (reconcileTargets.isEmpty()) return;
        // Snapshot the view targets and build the pure coordinator's label-only targets from them.
        final java.util.LinkedHashMap<String, ReconcileTarget> viewTargets =
                new java.util.LinkedHashMap<String, ReconcileTarget>(reconcileTargets);
        final java.util.LinkedHashMap<String, TweakReconcileCoordinator.Target> coordTargets =
                new java.util.LinkedHashMap<String, TweakReconcileCoordinator.Target>();
        for (Map.Entry<String, ReconcileTarget> e : viewTargets.entrySet()) {
            coordTargets.put(e.getKey(),
                    new TweakReconcileCoordinator.Target(e.getValue().enabledLabel,
                            e.getValue().disabledLabel));
        }

        final TweakAppliedChecker checker = new TweakAppliedChecker(getApplicationContext());
        // POSSIBLE FOLLOW-UPS (deferred, intentionally not implemented here):
        //   (a) a batch applied-state API on TweakAppliedChecker/PhixitEngine that reads the DB
        //       once for many keys, instead of the per-key probe scanning it independently; and
        //   (b) guarding against overlapping reconcile threads if the activity is recreated
        //       rapidly (each onCreate currently spawns its own pass).
        final TweakReconcileCoordinator coordinator = new TweakReconcileCoordinator(
                coordTargets, checker::appliedState, tweakStateStore);

        new Thread() {
            @Override
            public void run() {
                coordinator.run(new TweakReconcileCoordinator.Sink() {
                    @Override
                    public void paint(final String key, final TweakStatus status,
                                      final String labelOrNull) {
                        if (activityDestroyed) return;
                        final ReconcileTarget target = viewTargets.get(key);
                        if (target == null) return;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (activityDestroyed) return;
                                changeStatus(target.statusView, status.code(), false);
                                if (target.button != null && labelOrNull != null) {
                                    target.button.setText(labelOrNull);
                                }
                            }
                        });
                    }
                });
            }
        }.start();
    }

    /**
     * Synchronously repaints every enabled-but-reboot-pending tweak's icon yellow, before the
     * background reconcile runs. The per-tweak {@code load(key)} paint in {@link #onCreate} only
     * knows green/red, so a freshly-applied (reboot-pending) tweak would briefly show green and
     * then be flipped to yellow by the reconcile pass — this removes that flicker by drawing
     * yellow up front (the resolver's precedence is the same: enabled + reboot-pending → yellow).
     */
    private void paintOptimisticRebootPending() {
        for (Map.Entry<String, ReconcileTarget> e : reconcileTargets.entrySet()) {
            String key = e.getKey();
            if (tweakStateStore.isEnabled(key) && tweakStateStore.isRebootPending(key)) {
                changeStatus(e.getValue().statusView, TweakStatus.REBOOT_PENDING.code(), false);
            }
        }
    }

    /**
     * The set of flag-mapped tweak keys registered for the post-root reconcile pass. Exposed
     * package-private so {@code MainActivityReconcileRegistrationTest} can assert every LIVE
     * flag-mapped tweak is registered (guarding against forgetting to wire up a new tweak).
     */
    java.util.Set<String> registeredReconcileKeys() {
        return new java.util.LinkedHashSet<String>(reconcileTargets.keySet());
    }

    /** Injected into {@link RebootFabController} where no animation is wanted. */
    private static final Runnable NO_OP = new Runnable() {
        @Override
        public void run() {
            // Intentionally empty: the reboot FAB appears with no animation/glow.
        }
    };
    private Button nospeed;
    private Button taplimitat;
    private Button coolwalkDayNightTweak;
    private Button patchapps;
    private Button messageAutoReadTweak;
    private Button batteryoutline;
    private Button forceNoWideScreen;
    private Button forceWideScreenButton;
    private Button forcePortrait;
    private Button messagesHunThrottling;
    private Button mediathrottlingbutton;
    private Button intertialScrollButton;
    private Button bluetoothoff;
    private Button mdbutton;
    private Button batteryWarning;
    private Button verticalBarTweakButton;
    private Button disableTelemetryButton;
    private Button tweakUSBBitrateButton;
    private Button tweakWiFiBitrateButton;
    private Button newSeekbarTweakButton;
    private Button coolwalkTweak;
    private Button nocoolwalkTweak;
    private Button deleteCarMode;
    private Button assistantTipsButton;
    private Button declineSmsTweak;
    private Button uxprototypeButton;
    private Button materialYouButton;
    // Owns the reboot FAB's views + visibility state and is the single place that
    // applies the RebootFabVisibility policy (VISIBLE/GONE + entrance animation +
    // glow). Both the page-change listener and showRebootButton() delegate to it.
    // UI-thread-confined (mutated only via runOnUiThread).
    private RebootFabController rebootFabController;
    // Reboot-pending state restored from a previous activity instance (e.g. after
    // rotation), read in onCreate when constructing the controller. Set by
    // onRestoreInstanceState; defaults false on a fresh launch.
    private boolean restoredRebootRevealed;
    // Key under which the reboot-pending flag is persisted across recreate.
    private static final String STATE_REBOOT_REVEALED = "reboot_revealed";
    // Index of the Logs page in the ViewPager; set where the pages are inserted
    // so it can't silently drift out of sync with the adapter order.
    private int logsPageIndex;
    private boolean  urlprototype;


    ProgressDialog progress;

    SharedPreferences accountsPrefs;
    private URL url;



    public static Context getContext() {
        return mContext;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Persist whether a reboot is pending so the FAB survives a recreate.
        // currentPage is intentionally not persisted: the ViewPager restores its
        // own position and re-seeds the controller via getCurrentItem() in onCreate.
        if (rebootFabController != null) {
            outState.putBoolean(STATE_REBOOT_REVEALED, rebootFabController.isRebootRevealed());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Single shared state store for enabled / reboot-pending markers (used by the reconcile
        // pass, revert and apply paths). App context so it doesn't pin this Activity.
        tweakStateStore = new TweakStateStore(getApplicationContext());

        // Recover a reboot that was pending before this activity was recreated
        // (e.g. on rotation) so the FAB can be re-shown on the Tweaks page.
        if (savedInstanceState != null) {
            restoredRebootRevealed = savedInstanceState.getBoolean(STATE_REBOOT_REVEALED, false);
        }


        Bundle extras = new Bundle()    ;

        try {
            extras = getIntent().getExtras();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }


        final String path = getApplicationInfo().dataDir;
        appDirectory = path;
        loadStatus(path);



        if (extras != null && extras.getString("NewVersionName") != null) {

            BottomDialog bd;

            final BottomDialog builder2 = new BottomDialog.Builder(this)
                    .setTitle(R.string.new_version_available)
                    .setContent(getString(R.string.go_to_new_version, extras.getString("NewVersionName")))
                    .setPositiveBackgroundColor(R.color.brand_blue)
                    .setPositiveText(R.string.go_to_download)
                    .setNegativeText(R.string.ignore_for_now)
                    .onPositive(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xiddoc/AndroidAutoX/releases/")));
                        }
                    })
                    .onNegative(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {

                        }
                    })
                    .setBackgroundColor(R.color.centercolor).build();

            builder2.show();
        }









        setContentView(R.layout.activity_main);

        // (Re)schedule the background re-apply job to match what's currently enabled.
        ReapplyScheduler.sync(getApplicationContext());

        ImageView revertNotificationDuration = findViewById(R.id.revert_hun_throttling);
        ImageView revertMediaNotificationDuration = findViewById(R.id.revert_media_hun);
        ImageView revertWifiBitrate = findViewById(R.id.revert_bitrate_wifi);
        ImageView revertUsbBitrate = findViewById(R.id.revert_bitrate_usb);


        ViewPager viewPager = findViewById(R.id.viewpager);
        CommonPageAdapter adapter = new CommonPageAdapter();
        adapter.insertViewId(R.id.page_one, getString(R.string.tab_tweaks));
        // Derive the Logs page index from insertion order so it can't drift.
        logsPageIndex = adapter.insertViewId(R.id.page_two, getString(R.string.tab_logs));
        viewPager.setAdapter(adapter);

        com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        TextView versionLabel = findViewById(R.id.version_label);
        versionLabel.setText("v" + BuildConfig.VERSION_NAME);

        final View infoCard = findViewById(R.id.info_card);
        if (load("info_card_dismissed")) {
            infoCard.setVisibility(View.GONE);
        }
        findViewById(R.id.info_card_dismiss).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                infoCard.setVisibility(View.GONE);
                save(true, "info_card_dismissed");
            }
        });

        findViewById(R.id.copy_logs_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                TextView tv = findViewById(R.id.logs);
                ClipData clip = ClipData.newPlainText("logs", tv.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getApplicationContext(), getString(R.string.log_copied), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.clear_logs_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView tv = findViewById(R.id.logs);
                tv.setText(getString(R.string.log_first_line));
                Toast.makeText(getApplicationContext(), getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show();
            }
        });

        Button toapp = findViewById(R.id.toapp_button);
        toapp.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MainActivity.this, AppsList.class);
                        startActivity(intent);
                    }
                }
        );

        Button rebootbutton = findViewById(R.id.reboot_button);
        final DialogFragment rebootDialog = new RebootDialog();
        rebootbutton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        rebootDialog.show(getSupportFragmentManager(), "RebootDialog");
                    }
                }
        );

        rebootButton = findViewById(R.id.reboot_button);
        rebootContainer = findViewById(R.id.reboot_container);
        applyAzureGlow(rebootButton);

        // Build the FAB controller now that its views exist. It owns the
        // VISIBLE/GONE apply path (shown once a tweak is applied, hidden on the
        // Logs page). The entrance animation and glow are injected as Runnables;
        // they are no-ops here — the FAB simply appears with no animation or
        // colour-glow overlay. Initialise its page state from the pager's current
        // item so a restored Logs page is honoured immediately
        // (addOnPageChangeListener does NOT fire for the initial/restored page).
        final View rebootFabRoot = RebootFabController.resolveFabRoot(rebootContainer, rebootButton);
        rebootFabController = new RebootFabController(
                rebootFabRoot,
                NO_OP,
                NO_OP,
                logsPageIndex,
                viewPager.getCurrentItem());

        // Restore a reboot that was pending before an activity recreate (e.g.
        // rotation), then reconcile FAB visibility against the restored page.
        rebootFabController.restoreRevealed(restoredRebootRevealed);

        // The reboot FAB floats over the whole activity; on the Logs page it would
        // overlap the Copy Logs button. Register the page-change listener here —
        // after the FAB views exist — so the controller is never touched before
        // it is wired. It owns the shared RebootFabVisibility policy.
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                rebootFabController.onPageChanged(position);
            }
        });

        // Ambient AI backdrop: keep the aurora blobs slowly drifting/breathing
        // the whole time the screen is open.
        startAurora();



        // Configure the logs TextView once: enable text selection so the user
        // can long-press to copy log output.  Vertical scrolling is handled by
        // the parent ScrollView (logs_scroll); horizontal scrolling is handled
        // by the nested HorizontalScrollView (logs_hscroll).  We call this once
        // here so it does NOT run on every button tap (repeated calls to
        // setTextIsSelectable can reset focus state).
        configureLogsView();
        TextView logs = getLogsView();

        // Dev-only: read-only diagnostic of the new "phixit" Phenotype snapshot. Hidden
        // behind developer mode (enable by tapping the About text 7x) so it doesn't run /
        // spam the log on every launch for normal users.
        if (isDevMode(this)) {
            phixitDiagnostic(logs);
        }


/*        nospeed = findViewById(R.id.nospeed);
        noSpeedRestrictionsStatus = findViewById(R.id.speedhackstatus);
        if (load("aa_speed_hack")) {
            nospeed.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.unlimited_scrolling_when_driving));
            changeStatus(noSpeedRestrictionsStatus, 2, false);
        } else {
            nospeed.setText(getString(R.string.disable_tweak_string) + getString(R.string.unlimited_scrolling_when_driving));
            changeStatus(noSpeedRestrictionsStatus, 0, false);
        }

        nospeed.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_speed_hack")) {
                            revert("aa_speed_hack");
                            nospeed.setText(getString(R.string.disable_tweak_string) + getString(R.string.unlimited_scrolling_when_driving));
                            changeStatus(noSpeedRestrictionsStatus, 0, true);
                            showRebootButton();
                        } else {
                            patchforspeed(UserCount);
                        }
                    }
                });

        setOnLongClickListener(nospeed, R.string.tutorial_nospeed, R.drawable.tutorial_nospeed);*/



        taplimitat = findViewById(R.id.taplimit);
        taplimitstatus = findViewById(R.id.sixtapstatus);
        if (load("aa_six_tap")) {
            taplimitat.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.disable_speed_limitations));
            changeStatus(taplimitstatus, 2, false);

        } else {
            taplimitat.setText(getString(R.string.disable_tweak_string) + getString(R.string.disable_speed_limitations));
            changeStatus(taplimitstatus, 0, false);

        }
        registerReconcileTarget("aa_six_tap", taplimitstatus, taplimitat,
                getString(R.string.re_enable_tweak_string) + getString(R.string.disable_speed_limitations),
                getString(R.string.disable_tweak_string) + getString(R.string.disable_speed_limitations));

        setOnLongClickListener(taplimitat, R.string.tutorial_sixtap, R.drawable.tutorial_sixtap);


        taplimitat.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_six_tap")) {
                            revert("aa_six_tap");
                            taplimitat.setText(getString(R.string.disable_tweak_string) + getString(R.string.disable_speed_limitations));
                            changeStatus(taplimitstatus, 0, true);
                            showRebootButton();
                        } else {
                            patchfortouchlimit();
                        }
                    }
                });

        coolwalkDayNightTweak = findViewById(R.id.coolwalkdaynighttweak);
        navstatus = findViewById(R.id.coolwalkdaynightstatus);
        if (load("coolwalk_daynight_tweak")) {
            coolwalkDayNightTweak.setText(getString(R.string.disable_tweak_string) + getString(R.string.coolwalk_daynight_tweak));
            changeStatus(navstatus, 2, false);
        } else {
            coolwalkDayNightTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_daynight_tweak));
            changeStatus(navstatus, 0, false);
        }
        registerReconcileTarget("coolwalk_daynight_tweak", navstatus, coolwalkDayNightTweak,
                getString(R.string.disable_tweak_string) + getString(R.string.coolwalk_daynight_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_daynight_tweak));
        coolwalkDayNightTweak.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("coolwalk_daynight_tweak")) {
                            revert("coolwalk_daynight_tweak");
                            coolwalkDayNightTweak.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.coolwalk_daynight_tweak));
                            changeStatus(navstatus, 0, true);
                            showRebootButton();
                        } else {
                            coolwalkdaynightpatch();
                        }
                    }
                });

        setOnLongClickListener(coolwalkDayNightTweak, R.string.coolwalk_daynight_tutorial, R.drawable.tutorial_coolwalkdaynight);

        patchapps = findViewById(R.id.patchapps);
        patchappstatus = findViewById(R.id.patchedappstatus);


        if (load("aa_patched_apps")) {
            patchapps.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.patch_custom_apps));
            changeStatus(patchappstatus, 2, false);
        } else {
            patchapps.setText(getString(R.string.patch_app) + getString(R.string.patch_custom_apps));
            changeStatus(patchappstatus, 0, false);
        }
        // Icon-only: the patched-apps button text depends on the chosen whitelist, so the
        // background pass repaints the status icon but leaves the button label alone.
        registerReconcileTarget("aa_patched_apps", patchappstatus);

        patchapps.setOnClickListener(
                new View.OnClickListener() {


                    @Override
                    public void onClick(View view) {
                        if (load("aa_patched_apps")) {
                            revert("aa_patched_apps");
                            patchapps.setText(getString(R.string.patch_app) + getString(R.string.patch_custom_apps));
                            changeStatus(patchappstatus, 0, true);
                            showRebootButton();
                        } else {
                            SharedPreferences appsListPref = getApplicationContext().getSharedPreferences("appsListPref", 0);
                            Map<String, ?> allEntries = appsListPref.getAll();
                            if (allEntries.isEmpty()) {
                                Intent intent = new Intent(MainActivity.this, AppsList.class);
                                startActivity(intent);
                                Toast.makeText(getApplicationContext(), getString(R.string.choose_apps_warning), Toast.LENGTH_LONG).show();
                            } else{
                                temp = true;
                                final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                                builder.setTitle(getString(R.string.warning_title));
                                builder.setMessage(getResources().getString(R.string.warning_patch_apps));
                                builder.setNeutralButton( getString(android.R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                temp = false;
                                                patchforapps();

                                            }
                                        });
                                builder.setNegativeButton( android.R.string.no
                                        ,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        });
                                builder.setCancelable(false);
                                builder.show();

                            }
                        }
                    }
                });

        setOnLongClickListener(patchapps, R.string.tutorial_patchapps);


        messageAutoReadTweak = findViewById(R.id.message_autoread_tweak_button);
        messageAutoReadStatus = findViewById(R.id.message_autoread_tweak_status);
        if (load("aa_message_autoread")) {
            messageAutoReadTweak.setText(getString(R.string.disable_tweak_string) + getString(R.string.message_auto_read));
            changeStatus(messageAutoReadStatus, 2, false);

        } else {
            messageAutoReadTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.message_auto_read));
            changeStatus(messageAutoReadStatus, 0, false);
        }
        registerReconcileTarget("aa_message_autoread", messageAutoReadStatus, messageAutoReadTweak,
                getString(R.string.disable_tweak_string) + getString(R.string.message_auto_read),
                getString(R.string.enable_tweak_string) + getString(R.string.message_auto_read));

        messageAutoReadTweak.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_message_autoread")) {
                            revert("aa_message_autoread");
                            messageAutoReadTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.message_auto_read));
                            changeStatus(messageAutoReadStatus, 0, true);
                            showRebootButton();
                        } else {
                            messageAutoRead();
                        }
                    }
                });

        setOnLongClickListener(messageAutoReadTweak, R.string.tutorial_autoplay_message);


        uxprototypeButton = findViewById(R.id.uxprototypetweak);
        uxprototypeTweakStatus = findViewById(R.id.uxptototypestatus);
        if (load("uxprototype_tweak")) {
            uxprototypeButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.uxprototype_tweak));
            changeStatus(uxprototypeTweakStatus, 2, false);

        } else {
            uxprototypeButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.uxprototype_tweak));
            changeStatus(uxprototypeTweakStatus, 0, false);
        }
        registerReconcileTarget("uxprototype_tweak", uxprototypeTweakStatus, uxprototypeButton,
                getString(R.string.disable_tweak_string) + getString(R.string.uxprototype_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.uxprototype_tweak));

        uxprototypeButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {


                        if (load("uxprototype_tweak")) {
                            revert("uxprototype_tweak");
                            uxprototypeButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.uxprototype_tweak));
                            changeStatus(uxprototypeTweakStatus, 0, true);
                            showRebootButton();
                        } else {
                            final Dialog uxprototypeDialog;
                            uxprototypeDialog = new Dialog(MainActivity.this);
                            uxprototypeDialog.setContentView(R.layout.dialog_layout);
                            uxprototypeDialog.setCancelable(false);


                            WindowManager.LayoutParams lp = setDialogLayoutParams(uxprototypeDialog);

                            final EditText readURL = uxprototypeDialog.findViewById(R.id.textuxprototype);
                            readURL.setVisibility(View.VISIBLE);

                            TextView acceptButton =  uxprototypeDialog.findViewById(R.id.yes);
                            TextView cancelButton =  uxprototypeDialog.findViewById(R.id.no);

                            acceptButton.setVisibility(View.VISIBLE);
                            cancelButton.setVisibility(View.VISIBLE);
                            acceptButton.setOnClickListener(new View.OnClickListener() {

                                public void onClick(View v) {

                                        String url = readURL.getText().toString();
                                        if (!url.contains("http://") && !url.contains("https://")) {
                                           url =  "http://" + url;
                                        }


                                    try {
                                        uxprototypeTweak(new URL(readURL.getText().toString()));
                                        uxprototypeDialog.dismiss();
                                    } catch (MalformedURLException e) {
                                        e.printStackTrace();
                                    }

                                    if (uxprototypeDialog.isShowing()) {
                                        Toast.makeText(uxprototypeDialog.getContext(), R.string.uxprototype_dialog, Toast.LENGTH_LONG).show();
                                    }


                                }
                            });
                            cancelButton.setOnClickListener(new View.OnClickListener() {

                                public void onClick(View v) {
                                    uxprototypeDialog.dismiss();
                                }
                            });
                            uxprototypeDialog.show();
                            // Make the window backdrop transparent so the rounded card corners aren't framed by a gray rectangle.
                            uxprototypeDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                            uxprototypeDialog.getWindow().setAttributes(lp);

                        }
                    }


                });


        setOnLongClickListener(uxprototypeButton, R.string.uxprototype_tutorial);


        materialYouButton = findViewById(R.id.materialyoutweak);
        materialYouTweakStatus = findViewById(R.id.materialyoutweakstatus);
        if (load("aa_material_you")) {
            materialYouButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.materialyou_tweak));
            changeStatus(materialYouTweakStatus, 2, false);

        } else {
            materialYouButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.materialyou_tweak));
            changeStatus(materialYouTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_material_you", materialYouTweakStatus, materialYouButton,
                getString(R.string.disable_tweak_string) + getString(R.string.materialyou_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.materialyou_tweak));

        materialYouButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if (load("uxprototype_tweak")) {
                            revert("uxprototype_tweak");
                            materialYouButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.materialyou_tweak));
                            changeStatus(materialYouTweakStatus, 0, true);
                            showRebootButton();
                        } else {
                            activateMaterialYou();
                        }
                    }


                });


        setOnLongClickListener(materialYouButton, R.string.tutorial_materialyou, R.drawable.tutorial_materialyou);



        batteryoutline = findViewById(R.id.battoutline);
        batteryOutlineStatus = findViewById(R.id.batterystatus);
        if (load("aa_battery_outline")) {
            batteryoutline.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.battery_outline_string));
            changeStatus(batteryOutlineStatus, 2, false);

        } else {
            batteryoutline.setText(getString(R.string.disable_tweak_string) + getString(R.string.battery_outline_string));
            changeStatus(batteryOutlineStatus, 0, false);
        }
        registerReconcileTarget("aa_battery_outline", batteryOutlineStatus, batteryoutline,
                getString(R.string.re_enable_tweak_string) + getString(R.string.battery_outline_string),
                getString(R.string.disable_tweak_string) + getString(R.string.battery_outline_string));

        batteryoutline.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_battery_outline")) {
                            revert("aa_battery_outline");
                            batteryoutline.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.battery_outline_string));
                            changeStatus(batteryOutlineStatus, 0, true);
                            showRebootButton();
                        } else {
                            battOutline();
                        }
                    }
                });

        setOnLongClickListener(batteryoutline, R.string.tutorial_battery_outline, R.drawable.tutorial_outline);


        forceNoWideScreen = findViewById(R.id.force__no_ws_button);
        forceNoWideScreenStatus = findViewById(R.id.force_no_ws_status);


        forceWideScreenButton = findViewById(R.id.force_ws_button);
        forceWideScreenStatus = findViewById(R.id.force_ws_status);

        forcePortrait = findViewById(R.id.force_portrait_button);
        forcePortraitStatus = findViewById(R.id.force_portrait_status);


        if (load("force_ws")) {
            forceWideScreenButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.force_widescreen_text));
            changeStatus(forceWideScreenStatus, 2, false);
        } else {
            forceWideScreenButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.force_widescreen_text));
            changeStatus(forceWideScreenStatus, 0, false);
        }

        forceWideScreenButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("force_ws")) {
                            revert("force_ws");
                            forceWideScreenButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.force_widescreen_text));
                            changeStatus(forceWideScreenStatus, 0, true);
                            showRebootButton();
                        } else {
                            forceWideScreen(view, 470);
                            forceWideScreenButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.force_widescreen_text));
                            if (load("force_no_ws") || load ("force_portrait")) {
                                Toast.makeText(getApplicationContext(), getString(R.string.force_disable_widescreen_warning), Toast.LENGTH_LONG).show();
                                revert("force_no_ws");
                                revert("force_portrait");
                                save(false, "force_no_ws");
                                save(false, "force_portrait");
                            }
                        }
                    }
                });

        setOnLongClickListener(forceWideScreenButton, R.string.tutorial_widescreen, R.drawable.tutorial_widescreen);


        if (load("force_no_ws")) {
            forceNoWideScreen.setText(getString(R.string.reset_tweak) + getString(R.string.base_no_ws));
            changeStatus(forceNoWideScreenStatus, 2, false);

        } else {
            forceNoWideScreen.setText(getString(R.string.force_disable_tweak) + getString(R.string.base_no_ws));
            changeStatus(forceNoWideScreenStatus, 0, false);
        }

        forceNoWideScreen.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("force_no_ws")) {
                            revert("force_no_ws");
                            forceNoWideScreen.setText(getString(R.string.force_disable_tweak) + getString(R.string.base_no_ws));
                            changeStatus(forceNoWideScreenStatus, 0, true);
                            showRebootButton();
                        } else {
                            forceWideScreen(view, 1920);
                            forceNoWideScreen.setText(getString(R.string.reset_tweak) + getString(R.string.base_no_ws));
                            if (load("force_portrait") || load ("force_ws")) {
                                revert("force_portrait");
                                revert("force_ws");
                                save(false, "force_portrait");
                                save(false, "force_ws");
                            }
                        }
                    }
                });

        setOnLongClickListener(forceNoWideScreen, R.string.tutorial_no_widescreen, R.drawable.tutorial_nowidescreen);

        if (load("force_portrait")) {
            forcePortrait.setText(getString(R.string.reset_tweak) + getString(R.string.portrait_layout));
            changeStatus(forcePortraitStatus, 2, false);
        } else {
            forcePortrait.setText(getString(R.string.enable_tweak_string) + getString(R.string.portrait_layout));
            changeStatus(forcePortraitStatus, 0, false);
        }

        forcePortrait.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("force_portrait")) {
                            revert("force_portrait");
                            forcePortrait.setText(getString(R.string.enable_tweak_string) + getString(R.string.portrait_layout));
                            changeStatus(forcePortraitStatus, 0, true);
                            showRebootButton();
                        } else {
                            forceWideScreen(view, 10);
                            forcePortrait.setText(getString(R.string.disable_tweak_string) + getString(R.string.portrait_layout));
                            if (load("force_no_ws") || load ("force_ws")) {
                                Toast.makeText(getApplicationContext(), getString(R.string.force_disable_widescreen_warning), Toast.LENGTH_LONG).show();
                                revert("force_no_ws");
                                revert("force_ws");
                                save(false, "force_no_ws");
                                save(false, "force_ws");
                            }
                        }
                    }
                });

        setOnLongClickListener(forcePortrait, R.string.tutorial_portrait, R.string.restricted_coolwalk, R.drawable.tutorial_portrait);

        messagesHunThrottling = findViewById(R.id.hunthrottlingbutton);
        final int[] messagesHunScrollbarValue = {0};
        final TextView displayValue = findViewById(R.id.seekbar_text);
        final SeekBar hunSeekbar = findViewById(R.id.messages_hun_seekbar);
        hunSeekbar.setProgress(8000);
        displayValue.setText(hunSeekbar.getProgress() + "ms");
        hunSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = ((int) Math.round(progress / 100)) * 100;
                seekBar.setProgress(progress);
                messagesHunScrollbarValue[0] = hunSeekbar.getProgress();
                displayValue.setText(hunSeekbar.getProgress() + "ms");
                if (hunSeekbar.getProgress() == 8000) {
                    messagesHunThrottling.setText(getString(R.string.reset_tweak) + getString(R.string.set_notification_duration_to) + getString(R.string.default_string));
                } else {
                    messagesHunThrottling.setText(getString(R.string.set_value) + getString(R.string.set_notification_duration_to) + " " + hunSeekbar.getProgress() + " ms");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                displayValue.setText(hunSeekbar.getProgress() + "ms");
                messagesHunThrottling.setText(getString(R.string.set_value) + getString(R.string.set_notification_duration_to) + " " + hunSeekbar.getProgress() + " ms");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                messagesHunScrollbarValue[0] = hunSeekbar.getProgress();
                displayValue.setText(hunSeekbar.getProgress() + "ms");
                if (hunSeekbar.getProgress() == 8000) {
                    messagesHunThrottling.setText(getString(R.string.reset_tweak) + getString(R.string.set_notification_duration_to) + getString(R.string.default_string));
                } else {
                    messagesHunThrottling.setText(getString(R.string.set_value) + getString(R.string.set_notification_duration_to) + " " + hunSeekbar.getProgress() + " ms");
                }
            }
        });

        revertNotificationDuration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hunSeekbar.setProgress(8000);
            }
        });


        messagesHunStatus = findViewById(R.id.huntrottlingstatus);

        currentlySetHun = findViewById(R.id.notification_currently_set);
        if (load("aa_hun_ms")) {
            messagesHunThrottling.setText(getString(R.string.reset_tweak) + getString(R.string.set_notification_duration_to) + getString(R.string.default_string));
            changeStatus(messagesHunStatus, 2, false);
            if (loadValue("messaging_hun_value") == 0) {
                saveValue((int) readPhixitLong(FlagSpec.PKG_GEARHEAD,
                        "SystemUi__hun_default_heads_up_timeout_ms", 0), "messaging_hun_value");
            }
            currentlySetHun.setText(getString(R.string.currently_set) + loadValue("messaging_hun_value"));
        } else {
            messagesHunThrottling.setText(getString(R.string.set_value) + getString(R.string.set_notification_duration_to) + "...");
            changeStatus(messagesHunStatus, 0, false);
        }
        // Icon-only: dynamic-value tweak; button text reflects the seekbar, not applied state.
        registerReconcileTarget("aa_hun_ms", messagesHunStatus);



        messagesHunThrottling.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (hunSeekbar.getProgress() == 8000) {
                            if (load("aa_hun_ms")) {
                                revert("aa_hun_ms");
                                saveValue(0, "messaging_hun_value");
                                changeStatus(messagesHunStatus, 0, true);
                                currentlySetHun.setText("");
                                showRebootButton();
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.choose_value_first), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            setHunDuration(view, hunSeekbar.getProgress());
                        }
                    }
                });

        setOnLongClickListener(messagesHunThrottling, R.string.tutorial_hun, R.drawable.tutorial_hun);

        mediathrottlingbutton = findViewById(R.id.media_throttling_button);
        final int[] secondScrollBarStatus = {0};
        final TextView secondDisplayValue = findViewById(R.id.second_seekbar_text);
        final SeekBar mediaSeekbar = findViewById(R.id.media_hun_seekbar);
        mediaSeekbar.setProgress(8000);
        secondDisplayValue.setText(mediaSeekbar.getProgress() + "ms");
        mediaSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = ((int) Math.round(progress / 1000)) * 1000;
                mediaSeekbar.setProgress(progress);
                secondDisplayValue.setText(mediaSeekbar.getProgress() + "ms");
                if (mediaSeekbar.getProgress() == 8000) {
                    mediathrottlingbutton.setText(getString(R.string.reset_tweak) + getString(R.string.media_notification_duration_to) + getString(R.string.default_string));
                } else {
                    mediathrottlingbutton.setText(getString(R.string.set_value) + getString(R.string.media_notification_duration_to) + " " + mediaSeekbar.getProgress() + " ms");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                secondDisplayValue.setText(mediaSeekbar.getProgress() + "ms");
                mediathrottlingbutton.setText(getString(R.string.set_value) + getString(R.string.media_notification_duration_to) + " " + mediaSeekbar.getProgress() + " ms");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                secondScrollBarStatus[0] = mediaSeekbar.getProgress();
                secondDisplayValue.setText(mediaSeekbar.getProgress() + "ms");
                if (mediaSeekbar.getProgress() == 8000) {
                    mediathrottlingbutton.setText(getString(R.string.reset_tweak) + getString(R.string.media_notification_duration_to) + getString(R.string.default_string));
                } else {
                    mediathrottlingbutton.setText(getString(R.string.set_value) + getString(R.string.media_notification_duration_to) + " " + mediaSeekbar.getProgress() + " ms");
                }
            }
        });

        revertMediaNotificationDuration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaSeekbar.setProgress(8000);
            }
        });

        currentlySetMediaHun = findViewById(R.id.media_notification_currently_set);
        mediaHunStatus = findViewById(R.id.media_trhrottling_status);
        if (load("aa_media_hun")) {
            mediathrottlingbutton.setText(getString(R.string.reset_tweak) + getString(R.string.media_notification_duration_to) + getString(R.string.default_string));
            changeStatus(mediaHunStatus, 2, false);
            if (loadValue("media_hun_value") == 0) {
                saveValue((int) readPhixitLong(FlagSpec.PKG_GEARHEAD,
                        "SystemUi__media_hun_in_rail_widget_timeout_ms", 0), "media_hun_value");
            }
            currentlySetMediaHun.setText(getString(R.string.currently_set) + loadValue("media_hun_value"));
        } else {
            mediathrottlingbutton.setText(getString(R.string.set_value) + getString(R.string.media_notification_duration_to) + "...");
            changeStatus(mediaHunStatus, 0, false);
        }
        // Icon-only: dynamic-value tweak; button text reflects the seekbar, not applied state.
        registerReconcileTarget("aa_media_hun", mediaHunStatus);

        mediathrottlingbutton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_media_hun")) {
                            if (mediaSeekbar.getProgress() == 8000) {
                                revert("aa_media_hun");
                                saveValue(0, "media_hun_value");
                                changeStatus(mediaHunStatus, 0, true);
                                currentlySetMediaHun.setText("");
                            } else {
                                setMediaHunDuration(view, mediaSeekbar.getProgress());
                            }
                            showRebootButton();
                        } else {
                            setMediaHunDuration(view, mediaSeekbar.getProgress());
                        }
                    }
                });

        setOnLongClickListener(mediathrottlingbutton, R.string.tutorial_media_hun, R.drawable.tutorial_media_hun);

        intertialScrollButton = findViewById(R.id.inertial_scroll_button);



        /*final int[] calendarSeekbarStatus = {0};
        final TextView calendarSeekbarTextView = findViewById(R.id.calendar_days_seekbar_text);
        final SeekBar calendarSeekbar = findViewById(R.id.calendar_days_seekbar);
        calendarSeekbar.setProgress(1);
        calendarSeekbarTextView.setText("1");
        calendarSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                calendarSeekbar.setProgress(progress);
                calendarSeekbarTextView.setText(calendarSeekbar.getProgress() + "");
                if (progress == 1 || progress == 0) {
                    moreCalendarButton.setText(getString(R.string.calendar_tweak_single, calendarSeekbar.getProgress()));
                } else {
                    moreCalendarButton.setText(getString(R.string.calendar_tweak, calendarSeekbar.getProgress()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                calendarSeekbarTextView.setText(calendarSeekbar.getProgress() + "");
                moreCalendarButton.setText(getString(R.string.calendar_tweak, calendarSeekbar.getProgress()));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                calendarSeekbarStatus[0] = calendarSeekbar.getProgress();
                calendarSeekbarTextView.setText(calendarSeekbar.getProgress() + "");
            }
        });

        revertCalendarDays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                calendarSeekbar.setProgress(1);
            }
        });*/


        intertialScrollStatus = findViewById(R.id.inertial_scroll_status);

        if (load("aa_inertial_scroll")) {
            changeStatus(intertialScrollStatus, 2, false);
            intertialScrollButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.inertial_scroll_tweak));
        } else {
            intertialScrollButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.inertial_scroll_tweak));
            changeStatus(intertialScrollStatus, 0, false);
        }
        registerReconcileTarget("aa_inertial_scroll", intertialScrollStatus, intertialScrollButton,
                getString(R.string.enable_tweak_string) + getString(R.string.inertial_scroll_tweak),
                getString(R.string.disable_tweak_string) + getString(R.string.inertial_scroll_tweak));

        intertialScrollButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                            if (load("aa_inertial_scroll")) {
                                revert("aa_inertial_scroll");
                                changeStatus(intertialScrollStatus, 0, true);
                                showRebootButton();
                            } else {
                            inertialScrollTweak();
                        }
                    }
                });

        setOnLongClickListener(intertialScrollButton, R.string.tutorial_inertial_scroll);

        bluetoothoff = findViewById(R.id.bluetooth_disable_button);
        btstatus = findViewById(R.id.bt_disable_status);
        if (load("bluetooth_pairing_off")) {
            bluetoothoff.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.bluetooth_auto_connect));
            changeStatus(btstatus, 2, false);
        } else {
            bluetoothoff.setText(getString(R.string.disable_tweak_string) + getString(R.string.bluetooth_auto_connect));
            changeStatus(btstatus, 0, false);

        }
        registerReconcileTarget("bluetooth_pairing_off", btstatus, bluetoothoff,
                getString(R.string.re_enable_tweak_string) + getString(R.string.bluetooth_auto_connect),
                getString(R.string.disable_tweak_string) + getString(R.string.bluetooth_auto_connect));

        verticalBarStatus = findViewById(R.id.vertical_bar_tweak_status);

        verticalBarTweakButton = findViewById(R.id.vertical_bar_tweak);
        if (load("aa_vertical_bar")) {
            verticalBarTweakButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.vertical_bar_tweak));
            changeStatus(verticalBarStatus, 2, false);
        } else {
            verticalBarTweakButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.vertical_bar_tweak));
            changeStatus(verticalBarStatus, 0, false);
        }
        registerReconcileTarget("aa_vertical_bar", verticalBarStatus, verticalBarTweakButton,
                getString(R.string.disable_tweak_string) + getString(R.string.vertical_bar_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.vertical_bar_tweak));

        verticalBarTweakButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_vertical_bar")) {
                            revert("aa_vertical_bar");
                            changeStatus(verticalBarStatus, 0, true);
                            showRebootButton();
                        } else {
                            verticalBarTweak();
                        }
                    }
                });

        setOnLongClickListener(verticalBarTweakButton, R.string.tutorial_vertical_bar_tweak);

        bluetoothoff.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("bluetooth_pairing_off")) {
                            revert("bluetooth_pairing_off");
                            bluetoothoff.setText(getString(R.string.disable_tweak_string) + getString(R.string.bluetooth_auto_connect));
                            changeStatus(btstatus, 0, true);
                            showRebootButton();
                        } else {
                            forceNoBt();
                        }
                    }
                });

        setOnLongClickListener(bluetoothoff, R.string.tutorial_bluetooth);


        mdbutton = findViewById(R.id.multi_display_button);
        mdstatus = findViewById(R.id.multi_display_status);
        if (load("multi_display")) {
            mdbutton.setText(getString(R.string.disable_tweak_string) + getString(R.string.multi_display_string));
            changeStatus(mdstatus, 2, false);
        } else {
            mdbutton.setText(getString(R.string.enable_tweak_string) + getString(R.string.multi_display_string));
            changeStatus(mdstatus, 0, false);
        }
        registerReconcileTarget("multi_display", mdstatus, mdbutton,
                getString(R.string.disable_tweak_string) + getString(R.string.multi_display_string),
                getString(R.string.enable_tweak_string) + getString(R.string.multi_display_string));

        mdbutton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("multi_display")) {
                            revert("multi_display");
                            mdbutton.setText(getString(R.string.enable_tweak_string) + getString(R.string.multi_display_string));
                            changeStatus(mdstatus, 0, true);
                            showRebootButton();
                        } else {
                            multiDisplay();
                        }
                    }
                });

        setOnLongClickListener(mdbutton, R.string.tutorial_multidisplay, R.drawable.tutorial_md1, R.drawable.tutorial_md2, R.drawable.tutorial_md3);

        batteryWarning = findViewById(R.id.battery_warning_button);
        batteryWarningStatus = findViewById(R.id.battery_warning_status);
        if (load("battery_saver_warning")) {
            batteryWarning.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.battery_warning));
            changeStatus(batteryWarningStatus, 2, false);
        } else {
            batteryWarning.setText(getString(R.string.disable_tweak_string) + getString(R.string.battery_warning));
            changeStatus(batteryWarningStatus, 0, false);
        }
        registerReconcileTarget("battery_saver_warning", batteryWarningStatus, batteryWarning,
                getString(R.string.re_enable_tweak_string) + getString(R.string.battery_warning),
                getString(R.string.disable_tweak_string) + getString(R.string.battery_warning));

        batteryWarning.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("battery_saver_warning")) {
                            revertBatteryWarning();
                        } else {
                            disableBatteryWarning();
                        }
                    }
                });

        setOnLongClickListener(batteryWarning, R.string.tutorial_battery_saver_warning, R.drawable.tutorial_battery_saver);


        disableTelemetryButton = findViewById(R.id.telemetry_disable_tweak);
        telemetryStatus = findViewById(R.id.telemetry_disable_status);
        if (load("kill_telemetry")) {
            disableTelemetryButton.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.telemetry_string));
            changeStatus(telemetryStatus, 2, false);
        } else {
            disableTelemetryButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.telemetry_string));
            changeStatus(telemetryStatus, 0, false);
        }
        registerReconcileTarget("kill_telemetry", telemetryStatus, disableTelemetryButton,
                getString(R.string.re_enable_tweak_string) + getString(R.string.telemetry_string),
                getString(R.string.disable_tweak_string) + getString(R.string.telemetry_string));

        disableTelemetryButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("kill_telemetry")) {
                            revert("kill_telemetry");
                            disableTelemetryButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.telemetry_string));
                            changeStatus(telemetryStatus, 0, true);
                            showRebootButton();
                        } else {
                            disableTelemetry();

                        }
                    }
                });

        setOnLongClickListener(disableTelemetryButton, R.string.tutorial_telemetry);

        tweakUSBBitrateButton = findViewById(R.id.tweak_bitrate_usb);
        final int[] usbBitrateValue = {0};
        final TextView currentSeekbarUSB = findViewById(R.id.usb_bitrate_currently_set);
        final TextView toBeSetSeekbarUSB = findViewById(R.id.usb_bitrate_to_be_set);
        final SeekBar usbBitrateSeekbar = findViewById(R.id.usb_bitrate_seekbar);
        final Double[] valueUSB = new Double[1];
        usbBitrateSeekbar.setProgress(10);
        toBeSetSeekbarUSB.setText("1.0" + "X");
        usbBitrateSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueUSB[0] = (Double.valueOf(progress) / 10.0);
                toBeSetSeekbarUSB.setText(valueUSB[0] + "X");
                if (usbBitrateSeekbar.getProgress() == 10) {
                    tweakUSBBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_usb_bitrate) + " " + getString(R.string.default_string));
                    toBeSetSeekbarUSB.setText(valueUSB[0] + "X");
                } else {
                    tweakUSBBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_usb_bitrate) + " " + valueUSB[0] + " X");
                    toBeSetSeekbarUSB.setText(valueUSB[0] + "X");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (usbBitrateSeekbar.getProgress() == 10) {
                    tweakUSBBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_usb_bitrate) + " " + getString(R.string.default_string));
                    toBeSetSeekbarUSB.setText(valueUSB[0] + "X");
                } else {
                    tweakUSBBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_usb_bitrate) + " " + valueUSB[0] + " X");
                    toBeSetSeekbarUSB.setText(valueUSB[0] + "X");
                }
            }
        });

        revertUsbBitrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbBitrateSeekbar.setProgress(10);
            }
        });


        usbBitrateStatus = findViewById(R.id.tweak_bitrate_usb_status);

        currentlySetUSBSeekbar = findViewById(R.id.usb_bitrate_currently_set);
        if (load("aa_bitrate_usb")) {
            tweakUSBBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_usb_bitrate) + " " + getString(R.string.default_string));
            changeStatus(usbBitrateStatus, 2, false);
            currentlySetUSBSeekbar.setText(getString(R.string.currently_set) + loadFloat("usb_bitrate_value"));
        } else {
            tweakUSBBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_usb_bitrate) + "...");
            changeStatus(usbBitrateStatus, 0, false);
        }
        // Icon-only: dynamic-value tweak; button text reflects the seekbar, not applied state.
        registerReconcileTarget("aa_bitrate_usb", usbBitrateStatus);

        tweakUSBBitrateButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (usbBitrateSeekbar.getProgress() == 10) {
                            if (load("aa_bitrate_usb")) {
                                revert("aa_bitrate_usb");
                                saveFloat(0, "usb_bitrate_value");
                                changeStatus(usbBitrateStatus, 0, true);
                                currentlySetUSBSeekbar.setText("");
                                showRebootButton();
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.choose_value_first), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            setUSBbitrate(valueUSB[0]);

                        }
                    }
                });

        setOnLongClickListener(tweakUSBBitrateButton, R.string.tutorial_bitrate);




        tweakWiFiBitrateButton = findViewById(R.id.tweak_bitrate_wifi);
        final int[] wifiBitrateValue = {0};
        final TextView currentSeekbarWiFi = findViewById(R.id.wifi_bitrate_currently_set);
        final TextView toBeSetSeekbarWiFi = findViewById(R.id.wifi_bitrate_to_be_set);
        final SeekBar WiFiBitrateSeekbar = findViewById(R.id.wifi_bitrate_seekbar);
        final Double[] valueWiFi = new Double[1];
        WiFiBitrateSeekbar.setProgress(10);
        toBeSetSeekbarWiFi.setText("1.0" + "X");
        WiFiBitrateSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueWiFi[0] = (Double.valueOf(progress) / 10.0);
                toBeSetSeekbarWiFi.setText(valueWiFi[0] + "X");
                if (WiFiBitrateSeekbar.getProgress() == 10) {
                    tweakWiFiBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_wifi_tweak) + " " + getString(R.string.default_string));
                } else {
                    tweakWiFiBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_wifi_tweak) + " " + valueWiFi[0] + " X");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (WiFiBitrateSeekbar.getProgress() == 10) {
                    tweakWiFiBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_wifi_tweak) + " " + getString(R.string.default_string));
                } else {
                    tweakWiFiBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_wifi_tweak) + " " + valueWiFi[0] + " X");
                }
            }
        });

        setOnLongClickListener(tweakWiFiBitrateButton, R.string.tutorial_bitrate);

        revertWifiBitrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WiFiBitrateSeekbar.setProgress(10);
            }
        });


        wifiBitrateStatus = findViewById(R.id.tweak_bitrate_wifi_status);
        currentlySetWiFiSeekbar = findViewById(R.id.wifi_bitrate_currently_set);
        if (load("aa_bitrate_wireless")) {
            tweakWiFiBitrateButton.setText(getString(R.string.reset_tweak) + getString(R.string.set_wifi_tweak) + " " + getString(R.string.default_string));
            changeStatus(wifiBitrateStatus, 2, false);
            currentlySetWiFiSeekbar.setText(getString(R.string.currently_set) + loadFloat("wifi_bitrate_value"));
        } else {
            tweakWiFiBitrateButton.setText(getString(R.string.set_value) + getString(R.string.set_wifi_tweak) + "...");
            changeStatus(wifiBitrateStatus, 0, false);
        }
        // Icon-only: dynamic-value tweak; button text reflects the seekbar, not applied state.
        registerReconcileTarget("aa_bitrate_wireless", wifiBitrateStatus);

        tweakWiFiBitrateButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (WiFiBitrateSeekbar.getProgress() == 10) {
                            if (load("aa_bitrate_wireless")) {
                                revert("aa_bitrate_wireless");
                                saveFloat(0, "wifi_bitrate_value");
                                changeStatus(wifiBitrateStatus, 0, true);
                                currentlySetWiFiSeekbar.setText("");
                                showRebootButton();
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.choose_value_first), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            setWiFiBitrate(valueWiFi[0]);

                        }
                    }
                });



        newSeekbarTweakButton = findViewById(R.id.new_seekbar_tweak_button);
        newSeekbarTweakStatus = findViewById(R.id.newseekbar_tweak_status);


        if (load("aa_new_seekbar")) {
            newSeekbarTweakButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.tappable_progress));
            changeStatus(newSeekbarTweakStatus, 2, false);
        } else {
            newSeekbarTweakButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.tappable_progress));
            changeStatus(newSeekbarTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_new_seekbar", newSeekbarTweakStatus, newSeekbarTweakButton,
                getString(R.string.disable_tweak_string) + getString(R.string.tappable_progress),
                getString(R.string.enable_tweak_string) + getString(R.string.tappable_progress));

        newSeekbarTweakButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_new_seekbar")) {
                            revert("aa_new_seekbar");
                            newSeekbarTweakButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.tappable_progress));
                            changeStatus(newSeekbarTweakStatus, 0, true);
                            showRebootButton();
                        } else {
                            newSeekbar();
                        }
                    }
                });

        setOnLongClickListener(newSeekbarTweakButton, R.string.tutorial_new_seekbar);

        coolwalkTweak = findViewById(R.id.coolwalk_tweak_button);
        coolwalkTweakStatus = findViewById(R.id.coolwalk_tweak_status);
        nocoolwalkTweak = findViewById(R.id.nocoolwalk_tweak_button);
        nocoolwalkTweakStatus = findViewById(R.id.nocoolwalk_tweak_status);

        if (load("aa_activate_coolwalk")) {
            coolwalkTweak.setText(getString(R.string.disable_tweak_string) + getString(R.string.coolwalk_tweak));
            changeStatus(coolwalkTweakStatus, 2, false);
        } else {
            coolwalkTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_tweak));
            changeStatus(coolwalkTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_activate_coolwalk", coolwalkTweakStatus, coolwalkTweak,
                getString(R.string.disable_tweak_string) + getString(R.string.coolwalk_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_tweak));

        coolwalkTweak.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                            if (load ("aa_activate_coolwalk")) {

                                coolwalkTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_tweak));
                                changeStatus(coolwalkTweakStatus, 0, true);
                                revert("aa_activate_coolwalk");
                            } else {
                                if (load ("aa_deactivate_coolwalk")) {
                                    revert("aa_deactivate_coolwalk");
                                    nocoolwalkTweak.setText(getString(R.string.force_disable_tweak) + getString(R.string.coolwalk_tweak));
                                    changeStatus(nocoolwalkTweakStatus,0,false);
                                }
                                activateCoolwalk();
                            }

                    }
                });

        setOnLongClickListener(coolwalkTweak, R.string.tutorial_coolwalk, R.drawable.cw5, R.drawable.tutorial_coolwalk_1, R.drawable.tutorial_coolwalk_3);




        if (load("aa_deactivate_coolwalk")) {
            nocoolwalkTweak.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.coolwalk_tweak));
            changeStatus(nocoolwalkTweakStatus, 2, false);
        } else {
            nocoolwalkTweak.setText(getString(R.string.force_disable_tweak) + getString(R.string.coolwalk_tweak));
            changeStatus(nocoolwalkTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_deactivate_coolwalk", nocoolwalkTweakStatus, nocoolwalkTweak,
                getString(R.string.re_enable_tweak_string) + getString(R.string.coolwalk_tweak),
                getString(R.string.force_disable_tweak) + getString(R.string.coolwalk_tweak));

        nocoolwalkTweak.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if (load ("aa_deactivate_coolwalk")) {

                            nocoolwalkTweak.setText(getString(R.string.force_disable_tweak) + getString(R.string.coolwalk_tweak));
                            changeStatus(nocoolwalkTweakStatus, 2, true);
                            revert("aa_deactivate_coolwalk");
                        } else {
                            if (load ("aa_activate_coolwalk")) {
                                revert("aa_activate_coolwalk");
                                coolwalkTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.coolwalk_tweak));
                                changeStatus(coolwalkTweakStatus,0,false);
                            }
                            deactivateCoolwalk();
                        }

                    }
                });

        setOnLongClickListener(nocoolwalkTweak, R.string.tutorial_nocoolwalk);


        assistantTipsButton = findViewById(R.id.assistanttips_tweak_button);
        assistantTipsTweakStatus = findViewById(R.id.assistanttips_tweak_status);


        if (load("aa_activate_assistant_tips")) {
            assistantTipsButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.assistant_tips));
            changeStatus(assistantTipsTweakStatus, 2, false);
        } else {
            assistantTipsButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.assistant_tips));
            changeStatus(assistantTipsTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_activate_assistant_tips", assistantTipsTweakStatus, assistantTipsButton,
                getString(R.string.disable_tweak_string) + getString(R.string.assistant_tips),
                getString(R.string.enable_tweak_string) + getString(R.string.assistant_tips));

        assistantTipsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_activate_assistant_tips")) {
                            revert("aa_activate_assistant_tips");
                            assistantTipsButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.assistant_tips));
                            changeStatus(assistantTipsTweakStatus, 0, true);
                            showRebootButton();
                        } else {
                            activateAssistantTips();
                        }
                    }
                });

        setOnLongClickListener(assistantTipsButton, R.string.tutorial_assistant_suggestions);

        declineSmsTweak = findViewById(R.id.declinesms_tweak_button);
        declineSmsTweakStatus = findViewById(R.id.declinesms_tweak_status);


        if (load("aa_activate_declinesms")) {
            declineSmsTweak.setText(getString(R.string.disable_tweak_string) + getString(R.string.decline_message_tweak));
            changeStatus(declineSmsTweakStatus, 2, false);
        } else {
            declineSmsTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.decline_message_tweak));
            changeStatus(declineSmsTweakStatus, 0, false);
        }
        registerReconcileTarget("aa_activate_declinesms", declineSmsTweakStatus, declineSmsTweak,
                getString(R.string.disable_tweak_string) + getString(R.string.decline_message_tweak),
                getString(R.string.enable_tweak_string) + getString(R.string.decline_message_tweak));

        declineSmsTweak.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (load("aa_activate_declinesms")) {
                            revert("aa_activate_declinesms");
                            declineSmsTweak.setText(getString(R.string.enable_tweak_string) + getString(R.string.decline_message_tweak));
                            changeStatus(declineSmsTweakStatus, 0, true);
                            showRebootButton();
                        } else {
                            activatesmsdecline();
                        }
                    }
                });

        setOnLongClickListener(declineSmsTweak, R.string.tutorial_decline_message);




        deleteCarMode = findViewById(R.id.car_remover);
        deleteCarMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, CarRemover.class);
                intent.putExtra("path", path);
                startActivity(intent);
            }
        });

        deleteCarMode.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View arg0) {
                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);

                TextView tutorial = view.findViewById(R.id.dialog_content);
                tutorial.setText(getString(R.string.tutorial_carremover));

                dialog.setContentView(view);

                dialog.show();

                Window window = dialog.getWindow();
                // Make the window backdrop transparent so the rounded card corners aren't framed by a gray rectangle.
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                // Inset the card from the screen edges so it has breathing room on the sides
                // (default dialog gravity centers it, giving equal left/right gaps).
                int sideMargin = (int) (16 * getResources().getDisplayMetrics().density);
                int cardWidth = getResources().getDisplayMetrics().widthPixels - (2 * sideMargin);
                window.setLayout(cardWidth, WRAP_CONTENT);

                return true;
            }
        });

        // All status views are now looked up and optimistically painted (green/red) from the
        // stored enabled booleans. Re-paint any enabled-but-reboot-pending tweak yellow up front
        // so there is no green→yellow flicker: without this, the background reconcile below would
        // flip a freshly-applied tweak from green to yellow a moment later.
        paintOptimisticRebootPending();

        // Now correct the icons from the real phenotype-DB state on a single background thread
        // (heals lost booleans, persists yellow/reboot state correctly). Runs off the main
        // thread; if root isn't available the checks return UNKNOWN and behaviour is unchanged.
        reconcileTweakStatusesInBackground();

    }

    @Override
    protected void onDestroy() {
        // Drop any in-flight background reconcile result so we never touch a destroyed activity.
        activityDestroyed = true;
        super.onDestroy();
    }



    private void setOnLongClickListener(final Button button, final int... p) {
        button.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View arg0) {

                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);

                TextView tutorial = view.findViewById(R.id.dialog_content);
                tutorial.setText(getString(p[0]));

                ImageView img1 = view.findViewById(R.id.tutorialimage1);

                if (p.length>1) {
                    try {
                        img1.setImageDrawable(getDrawable(p[1]));
                    } catch (Exception e) {
                        tutorial.setText(getString(p[0]) + getString(p[1]));
                        e.printStackTrace();
                    }
                }

                ImageView img2 = view.findViewById(R.id.tutorialimage2);
                if (p.length>2) {
                    img2.setImageDrawable(getDrawable(p[2]));
                }

                ImageView img3 = view.findViewById(R.id.tutorialimage3);
                if (p.length>3) {
                    img3.setImageDrawable(getDrawable(p[3]));
                }

                dialog.setContentView(view);

                dialog.show();

                Window window = dialog.getWindow();
                // Make the window backdrop transparent so the rounded card corners aren't framed by a gray rectangle.
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                // Inset the card from the screen edges so it has breathing room on the sides
                // (default dialog gravity centers it, giving equal left/right gaps).
                int sideMargin = (int) (16 * getResources().getDisplayMetrics().density);
                int cardWidth = getResources().getDisplayMetrics().widthPixels - (2 * sideMargin);
                window.setLayout(cardWidth, WRAP_CONTENT);

                return true;
            }
        });
    }




    private void revert(final String toRevert) {

        final TextView logs = getLogsView();


        new Thread() {
            @Override
            public void run() {
                // The tweak is being turned off: clear any reboot-pending marker so it can't
                // linger as yellow after a revert (covers both the phixit and legacy paths below).
                tweakStateStore.setRebootPending(toRevert, false);
                // Migrated (phixit) tweaks: their flags live in the Phenotype param_partitions
                // blobs, which the legacy "DROP TRIGGER + DELETE FROM FlagOverrides" path below
                // does NOT touch. So we must always go through the phixit engine here, regardless
                // of whether a baseline was captured:
                //   - With a baseline (applied by a current build): restore the captured value,
                //     which drops appended flags back to "absent".
                //   - Without a baseline (applied by an old pre-baseline build, then reverted):
                //     explicitly REMOVE the tweak's override flags from the blobs.
                // Either way isAppliedStrict() reads FALSE afterwards, so the post-launch
                // reconcile cannot "heal" a deliberately-disabled tweak back to green.
                if (PhixitTweaks.has(toRevert)) {
                    appendText(logs, "\n\n--  Reverting (phixit): " + toRevert + "  --");
                    if (hasBaseline(toRevert)) {
                        revertPhixitSpecs(logs, PhixitTweaks.specs(toRevert));
                    } else {
                        // No baseline to restore — strip the tweak's flags outright so they no
                        // longer linger in the DB and resurrect the tweak on next launch.
                        appendText(logs, "\n\tno baseline captured; removing override flags");
                        applyPhixitSpecs(logs, removeSpecsFor(PhixitTweaks.specs(toRevert)), false);
                    }
                    save(false, toRevert);
                    return;
                }

                String path = getApplicationInfo().dataDir;

                save(false, toRevert);

                appendText(logs, "\n\n-- Reverting the hack  --");
                RootDb.exec(PHENO_DB, java.util.Arrays.asList(
                        "DROP TRIGGER IF EXISTS " + toRevert,
                        "DELETE FROM FlagOverrides"));
                appendText(logs, "\n\tdropped trigger " + toRevert + " and cleared FlagOverrides");
            }


        }.start();


    }

    private boolean hasBaseline(String key) {
        return new PhixitEngine(this, null).hasBaseline(key);
    }

    /**
     * Generic apply for a registry tweak: applies its flags via the phixit
     * engine, persists the toggle, and updates the button/status on success.
     */
    private void applyPhixitTweak(final String key, final ImageView statusView,
                                  final TextView button, final String reEnableLabel) {
        applyPhixitTweakSpecs(key, PhixitTweaks.specs(key), statusView, button, reEnableLabel);
    }

    /** As {@link #applyPhixitTweak} but with an explicit spec list (dynamic-value tweaks). */
    private void applyPhixitTweakSpecs(final String key, final java.util.List<FlagSpec> specs,
                                       final ImageView statusView, final TextView button,
                                       final String reEnableLabel) {
        final TextView logs = getLogsView();
        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.tweak_loading), true);
        new Thread() {
            @Override
            public void run() {
                appendText(logs, "\n\n--  Applying (phixit): " + key + "  --");
                final boolean ok = applyPhixitSpecs(logs, specs, true);
                if (ok) {
                    save(true, key);
                    // Flags are written but the consuming process hasn't restarted: mark the tweak
                    // as reboot-pending so it shows yellow (not green) until the device reboots,
                    // and so the state survives an app restart. Cleared on boot / on revert.
                    tweakStateStore.setRebootPending(key, true);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (ok) {
                            if (statusView != null) changeStatus(statusView, 1, true);
                            showRebootButton();
                            if (button != null && reEnableLabel != null) {
                                button.setText(getString(R.string.re_enable_tweak_string) + reEnableLabel);
                            }
                        } else {
                            DialogFragment notSuccessfulDialog = new NotSuccessfulDialog();
                            Bundle bundle = new Bundle();
                            bundle.putString("tweak", key);
                            bundle.putString("log", logs.getText().toString());
                            notSuccessfulDialog.setArguments(bundle);
                            notSuccessfulDialog.show(getSupportFragmentManager(), "NotSuccessfulDialog");
                        }
                    }
                });
            }
        }.start();
    }

    /** Developer mode flag (stored in the shared app prefs). Gates the dev/PoC menu
     *  actions and the startup diagnostic so they stay out of sight for normal users. */
    static final String DEV_MODE_KEY = "dev_mode_enabled";

    public static boolean isDevMode(Context ctx) {
        return ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .getBoolean(DEV_MODE_KEY, false);
    }

    public static void setDevMode(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(DEV_MODE_KEY, enabled).apply();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem auto = menu.findItem(R.id.auto_reapply);
        if (auto != null) {
            auto.setChecked(ReapplyScheduler.isAutoReapplyEnabled(getApplicationContext()));
        }
        MenuItem nondestructive = menu.findItem(R.id.experimental_nondestructive_patch);
        if (nondestructive != null) {
            nondestructive.setChecked(isExperimentalNonDestructivePatch(getApplicationContext()));
        }
        MenuItem autoBackup = menu.findItem(R.id.auto_backup_dbs);
        if (autoBackup != null) {
            autoBackup.setChecked(DbBackup.isEnabled(
                    getApplicationContext().getSharedPreferences(
                            PhixitEngine.PREFS, Context.MODE_PRIVATE)));
        }
        // Dev/PoC actions are hidden unless developer mode is on.
        boolean dev = isDevMode(getApplicationContext());
        MenuItem applyTest = menu.findItem(R.id.phixit_apply_test);
        if (applyTest != null) applyTest.setVisible(dev);
        MenuItem dumpAll = menu.findItem(R.id.phixit_dump_all);
        if (dumpAll != null) dumpAll.setVisible(dev);
        return super.onPrepareOptionsMenu(menu);
    }

    public WindowManager.LayoutParams setDialogLayoutParams(Dialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WRAP_CONTENT;
        return lp;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                DialogFragment aboutDialog = new AboutDialog();
                aboutDialog.show(getSupportFragmentManager(), "AboutDialog");
                break;

            case R.id.revert_everything:
                final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                builder.setMessage(getString(R.string.revert_everything_dialog))
                        .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                getAndRemoveOptionsSelected();
                            }
                        })
                        .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                builder.setCancelable(true);
                androidx.appcompat.app.AlertDialog Alert1 = builder.create();
                Alert1.show();
                break;
            case R.id.auto_reapply:
                boolean newState = !item.isChecked();
                item.setChecked(newState);
                ReapplyScheduler.setAutoReapplyEnabled(getApplicationContext(), newState);
                Toast.makeText(getApplicationContext(),
                        getString(newState ? R.string.auto_reapply_on : R.string.auto_reapply_off),
                        Toast.LENGTH_SHORT).show();
                break;

            case R.id.aa_settings:
                String packageName = "com.google.android.projection.gearhead";
                openApp(getApplicationContext(), packageName);
                break;

            case R.id.experimental_nondestructive_patch:
                // Opt-in, default-OFF: switch patchforapps() between the destructive reinstall
                // (default) and the experimental non-destructive pm set-installer path. See
                // docs/patch-apps-installer-analysis.md for the installing-vs-initiating caveat.
                boolean ndState = !item.isChecked();
                item.setChecked(ndState);
                setExperimentalNonDestructivePatch(ndState);
                Toast.makeText(getApplicationContext(),
                        getString(ndState ? R.string.nondestructive_patch_on
                                : R.string.nondestructive_patch_off),
                        Toast.LENGTH_SHORT).show();
                break;

            case R.id.auto_backup_dbs:
                // Default-ON safety net: a copy of every GMS DB is stashed before an edit. This
                // makes that toggle user-visible/disableable. See DbBackup.
                boolean backupState = !item.isChecked();
                item.setChecked(backupState);
                DbBackup.setEnabled(getApplicationContext(), backupState);
                Toast.makeText(getApplicationContext(),
                        getString(backupState ? R.string.auto_backup_dbs_on
                                : R.string.auto_backup_dbs_off),
                        Toast.LENGTH_SHORT).show();
                break;

            case R.id.phixit_apply_test:
                phixitApplyTest((TextView) findViewById(R.id.logs));
                break;

            case R.id.phixit_dump_all:
                dumpAllFlags((TextView) findViewById(R.id.logs));
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    public void save(final boolean isChecked, String key) {
        SharedPreferences sharedPreferences = getPreferences(getContext().MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, isChecked);
        editor.apply();
        // Keep the background re-apply job in sync with what's enabled.
        ReapplyScheduler.sync(getApplicationContext());
    }

    public void saveValue(final int value, String key) throws RuntimeException {
        SharedPreferences sharedPreferences = getPreferences(getContext().MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public void saveFloat(final float value, String key) {
        SharedPreferences sharedPreferences = getPreferences(getContext().MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    public boolean load(String key) {
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(key, false);
    }

    public int loadValue(String key) {
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        return sharedPreferences.getInt(key, 0);
    }

    public float loadFloat(String key) {
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        return sharedPreferences.getFloat(key, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public void patchforapps() {

        if (temp) {
            return;
        }


        final TextView logs = getLogsView();

        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.tweak_loading), true);

        // The app uninstall/reinstall loop (re-signing the APKs through pm so they pass
        // installer-source validation) lives ONLY here -- it must never run on the headless
        // re-apply path. The flag overrides themselves are produced by
        // TweakRegistry.patchedAppsSpecs() and applied via the phixit engine below.
        //
        // The destructive uninstall/reinstall is still the DEFAULT. An opt-in, default-OFF
        // experimental "non-destructive" mode (pm set-installer only) exists so a maintainer
        // can run the decisive on-device test of whether the ~11 validation-bypass flags
        // already subsume the installer re-stamp. set-installer is a strictly WEAKER spoof: it
        // only changes getInstallingPackageName(), NOT the immutable getInitiatingPackageName()
        // that the destructive reinstall also re-stamps. See
        // docs/patch-apps-installer-analysis.md.
        final PatchAppsPolicy.Mode mode = PatchAppsPolicy.modeFor(
                isExperimentalNonDestructivePatch(getApplicationContext()));
        new Thread() {
            @Override
            public void run() {
                SharedPreferences appsListPref =
                        getApplicationContext().getSharedPreferences("appsListPref", 0);
                Map<String, ?> allEntries = appsListPref.getAll();
                appendText(logs, "--  Apps which will be added to whitelist: --\n");
                appendText(logs, "--  Patch mode: " + mode + " --\n");
                // Collects any package left uninstalled/un-restored, paired with the temp-APK
                // path that holds the only surviving copy, so we can surface the data-loss to the
                // user after the loop (a loss buried in a scrolling log is effectively invisible).
                final java.util.List<String> lostPackages = new java.util.ArrayList<>();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    String pkg = entry.getKey();
                    appendText(logs, "\t\t- " + entry.getValue() + " (" + pkg + ")\n");

                    // Validate the package name BEFORE it is ever interpolated into a root
                    // command -- this closes the shell-injection vector for the whole loop.
                    if (!PatchAppsPolicy.isValidPackageName(pkg)) {
                        appendText(logs, "\t\t  SKIPPED: invalid/unsafe package name\n");
                        continue;
                    }

                    if (mode == PatchAppsPolicy.Mode.SET_INSTALLER) {
                        patchAppSetInstaller(logs, pkg);
                    } else {
                        String lost = patchAppDestructive(logs, pkg);
                        if (lost != null) {
                            lostPackages.add(lost);
                        }
                    }
                }

                // Surface any apps left uninstalled/un-restored as a dialog -- these are
                // data-loss situations the user must act on (recover the temp APK).
                if (!lostPackages.isEmpty()) {
                    showLostPackagesDialog(lostPackages);
                }

                appendText(logs, "\n\n--  restoring Google Play Services   --");
                appendText(logs, runSuWithCmd("pm enable com.google.android.gms").getStreamLogsWithLabels());

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        // Apply the whitelist + validation-bypass flags through the phixit
                        // engine: captures baselines, persists the toggle, updates the UI.
                        applyPhixitTweakSpecs("aa_patched_apps",
                                TweakRegistry.patchedAppsSpecs(getApplicationContext()),
                                patchappstatus, patchapps,
                                getString(R.string.patch_custom_apps));
                    }
                });
            }
        }.start();
    }

    /** SharedPreferences key for the opt-in, default-OFF non-destructive patch mode. */
    static final String PREF_EXPERIMENTAL_NONDESTRUCTIVE_PATCH =
            "experimental_nondestructive_patch";

    /**
     * Reads the experimental non-destructive patch preference (default {@code false}). Stored
     * in the shared {@link PhixitEngine#PREFS} file (the same explicitly-named file used by
     * {@link #isDevMode(Context)} and {@link DbBackup}) so all the app toggles live together.
     * See {@link PatchAppsPolicy} and docs/patch-apps-installer-analysis.md.
     */
    static boolean isExperimentalNonDestructivePatch(Context ctx) {
        return ctx.getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_EXPERIMENTAL_NONDESTRUCTIVE_PATCH, false);
    }

    private void setExperimentalNonDestructivePatch(boolean enabled) {
        getSharedPreferences(PhixitEngine.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_EXPERIMENTAL_NONDESTRUCTIVE_PATCH, enabled)
                .apply();
    }

    /**
     * Experimental, non-destructive per-app patch: only re-stamps the installing package via
     * {@code pm set-installer}. The app is never uninstalled, so this only changes
     * {@code getInstallingPackageName()} -- NOT the immutable {@code getInitiatingPackageName()}
     * the destructive reinstall also re-stamps -- making it a strictly weaker spoof. {@code pkg}
     * must already be validated by {@link PatchAppsPolicy#isValidPackageName(String)}.
     */
    private void patchAppSetInstaller(final TextView logs, String pkg) {
        // Existence guard (mirrors the destructive path): skip cleanly if the package is gone.
        if (!isPackageInstalled(pkg)) {
            appendText(logs, "\t\t  SKIPPED: package not found (" + pkg + ")\n");
            return;
        }
        String cmd = "pm set-installer " + pkg + " " + PatchAppsPolicy.PLAY_STORE_PKG;
        com.topjohnwu.superuser.Shell.Result r = runSuWithCmdResult(cmd);
        appendText(logs, describeResult(cmd, r));
        // Judge by pm's OUTPUT, not its exit code: pm exits 0 even when it prints Failure.
        if (!pmResultOk(r)) {
            appendText(logs, "\n\t\t  WARNING: set-installer failed for " + pkg + "\n");
        }
    }

    /**
     * Default destructive per-app patch: copy the base APK aside, uninstall, reinstall with the
     * Play Store as the installer/initiator. Because {@code pm} exits 0 even when it prints
     * {@code Failure}, every {@code pm} step is judged by its OUTPUT ({@link #pmResultOk}), and
     * the temp APK -- the only surviving copy after uninstall -- is deleted ONLY after positively
     * confirming via PackageManager that the package is installed. The sequencing decision (when
     * to proceed / rollback / keep or drop the temp) is delegated to the pure, unit-tested
     * {@link PatchAppsPolicy#nextAction}. {@code pkg} must already be validated by
     * {@link PatchAppsPolicy#isValidPackageName(String)}.
     *
     * @return {@code null} on success/safe-abort, or a human-readable "pkg -> tmpApk" loss
     *         descriptor when the app was left uninstalled with only the temp APK surviving.
     */
    private String patchAppDestructive(final TextView logs, String pkg) {
        // Resolve the APK path(s) via PackageManager instead of parsing "pm path" output -- this
        // removes the shell parse AND lets us detect split APKs (which the single-base-APK
        // reinstall would corrupt).
        ApplicationInfo ai;
        try {
            ai = getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            appendText(logs, "\t\t  SKIPPED: package not found (" + pkg + ")\n");
            return null;
        }
        if (PatchAppsPolicy.isSplitApk(ai.splitSourceDirs)) {
            // A split APK can't be safely re-stamped by the single-base-APK reinstall; failing
            // gracefully is far better than corrupting (uninstalling) the app.
            appendText(logs, "\t\t  SKIPPED: split APK -- cannot safely reinstall (" + pkg + ")\n");
            return null;
        }
        String srcApk = ai.sourceDir;
        if (srcApk == null || srcApk.isEmpty()) {
            appendText(logs, "\t\t  SKIPPED: no source APK path (" + pkg + ")\n");
            return null;
        }

        // Quote every non-validated interpolated value (srcApk + tmpApk path) for the shell;
        // pkg itself is already validated, but quoting OS-controlled paths is defence-in-depth.
        String tmpApk = PatchAppsPolicy.tmpApkPath(pkg);
        String qTmp = PatchAppsPolicy.quoteShellArg(tmpApk);

        // 1. Copy (not move) the APK aside first, so the original stays in place until the copy
        //    is confirmed -- we only uninstall once the APK is safely saved.
        String cpCmd = "cp " + PatchAppsPolicy.quoteShellArg(srcApk) + " " + qTmp;
        com.topjohnwu.superuser.Shell.Result cp = runSuWithCmdResult(cpCmd);
        appendText(logs, describeResult(cpCmd, cp));
        if (PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.COPY, shellOk(cp))
                == PatchAppsPolicy.NextAction.ABORT_APP_UNTOUCHED) {
            appendText(logs, "\n\t\t  ABORTED: could not copy APK aside; app left untouched (" + pkg + ")\n");
            return null;
        }

        // 2. Uninstall (APK is now safely saved at tmpApk). pm exits 0 on failure, so judge by output.
        String unCmd = "pm uninstall " + pkg;
        com.topjohnwu.superuser.Shell.Result un = runSuWithCmdResult(unCmd);
        appendText(logs, describeResult(unCmd, un));
        if (PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.UNINSTALL, pmResultOk(un))
                == PatchAppsPolicy.NextAction.ABORT_DELETE_TMP) {
            appendText(logs, "\n\t\t  ABORTED: uninstall failed; app left installed (" + pkg + ")\n");
            runSuWithCmd("rm -f " + qTmp);
            return null;
        }

        // 3. Reinstall, re-stamping the Play Store as installer + initiator.
        String inCmd = "pm install -t -i \"" + PatchAppsPolicy.PLAY_STORE_PKG + "\" -r " + qTmp;
        com.topjohnwu.superuser.Shell.Result in = runSuWithCmdResult(inCmd);
        appendText(logs, describeResult(inCmd, in));
        PatchAppsPolicy.NextAction afterInstall =
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.INSTALL, pmResultOk(in));
        if (afterInstall == PatchAppsPolicy.NextAction.DONE_DELETE_TMP) {
            return finishDestructive(logs, pkg, tmpApk, qTmp);
        }

        // 4. Install failed -> best-effort rollback (reinstall the saved APK).
        appendText(logs, "\n\t\t  install FAILED -- attempting rollback (" + pkg + ")\n");
        String rbCmd = "pm install -r " + qTmp;
        com.topjohnwu.superuser.Shell.Result rb = runSuWithCmdResult(rbCmd);
        appendText(logs, describeResult(rbCmd, rb));
        PatchAppsPolicy.NextAction afterRollback =
                PatchAppsPolicy.nextAction(PatchAppsPolicy.Step.ROLLBACK, pmResultOk(rb));
        if (afterRollback == PatchAppsPolicy.NextAction.DONE_DELETE_TMP) {
            return finishDestructive(logs, pkg, tmpApk, qTmp);
        }

        // Rollback also failed: KEEP the temp APK (only surviving copy) and report the loss.
        appendText(logs, "\n\t\t  ROLLBACK FAILED -- temp APK kept at " + tmpApk + "\n");
        return pkg + " -> " + tmpApk;
    }

    /**
     * Confirms the package is actually installed via PackageManager (NOT pm output) before
     * deleting the temp APK -- the only surviving copy after uninstall, so the delete is
     * irreversible. Deletes only when confirmed; otherwise keeps the temp APK and reports a loss.
     *
     * @return {@code null} when the package is present and the temp APK was deleted; a loss
     *         descriptor ("pkg -> tmpApk") when the package is absent and the temp APK was kept.
     */
    private String finishDestructive(final TextView logs, String pkg, String tmpApk, String qTmp) {
        if (isPackageInstalled(pkg)) {
            appendText(logs, "\n\t\t  confirmed installed; removing temp APK (" + pkg + ")\n");
            runSuWithCmd("rm -f " + qTmp);
            return null;
        }
        // pm claimed success but the package isn't actually there -- never delete the only copy.
        appendText(logs, "\n\t\t  WARNING: " + pkg + " reported success but is NOT installed -- "
                + "temp APK kept at " + tmpApk + "\n");
        return pkg + " -> " + tmpApk;
    }

    /** True iff {@code pkg} is currently installed (authoritative, unlike pm stdout). */
    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * True iff a {@code pm} command succeeded, judged by its OUTPUT (an exact {@code Success}
     * line) rather than its exit code: {@code pm} prints {@code Failure [...]} to stdout while
     * still exiting 0, so an exit-code check would mistake failures for success. A {@code null}
     * result (shell threw) is failure. Delegates the parse to the pure {@link PatchAppsPolicy}.
     */
    private static boolean pmResultOk(com.topjohnwu.superuser.Shell.Result r) {
        if (r == null) {
            return false;
        }
        // pm may write Success/Failure to either stream; check both.
        return PatchAppsPolicy.pmSucceeded(r.getOut())
                || PatchAppsPolicy.pmSucceeded(r.getErr());
    }

    /**
     * Surfaces apps left uninstalled (only the temp APK survives) to the user via the project's
     * {@link NotSuccessfulDialog} convention, on the main thread. Each entry names the package
     * and its {@code /data/local/tmp/tmpapk<pkg>.apk} recovery path.
     */
    private void showLostPackagesDialog(final java.util.List<String> lostPackages) {
        final StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.patch_apps_lost_intro)).append("\n\n");
        for (String loss : lostPackages) {
            sb.append("  - ").append(loss).append("\n");
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                DialogFragment d = new NotSuccessfulDialog();
                Bundle b = new Bundle();
                b.putString("tweak", "patch_apps_lost");
                b.putString("log", sb.toString());
                d.setArguments(b);
                d.show(getSupportFragmentManager(), "NotSuccessfulDialog");
            }
        });
    }

    private String gainOwnership(final TextView logs) {
        appendText(logs, "\n\n--  Gaining ownership of the database   --");
        appendText(logs, runSuWithCmd("chown root /data/data/com.google.android.gms/databases/phenotype.db").getStreamLogsWithLabels());

        String currentPolicy = runSuWithCmd("getenforce").getInputStreamLog();
        appendText(logs, "\n\n--  Setting SELINUX to permessive   --");
        appendText(logs, runSuWithCmd("setenforce 0").getStreamLogsWithLabels());
        return currentPolicy;
    }


    public void messageAutoRead() {
        applyPhixitTweak("aa_message_autoread", messageAutoReadStatus, null, null);
    }

    public void patchforspeed(int usercount) {
        applyPhixitTweak("aa_speed_hack", noSpeedRestrictionsStatus, nospeed, getString(R.string.unlimited_scrolling_when_driving));
    }

    public void multiDisplay() {
        applyPhixitTweak("multi_display", mdstatus, null, null);
    }

    public void patchfortouchlimit() {
        applyPhixitTweak("aa_six_tap", taplimitstatus, taplimitat, getString(R.string.disable_speed_limitations));
    }

    public void coolwalkdaynightpatch() {
        applyPhixitTweak("coolwalk_daynight_tweak", navstatus, null, null);
    }




    /** Flags toggled by the "battery saver warning" tweak (phixit schema). */
    private static java.util.List<FlagSpec> batteryWarningSpecs() {
        java.util.List<FlagSpec> l = new java.util.ArrayList<FlagSpec>();
        l.add(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "BatterySaver__warning_enabled", false));
        return l;
    }

    public void disableBatteryWarning() {
        final TextView logs = getLogsView();
        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.tweak_loading), true);

        new Thread() {
            @Override
            public void run() {
                appendText(logs, "\n\n--  Applying (phixit): battery saver warning  --");
                final boolean ok = applyPhixitSpecs(logs, batteryWarningSpecs(), true);
                if (ok) {
                    save(true, "battery_saver_warning");
                    // Persist reboot-pending so the icon stays yellow across an app restart.
                    tweakStateStore.setRebootPending("battery_saver_warning", true);
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (ok) {
                            changeStatus(batteryWarningStatus, 1, true);
                            showRebootButton();
                            batteryWarning.setText(getString(R.string.re_enable_tweak_string) + getString(R.string.battery_warning));
                        } else {
                            DialogFragment notSuccessfulDialog = new NotSuccessfulDialog();
                            Bundle bundle = new Bundle();
                            bundle.putString("tweak", "battery_saver_warning");
                            bundle.putString("log", logs.getText().toString());
                            notSuccessfulDialog.setArguments(bundle);
                            notSuccessfulDialog.show(getSupportFragmentManager(), "NotSuccessfulDialog");
                        }
                    }
                });
            }
        }.start();
    }

    /** Reverts the battery saver warning tweak by restoring the captured baseline. */
    public void revertBatteryWarning() {
        final TextView logs = getLogsView();
        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.tweak_loading), true);

        new Thread() {
            @Override
            public void run() {
                appendText(logs, "\n\n--  Reverting (phixit): battery saver warning  --");
                revertPhixitSpecs(logs, batteryWarningSpecs());
                save(false, "battery_saver_warning");
                // Tweak turned off: clear reboot-pending so it doesn't stay yellow.
                tweakStateStore.setRebootPending("battery_saver_warning", false);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        changeStatus(batteryWarningStatus, 0, true);
                        showRebootButton();
                        batteryWarning.setText(getString(R.string.disable_tweak_string) + getString(R.string.battery_warning));
                    }
                });
            }
        }.start();
    }

    public void battOutline() {
        applyPhixitTweak("aa_battery_outline", batteryOutlineStatus, null, null);
    }


    public void activateCoolwalk() {
        applyPhixitTweak("aa_activate_coolwalk", coolwalkTweakStatus, null, null);
    }

    public void deactivateCoolwalk() {
        applyPhixitTweak("aa_deactivate_coolwalk", nocoolwalkTweakStatus, nocoolwalkTweak, getString(R.string.coolwalk_tweak));
    }

    public void activateMaterialYou() {
        applyPhixitTweak("aa_material_you", materialYouTweakStatus, null, null);
    }

    public void activateAssistantTips() {
        applyPhixitTweak("aa_activate_assistant_tips", assistantTipsTweakStatus, null, null);
    }

    public void activatesmsdecline() {
        applyPhixitTweak("aa_activate_declinesms", declineSmsTweakStatus, null, null);
    }

    public void newSeekbar() {
        applyPhixitTweak("aa_new_seekbar", newSeekbarTweakStatus, null, null);
    }

    /**
     * Configures the logs {@link android.widget.TextView} for text selection.
     * Call this ONCE from {@code onCreate} — NOT on every button tap.
     *
     * <p>Selectability is configured here in code rather than in XML so that
     * this method can be directly exercised by unit tests without parsing XML.
     * Horizontal scrolling is provided by the wrapping {@code HorizontalScrollView}
     * (logs_hscroll), so {@code ScrollingMovementMethod} is intentionally NOT set
     * here — it would replace the {@code ArrowKeyMovementMethod} that Android
     * installs when selection is enabled, breaking long-press selection.
     *
     * <p>Package-private so that {@code LogsTextSelectableTest} can call it
     * directly on an inflated view.
     */
    void configureLogsView() {
        final TextView logs = getLogsView();
        logs.setTextIsSelectable(true);
    }

    /**
     * Returns the logs {@link android.widget.TextView}. Pure lookup — no side effects.
     * Call {@link #configureLogsView()} once from {@code onCreate} to set it up.
     */
    @NonNull
    TextView getLogsView() {
        return (TextView) findViewById(R.id.logs);
    }



    public void forceNoBt() {
        applyPhixitTweak("bluetooth_pairing_off", btstatus, bluetoothoff, getString(R.string.bluetooth_auto_connect));
    }

    public void disableTelemetry() {
        applyPhixitTweak("kill_telemetry", telemetryStatus, disableTelemetryButton, getString(R.string.telemetry_string));
    }

    public void uxprototypeTweak(URL URL) {
        applyPhixitTweak("uxprototype_tweak", uxprototypeTweakStatus, disableTelemetryButton, getString(R.string.uxprototype_tweak));
    }

    public void setHunDuration(View view, final int value) {
        saveValue(value, "messaging_hun_value");
        applyPhixitTweakSpecs("aa_hun_ms", TweakRegistry.hunSpecs(value), messagesHunStatus, null, null);
    }

    public void setMediaHunDuration(View view, final int value) {
        saveValue(value, "media_hun_value");
        applyPhixitTweakSpecs("aa_media_hun", TweakRegistry.mediaHunSpecs(value), mediaHunStatus, null, null);
    }

    public void setUSBbitrate(final double value) {
        saveFloat((float) value, "usb_bitrate_value");
        applyPhixitTweakSpecs("aa_bitrate_usb", TweakRegistry.usbBitrateSpecs(value), usbBitrateStatus, null, null);
    }

    public void setWiFiBitrate(final double value) {
        saveFloat((float) value, "wifi_bitrate_value");
        applyPhixitTweakSpecs("aa_bitrate_wireless", TweakRegistry.wifiBitrateSpecs(value), wifiBitrateStatus, null, null);
    }

    private void inertialScrollTweak() {
        applyPhixitTweak("aa_inertial_scroll", intertialScrollStatus, intertialScrollButton, getString(R.string.inertial_scroll_tweak));
    }


    private void verticalBarTweak() {
        applyPhixitTweak("aa_vertical_bar", verticalBarStatus, verticalBarTweakButton, getString(R.string.vertical_bar_tweak));
    }

    public void forceWideScreen(View view, final int value) {
        final TextView logs = getLogsView();
        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.tweak_loading), true);
        final StringBuilder finalCommand = new StringBuilder();



        if (value == 10) {
            finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__short_portrait_breakpoint_dp\", \"\"," + value + ",0);");
            finalCommand.append(System.getProperty("line.separator"));
            finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__portrait_breakpoint_dp\", \"\"," + value + ",0);");
            finalCommand.append(System.getProperty("line.separator"));
            finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__widescreen_breakpoint_dp\", \"\"," + 3000 + ",0);");
            finalCommand.append(System.getProperty("line.separator"));

        }

        if (value == 470) {
            finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__widescreen_breakpoint_dp\", \"\"," + value + ",0);");
            finalCommand.append(System.getProperty("line.separator"));
            finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__rail_assistant_media_rec_enabled_min_screen_width\", \"\"," + value + ",0);");
            finalCommand.append(System.getProperty("line.separator"));
        }

            if (value == 1920) {

                finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__regular_layout_max_width_dp\", \"\"," + 1919 + ",0);");
                finalCommand.append(System.getProperty("line.separator"));
                finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__semi_widescreen_breakpoint_dp\", \"\"," + value + ",0);");
                finalCommand.append(System.getProperty("line.separator"));
                finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__widescreen_breakpoint_dp\", \"\"," + 2000 + ",0);");
                finalCommand.append(System.getProperty("line.separator"));
                finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__short_portrait_breakpoint_dp\", \"\"," + value + ",0);");
                finalCommand.append(System.getProperty("line.separator"));
                finalCommand.append("INSERT OR REPLACE INTO FlagOverrides (packageName,  flagType, name, user, intVal, committed) VALUES (\"com.google.android.projection.gearhead\",  0,\"SystemUi__portrait_breakpoint_dp\", \"\"," + value + ",0);");
                finalCommand.append(System.getProperty("line.separator"));
            }
        

        runOnUiThread(new Thread() {
            @Override
            public void run() {
                String path = getApplicationInfo().dataDir;
                suitableMethodFound = true;
                killps(logs);
                String currentOwner = runSuWithCmd("stat -c \"%U\" /data/data/com.google.android.gms/databases/phenotype.db").getInputStreamLog();
                String currentPolicy = gainOwnership(logs);
                String decideWhat = new String();




                appendText(logs, "\n\n--  run SQL method   --");
                java.util.List<String> setupStmts = new java.util.ArrayList<String>();
                setupStmts.add("DROP TRIGGER IF EXISTS force_ws");
                setupStmts.add("DROP TRIGGER IF EXISTS force_no_ws");
                setupStmts.addAll(splitSql(finalCommand.toString()));
                RootDb.exec(PHENO_DB, setupStmts);

                switch (value) {
                    case 470: {
                        decideWhat = "force_ws";
                        break;
                    }
                    case 1920: {
                        decideWhat = "force_no_ws";
                        break;
                    }
                    case 10: {
                        decideWhat = "force_portrait";
                        break;
                    }
                }
                RootDb.exec(PHENO_DB,
                        "CREATE TRIGGER " + decideWhat + " AFTER DELETE\n" +
                                "On FlagOverrides\n" +
                                "BEGIN\n" + finalCommand + "END");
                if (RootDb.query(PHENO_DB, "SELECT name FROM sqlite_master WHERE type='trigger' AND name='" + decideWhat + "'").trim().length() <= 4) {
                    suitableMethodFound = false;
                } else {
                    appendText(logs, "\n--  end SQL method   --");
                    switch (value) {
                        case 470: {
                            forceNoWideScreen.setText(getString(R.string.force_disable_tweak) + getString(R.string.base_no_ws));
                            forcePortrait.setText(getString(R.string.enable_tweak_string) + getString(R.string.portrait_layout));
                            changeStatus(forceWideScreenStatus, 1, true);
                            changeStatus(forceNoWideScreenStatus, 0, false);
                            changeStatus(forcePortraitStatus, 0, false);
                            showRebootButton();
                            break;
                        }
                        case 1920: {
                            forceWideScreenButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.base_no_ws));
                            forcePortrait.setText(getString(R.string.enable_tweak_string) + getString(R.string.portrait_layout));
                            changeStatus(forceNoWideScreenStatus, 1, true);
                            changeStatus(forceWideScreenStatus, 0, false);
                            changeStatus(forcePortraitStatus, 0, false);
                            showRebootButton();
                            break;
                        }
                        case 10: {
                            forceNoWideScreen.setText(getString(R.string.force_disable_tweak) + getString(R.string.base_no_ws));
                            forceWideScreenButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.base_no_ws));
                            changeStatus(forcePortraitStatus, 1, true);
                            changeStatus(forceNoWideScreenStatus, 0, false);
                            changeStatus(forceWideScreenStatus, 0, false);
                            showRebootButton();
                            break;
                        }
                    }
                    save(true, decideWhat);
                }

                
                    appendText(logs, "\n\n--  restoring Google Play Services   --");
                    appendText(logs, runSuWithCmd("pm enable com.google.android.gms").getStreamLogsWithLabels());
                

appendText(logs, "\n\n--  Restoring ownership of the database   --");
                appendText(logs, runSuWithCmd("chown " + currentOwner + " /data/data/com.google.android.gms/databases/phenotype.db").getStreamLogsWithLabels());

                if (currentPolicy.toLowerCase().equals("permissive")) {
                    appendText(logs, "\n\n--  Restoring SELINUX   --");
                    appendText(logs, runSuWithCmd("setenforce 1").getStreamLogsWithLabels());
                }
                dialog.dismiss();
                if (!suitableMethodFound) {
                    final DialogFragment notSuccessfulDialog = new NotSuccessfulDialog();
                    Bundle bundle = new Bundle();
                    bundle.putString("tweak", decideWhat);
                    bundle.putString("log", logs.getText().toString());
                    notSuccessfulDialog.setArguments(bundle);
                    notSuccessfulDialog.show(getSupportFragmentManager(), "NotSuccessfulDialog");
                }
            }
        });

    }

    private void killps(final TextView logs) {
        appendText(logs, "\n\n--  Force stopping Google Play Services   --");
        appendText(logs, runSuWithCmd("am kill all com.google.android.gms").getStreamLogsWithLabels());
    }


    /**
     * PoC step 1: read-only diagnostic for the new "phixit" Phenotype schema.
     *
     * <p>Reads {@code param_partitions.flags_content} for the Android Auto config
     * packages, decompresses + decodes it with {@link PhixitSnapshot}, runs a
     * bit-exact round-trip self-test of the codec (decode -> encode must reproduce
     * the original decompressed bytes), and reports flag counts, naming scheme
     * (string vs numeric), and whether known AndroidAutoX target flags are present.
     * It performs NO writes to the GMS database.
     */
    private void phixitDiagnostic(final TextView logs) {
        final String path = getApplicationInfo().dataDir;
        final String db = "/data/data/com.google.android.gms/databases/phenotype.db";
        final String[] pkgs = {
                "com.google.android.projection.gearhead",
                "com.google.android.gms.car"
        };
        final String[] targets = {
                "AppQualityTester__developer_setting_enabled",
                "ContentBrowse__sixtap_force_enabled",
                "ContentBrowse__drawer_default_allowed_taps_touchpad",
                "Watevra__max_list_size",
                "BatterySaver__warning_enabled",
                "MultiDisplay__enabled"
        };

        new Thread() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("\n==== PHIXIT PoC DIAGNOSTIC (read-only) ====\n");

                for (String pkg : pkgs) {
                    sb.append("\n-- package: ").append(pkg).append(" --\n");

                    String out = RootDb.query(db,
                            "SELECT param_partition_id, hex(flags_content) " +
                                    "FROM param_partitions WHERE static_config_package_id IN " +
                                    "(SELECT static_config_package_id FROM static_config_packages " +
                                    "WHERE name='" + pkg + "')");

                    if (out.isEmpty()) {
                        sb.append("  (no partitions matched by static_config_packages.name)\n");
                        String names = RootDb.query(db,
                                "SELECT static_config_package_id || '=' || name " +
                                        "FROM static_config_packages WHERE name LIKE '%gearhead%' " +
                                        "OR name LIKE '%projection%' OR name LIKE '%gms.car%'");
                        sb.append("  candidate static_config_packages:\n");
                        sb.append("    ").append(names.replace("\n", "\n    ")).append("\n");
                        continue;
                    }

                    java.util.List<PhixitSnapshot.Flag> all = new java.util.ArrayList<PhixitSnapshot.Flag>();
                    for (String line : out.split("\\r?\\n")) {
                        int bar = line.indexOf('|');
                        if (bar <= 0) continue;
                        String pid = line.substring(0, bar).trim();
                        String hex = line.substring(bar + 1).trim();
                        if (hex.isEmpty()) {
                            sb.append("  partition ").append(pid).append(": empty blob\n");
                            continue;
                        }
                        try {
                            byte[] comp = PhixitSnapshot.hexToBytes(hex);
                            byte[] dec = PhixitSnapshot.inflateRaw(comp);
                            java.util.List<PhixitSnapshot.Flag> flags = PhixitSnapshot.decode(dec);
                            byte[] re = PhixitSnapshot.encode(flags);
                            boolean ok = java.util.Arrays.equals(dec, re);

                            int strNamed = 0, numNamed = 0;
                            for (PhixitSnapshot.Flag f : flags) {
                                if (f.numericName) numNamed++; else strNamed++;
                            }

                            sb.append("  partition ").append(pid)
                                    .append(": comp=").append(comp.length)
                                    .append("B decomp=").append(dec.length)
                                    .append("B flags=").append(flags.size())
                                    .append(" (string=").append(strNamed)
                                    .append(", numeric=").append(numNamed).append(")")
                                    .append("  round-trip=").append(ok ? "PASS" : "FAIL")
                                    .append("\n");
                            if (!ok) {
                                sb.append("    !! re-encoded ").append(re.length)
                                        .append("B != original ").append(dec.length).append("B\n");
                            }

                            int shown = 0;
                            for (PhixitSnapshot.Flag f : flags) {
                                if (f.numericName) continue;
                                sb.append("      ").append(f.describe()).append("\n");
                                if (++shown >= 10) break;
                            }
                            all.addAll(flags);
                        } catch (Exception e) {
                            sb.append("  partition ").append(pid)
                                    .append(": DECODE EXCEPTION ").append(e).append("\n");
                        }
                    }

                    sb.append("  -- target flag lookup --\n");
                    for (String t : targets) {
                        PhixitSnapshot.Flag found = null;
                        for (PhixitSnapshot.Flag f : all) {
                            if (!f.numericName && t.equals(f.name)) { found = f; break; }
                        }
                        sb.append("    ").append(t).append(": ")
                                .append(found == null ? "NOT PRESENT" : found.describe())
                                .append("\n");
                    }
                }

                sb.append("==== END PoC DIAGNOSTIC ====\n");
                appendText(logs, sb.toString());
            }
        }.start();
    }

    /**
     * PoC step 2: WRITE path for the new "phixit" Phenotype schema.
     *
     * <p>Applies a couple of overrides to the Android Auto (gearhead) snapshot to
     * prove the mechanism end-to-end: one flag that already exists (edited in
     * place) and one that does not (appended). It edits the flag across ALL of
     * the package's param_partitions, bumps last_fetch.serving_version, clears
     * the phenotype file cache, and restarts GMS, then re-reads to confirm the
     * value survived the restart.
     *
     * <p>WARNING: this writes to the live GMS database. It is gated behind an
     * explicit menu action.
     */
    private void phixitApplyTest(final TextView logs) {
        final String path = getApplicationInfo().dataDir;
        final String filesDir = getFilesDir().getAbsolutePath();
        final String pkg = "com.google.android.projection.gearhead";

        new Thread() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("\n==== PHIXIT PoC APPLY (writes to GMS DB) ====\n");

                // Test edits: edit an existing long, append an absent bool.
                java.util.List<PhixitSnapshot.Flag> edits = new java.util.ArrayList<PhixitSnapshot.Flag>();
                edits.add(longFlag("ContentBrowse__drawer_default_allowed_taps_touchpad", 999));
                edits.add(boolFlag("AppQualityTester__developer_setting_enabled", true));

                String policy = runSuWithCmd("getenforce").getInputStreamLog();
                runSuWithCmd("setenforce 0");
                runSuWithCmd("am force-stop com.google.android.gms");

                sb.append(phixitApply(path, filesDir, pkg, edits));

                // Drop GMS's cached phenotype so it re-reads our edited snapshot from
                // the DB, then force-stop so it restarts fresh on next access.
                runSuWithCmd("rm -rf /data/data/com.google.android.gms/files/phenotype");
                runSuWithCmd("am force-stop com.google.android.gms");
                if (!policy.equals("Permissive")) {
                    runSuWithCmd("setenforce 1");
                }

                // Verify: re-read and report the edited flags' current values.
                sb.append("  -- verify after restart --\n");
                for (PhixitSnapshot.Flag e : edits) {
                    sb.append("    ").append(phixitReadFlag(path, pkg, e.name)).append("\n");
                }

                sb.append("==== END PoC APPLY ====\n");
                appendText(logs, sb.toString());
            }
        }.start();
    }

    private static PhixitSnapshot.Flag longFlag(String name, long value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_LONG;
        f.longValue = value;
        return f;
    }

    private static PhixitSnapshot.Flag boolFlag(String name, boolean value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = value ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE;
        return f;
    }

    private static void copyValue(PhixitSnapshot.Flag dst, PhixitSnapshot.Flag src) {
        dst.type = src.type;
        dst.longValue = src.longValue;
        dst.doubleBits = src.doubleBits;
        dst.stringValue = src.stringValue;
        dst.bytesValue = src.bytesValue;
    }

    /** Applies {@code edits} to every param_partition of {@code pkg}. Returns a log. */
    private String phixitApply(String path, String filesDir, String pkg,
                               java.util.List<PhixitSnapshot.Flag> edits) {
        StringBuilder sb = new StringBuilder();

        java.util.List<Partition> raw;
        try {
            raw = RootDb.readPartitions(pkg);
        } catch (Exception e) {
            sb.append("  read ERR: ").append(e).append("\n");
            return sb.toString();
        }
        if (raw.isEmpty()) {
            sb.append("  no partitions for ").append(pkg).append("\n");
            return sb.toString();
        }

        java.util.List<Partition> toWrite = new java.util.ArrayList<Partition>();
        for (Partition p : raw) {
            if (p.blob == null || p.blob.length == 0) continue;
            try {
                byte[] dec = PhixitSnapshot.inflateRaw(p.blob);
                java.util.List<PhixitSnapshot.Flag> flags = PhixitSnapshot.decode(dec);

                for (PhixitSnapshot.Flag e : edits) {
                    PhixitSnapshot.Flag found = null;
                    for (PhixitSnapshot.Flag f : flags) {
                        if (!f.numericName && e.name.equals(f.name)) { found = f; break; }
                    }
                    if (found != null) {
                        copyValue(found, e);
                    } else {
                        PhixitSnapshot.Flag add = new PhixitSnapshot.Flag();
                        add.name = e.name;
                        add.numericName = false;
                        copyValue(add, e);
                        flags.add(add);
                    }
                }

                byte[] reCompressed = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(flags));
                toWrite.add(new Partition(p.id, reCompressed));
                sb.append("  partition ").append(p.id).append(": ").append(flags.size())
                        .append(" flags, wrote ").append(reCompressed.length).append("B\n");
            } catch (Exception e) {
                sb.append("  partition ").append(p.id).append(": EXCEPTION ").append(e).append("\n");
            }
        }

        int servingVersion = (int) (System.currentTimeMillis() / 1000L);
        try {
            RootDb.writePartitions(toWrite, servingVersion);
            sb.append("  applied (serving_version=").append(servingVersion).append(")\n");
        } catch (Exception e) {
            sb.append("  apply ERR: ").append(e).append("\n");
        }
        return sb.toString();
    }

    /** Reads a single flag's current decoded value across all partitions of a package. */
    private String phixitReadFlag(String path, String pkg, String name) {
        java.util.List<Partition> raw;
        try {
            raw = RootDb.readPartitions(pkg);
        } catch (Exception e) {
            return name + ": read ERR " + e;
        }
        for (Partition p : raw) {
            if (p.blob == null || p.blob.length == 0) continue;
            try {
                java.util.List<PhixitSnapshot.Flag> flags =
                        PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(p.blob));
                for (PhixitSnapshot.Flag f : flags) {
                    if (!f.numericName && name.equals(f.name)) {
                        return f.describe() + " [partition " + p.id + "]";
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return name + ": NOT FOUND after apply";
    }

    // =====================================================================
    // Phixit engine: apply/revert flag overrides on the new Phenotype schema
    // by editing param_partitions.flags_content directly. This replaces the
    // legacy FlagOverrides/Flags SQL, which no longer exists on recent GMS.
    // =====================================================================

    public static final String PHENO_DB =
            "/data/data/com.google.android.gms/databases/phenotype.db";

    /**
     * Decodes every gearhead + car flag from the snapshot and writes them
     * (name = value) to a file, copied to /sdcard/Download for easy sharing.
     */
    private void dumpAllFlags(final TextView logs) {
        final String path = getApplicationInfo().dataDir;
        final String filesDir = getFilesDir().getAbsolutePath();
        new Thread() {
            @Override
            public void run() {
                StringBuilder file = new StringBuilder();
                String[] pkgs = {FlagSpec.PKG_GEARHEAD, FlagSpec.PKG_CAR};
                int total = 0;
                for (String pkg : pkgs) {
                    java.util.TreeMap<String, String> map = new java.util.TreeMap<String, String>();
                    java.util.List<Partition> raw;
                    try {
                        raw = RootDb.readPartitions(pkg);
                    } catch (Exception ex) {
                        raw = java.util.Collections.emptyList();
                    }
                    for (Partition p : raw) {
                        if (p.blob == null || p.blob.length == 0) continue;
                        try {
                            for (PhixitSnapshot.Flag f :
                                    PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(p.blob))) {
                                if (!f.numericName) map.put(f.name, f.describe());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    file.append("# ").append(pkg).append(" (").append(map.size()).append(" flags)\n");
                    for (java.util.Map.Entry<String, String> e : map.entrySet()) {
                        file.append(e.getValue()).append("\n");
                    }
                    file.append("\n");
                    total += map.size();
                }
                // Write straight to the public Downloads dir via MediaStore -- no root, no
                // shell cp/chmod (scoped storage; targetSdk 34). Same user-facing result: a
                // file the user can find in Downloads.
                boolean wrote = writeToDownloads(getApplicationContext(),
                        "androidautox_flags.txt", "text/plain", file.toString());
                if (wrote) {
                    appendText(logs, "\n\n==== DUMPED " + total +
                            " flags to Downloads/androidautox_flags.txt ====\n");
                } else {
                    appendText(logs, "\n  dump write ERR: could not write to Downloads\n");
                }
            }
        }.start();
    }

    /**
     * Writes {@code content} to the public Downloads directory via {@link MediaStore} -- no root
     * and no shell {@code cp}/{@code chmod} (scoped storage, targetSdk 34). Returns true on
     * success.
     */
    private static boolean writeToDownloads(Context ctx, String displayName,
                                            String mimeType, String content) {
        android.content.ContentResolver resolver = ctx.getContentResolver();
        String relativePath = android.os.Environment.DIRECTORY_DOWNLOADS;

        // Overwrite semantics: delete any prior entry with the same DISPLAY_NAME + RELATIVE_PATH
        // first, otherwise MediaStore appends " (1)", " (2)", ... on every re-export.
        resolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.provider.MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                        + android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?",
                new String[]{displayName, relativePath + "/"});

        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, relativePath);
        // Mark pending while we write so no other app sees a half-written file; clear it after.
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return false;
        }
        try (java.io.OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) {
                resolver.delete(uri, null, null);
                return false;
            }
            os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            return false;
        }
        // Clear IS_PENDING so the file becomes visible to other apps.
        android.content.ContentValues done = new android.content.ContentValues();
        done.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return true;
    }

    /**
     * Applies {@code specs} to every param_partition of each referenced package,
     * bumps last_fetch.serving_version, clears the phenotype file cache and
     * restarts GMS. When {@code captureBaseline} is true, the original value of
     * each flag (or "absent") is saved first so {@link #revertPhixitSpecs} can
     * restore it. Returns true on success.
     */
    private boolean applyPhixitSpecs(final TextView logs, java.util.List<FlagSpec> specs,
                                     boolean captureBaseline) {
        StringBuilder sb = new StringBuilder();
        boolean ok = new PhixitEngine(this, sb).applySpecs(specs, captureBaseline);
        appendText(logs, sb.toString());
        return ok;
    }

    /**
     * Maps each applied spec to a {@link FlagSpec#remove} spec for the same pkg/name, so the
     * phixit engine drops the override flags from the param_partition blobs. Used by the
     * no-baseline revert path (legacy pre-baseline applies) so a reverted tweak's flags really
     * leave the DB and {@code isAppliedStrict} reads FALSE afterwards.
     */
    private static java.util.List<FlagSpec> removeSpecsFor(java.util.List<FlagSpec> applied) {
        java.util.List<FlagSpec> removes = new java.util.ArrayList<FlagSpec>();
        if (applied == null) return removes;
        for (FlagSpec s : applied) {
            removes.add(FlagSpec.remove(s.pkg, s.name));
        }
        return removes;
    }

    /** Restores each spec's flag to the baseline captured at apply time. */
    private boolean revertPhixitSpecs(TextView logs, java.util.List<FlagSpec> applied) {
        StringBuilder sb = new StringBuilder();
        boolean ok = new PhixitEngine(this, sb).revertSpecs(applied);
        appendText(logs, sb.toString());
        return ok;
    }

    private void applySpecToList(java.util.List<PhixitSnapshot.Flag> flags, FlagSpec s) {
        int idx = -1;
        for (int i = 0; i < flags.size(); i++) {
            PhixitSnapshot.Flag f = flags.get(i);
            if (!f.numericName && s.name.equals(f.name)) { idx = i; break; }
        }
        if (s.remove) {
            if (idx >= 0) flags.remove(idx);
            return;
        }
        if (idx >= 0) {
            copyValue(flags.get(idx), s.flag);
        } else {
            PhixitSnapshot.Flag add = new PhixitSnapshot.Flag();
            add.name = s.name;
            add.numericName = false;
            copyValue(add, s.flag);
            flags.add(add);
        }
    }

    private String baselineKey(String pkg, String name) {
        return "phixit_base|" + pkg + "|" + name;
    }

    /** Reads a long-valued flag's current value from the snapshot, or {@code def}. */
    private long readPhixitLong(String pkg, String name, long def) {
        return new PhixitEngine(this, null).readLong(pkg, name, def);
    }

    private void captureBaselineIfAbsent(String pkg, String name,
                                         java.util.List<java.util.List<PhixitSnapshot.Flag>> parts) {
        SharedPreferences sp = getPreferences(Context.MODE_PRIVATE);
        String key = baselineKey(pkg, name);
        if (sp.contains(key)) return;
        PhixitSnapshot.Flag cur = null;
        for (java.util.List<PhixitSnapshot.Flag> p : parts) {
            for (PhixitSnapshot.Flag f : p) {
                if (!f.numericName && name.equals(f.name)) { cur = f; break; }
            }
            if (cur != null) break;
        }
        sp.edit().putString(key, serializeBaseline(cur)).apply();
    }

    private String serializeBaseline(PhixitSnapshot.Flag f) {
        if (f == null) return "A";
        switch (f.type) {
            case PhixitSnapshot.TYPE_BOOL_FALSE: return "B0";
            case PhixitSnapshot.TYPE_BOOL_TRUE:  return "B1";
            case PhixitSnapshot.TYPE_LONG:       return "L" + f.longValue;
            case PhixitSnapshot.TYPE_DOUBLE:     return "D" + f.doubleBits;
            case PhixitSnapshot.TYPE_STRING:
                try {
                    return "S" + android.util.Base64.encodeToString(
                            f.stringValue.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                } catch (Exception e) { return "S"; }
            case PhixitSnapshot.TYPE_BYTES:
                return "X" + PhixitSnapshot.bytesToHex(f.bytesValue);
            default: return "A";
        }
    }

    private FlagSpec deserializeBaseline(String pkg, String name, String b) {
        char tag = b.charAt(0);
        String body = b.substring(1);
        switch (tag) {
            case 'B': return FlagSpec.bool(pkg, name, body.equals("1"));
            case 'L': return FlagSpec.lng(pkg, name, Long.parseLong(body));
            case 'D': return FlagSpec.dbl(pkg, name, Double.longBitsToDouble(Long.parseLong(body)));
            case 'S':
                try {
                    return FlagSpec.str(pkg, name,
                            new String(android.util.Base64.decode(body, android.util.Base64.NO_WRAP), "UTF-8"));
                } catch (Exception e) { return FlagSpec.str(pkg, name, ""); }
            case 'X': return FlagSpec.bytes(pkg, name, PhixitSnapshot.hexToBytes(body));
            default:  return FlagSpec.remove(pkg, name);
        }
    }

    /**
     * Runs a shell command as root via libsu's persistent root shell and returns its
     * output in the same {@link StreamLogs} shape the rest of the app expects. Replaces
     * the old per-call {@code Runtime.exec("su")} (which spawned a fresh su every time
     * and had no timeout). DB work no longer goes through here -- it uses
     * {@link RootDb}/{@link PhixitRootService} -- but file/process/SELinux commands
     * (am, pm, rm, chown, dumpsys, setenforce, ...) still do.
     */
    public static StreamLogs runSuWithCmd(String cmd) {
        StreamLogs streamLogs = new StreamLogs();
        streamLogs.setOutputStreamLog(cmd);
        try {
            com.topjohnwu.superuser.Shell.Result result =
                    com.topjohnwu.superuser.Shell.cmd(cmd).exec();
            streamLogs.setInputStreamLog(joinLines(result.getOut()));
            streamLogs.setErrorStreamLog(joinLines(result.getErr()));
        } catch (Throwable t) {
            streamLogs.setErrorStreamLog(String.valueOf(t));
        }
        return streamLogs;
    }

    /**
     * Like {@link #runSuWithCmd(String)} but exposes the shell exit code so callers can react
     * to failures instead of silently ignoring them ({@link #runSuWithCmd(String)} discards the
     * code, which is fine for fire-and-forget callers but unsafe for the destructive
     * app-patching loop). The existing signature is intentionally left untouched so its other
     * callers are unaffected.
     *
     * @return the libsu {@link com.topjohnwu.superuser.Shell.Result} (exposes {@code getCode()}
     *         / {@code isSuccess()}), or {@code null} if the shell itself threw.
     */
    public static com.topjohnwu.superuser.Shell.Result runSuWithCmdResult(String cmd) {
        try {
            return com.topjohnwu.superuser.Shell.cmd(cmd).exec();
        } catch (Throwable t) {
            // Log the throwable (like runSuWithCmd does) so a shell death mid-patch-loop is
            // diagnosable. Returning null -> caller ABORTs, which is the safe direction.
            android.util.Log.w("AndroidAutoX", "runSuWithCmdResult failed for: " + cmd, t);
            return null;
        }
    }

    /** True iff the shell result is non-null and reported success (exit code 0). */
    private static boolean shellOk(com.topjohnwu.superuser.Shell.Result r) {
        return r != null && r.isSuccess();
    }

    /** Renders a {@link com.topjohnwu.superuser.Shell.Result} for the on-screen log. */
    private static String describeResult(String cmd, com.topjohnwu.superuser.Shell.Result r) {
        StreamLogs s = new StreamLogs();
        s.setOutputStreamLog(cmd);
        if (r != null) {
            s.setInputStreamLog(joinLines(r.getOut()));
            s.setErrorStreamLog(joinLines(r.getErr()));
        } else {
            s.setErrorStreamLog("shell threw / no result");
        }
        String out = s.getStreamLogsWithLabels();
        if (r != null && !r.isSuccess()) {
            out += "\n\t(exit code " + r.getCode() + ")";
        }
        return out;
    }

    /**
     * True if root access has actually been granted (a non-root libsu fallback shell
     * would still happily echo "1", so callers must check the shell's real root status
     * rather than command output).
     *
     * <p>{@link com.topjohnwu.superuser.Shell#getShell()} <em>builds</em> the libsu shell
     * if one is not already cached, which is what spawns {@code su} and surfaces Magisk's
     * grant prompt the first time. We then read {@link com.topjohnwu.superuser.Shell#isRoot()},
     * which reflects whether {@code su} actually succeeded.
     *
     * <p><b>Important:</b> libsu caches the shell as a singleton. If a shell already exists
     * (e.g. a non-root fallback built after an earlier denial), {@code getShell()} returns
     * the cached instance and does <em>not</em> re-run {@code su}; the {@code id} probe below
     * runs on that same warm shell and does <em>not</em> re-prompt Magisk. To force a fresh
     * {@code su} (a genuine re-prompt), the cached shell must first be closed — see
     * {@link #resetRootShell()}, which the "Request again" retry path calls.
     *
     * <p>MUST be called off the main thread — it blocks while waiting for the user to tap
     * "Grant".
     *
     * @return {@code true} only if a root shell was obtained
     */
    @WorkerThread
    public static boolean hasRootAccess() {
        try {
            // getShell() blocks until the shell is constructed; with a root-capable
            // device a *fresh* shell spawns `su` and triggers Magisk's prompt. On a
            // warm/cached shell it just returns the existing instance.
            com.topjohnwu.superuser.Shell shell = com.topjohnwu.superuser.Shell.getShell();
            if (!shell.isRoot()) {
                return false;
            }
            // Sanity probe on the (root) shell. Note this does NOT re-exercise `su` on a
            // warm shell — see resetRootShell() for the actual re-prompt mechanism.
            com.topjohnwu.superuser.Shell.cmd("id").exec();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Closes the cached libsu shell so the next {@link #hasRootAccess()} /
     * {@link com.topjohnwu.superuser.Shell#getShell()} builds a brand-new one and
     * re-invokes {@code su} (re-prompting Magisk).
     *
     * <p>libsu 5.2.2 caches a single shell singleton; once it has been built — especially
     * a non-root fallback after a denial — {@code getShell()} keeps returning it and never
     * re-runs {@code su}. {@link com.topjohnwu.superuser.Shell} is {@link java.io.Closeable};
     * closing the cached instance puts it into a non-alive state, and libsu's internal cache
     * discards a non-alive shell on the next access so a fresh one is constructed.
     *
     * <p>Caveat: if the user previously chose "Deny" in Magisk and asked Magisk to remember
     * it, Magisk itself caches that decision — a fresh {@code su} may be auto-denied without
     * any visible prompt until the user changes the rule in Magisk. All we can do (and do
     * here) is genuinely re-issue {@code su} so a re-prompt happens whenever Magisk allows it.
     *
     * <p>MUST be called off the main thread (closing the shell can do I/O).
     */
    @WorkerThread
    public static void resetRootShell() {
        try {
            com.topjohnwu.superuser.Shell cached = com.topjohnwu.superuser.Shell.getCachedShell();
            if (cached != null) {
                cached.close();
            }
        } catch (Throwable t) {
            // Best-effort: even if close() throws, the next getShell() may still rebuild.
        }
    }

    private static String joinLines(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** Splits a simple {@code ;}-terminated SQL script into individual statements.
     *  Only used for legacy scripts whose statements (INSERTs) contain no inner ';'. */
    private static java.util.List<String> splitSql(String script) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String s : script.split(";")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }


    private void appendText(final TextView textView, final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append(s);
            }
        });
    }

    public void loadStatus(final String path) {

        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "",
                getString(R.string.loading), true);

        new Thread() {
            @Override
            public void run() {
                // Which legacy trigger-based tweaks are currently installed (so their
                // toggles show as enabled). Triggers on FlagOverrides/Flags cover the
                // previous per-name checks (after_delete, aa_patched_apps) as a subset.
                //
                // NOTE: this only marks legacy SQL-TRIGGER tweaks enabled (the force-screen
                // toggles force_ws / force_no_ws / force_portrait), none of which are in
                // TweakRegistry.ALL_KEYS. The flag-mapped (phixit) tweaks set Phenotype
                // param_partitions blobs and create NO triggers, so they can never be matched
                // here. Their status is owned by reconcileTweakStatusesInBackground(), the
                // authority for every flag-mapped key. The two run on independent background
                // threads (no ordering between them), but they can never fight: this scan ONLY
                // writes legacy trigger-based keys (the force-screen toggles), and NONE of those
                // are in TweakRegistry.ALL_KEYS — so the set of keys each one touches is disjoint.
                // Left in place because the legacy force-screen toggles (NOT reconciled) rely on it.
                String get_names = RootDb.query(PHENO_DB,
                        "SELECT name FROM sqlite_master WHERE type='trigger' " +
                                "AND tbl_name IN ('FlagOverrides','Flags')");
                for (String name : get_names.split("\\r?\\n")) {
                    if (!name.trim().isEmpty()) save(true, name.trim());
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                    }
                });
            }
        }.start();

    }

    public void getAndRemoveOptionsSelected() {
        final TextView log = findViewById(R.id.logs);
        final ProgressDialog dialog = ProgressDialog.show(MainActivity.this, "", getString(R.string.loading), true);
        new Thread() {
            @Override
            public void run() {

                String get_names = RootDb.query(PHENO_DB,
                        "SELECT name FROM sqlite_master WHERE type='trigger' AND tbl_name='Flags'");
                appendText(log, get_names);
                String[] lines = get_names.split("\\r?\\n");

                java.util.List<String> stmts = new java.util.ArrayList<String>();
                stmts.add("DROP TABLE FlagOverrides");
                stmts.add("DELETE FROM Flags WHERE name='com.google.android.projection.gearhead'");
                stmts.add("DELETE FROM Flags WHERE name='com.google.android.gms.car'");
                for (String name : lines) {
                    if (!name.trim().isEmpty()) stmts.add("DROP TRIGGER IF EXISTS " + name.trim());
                }
                stmts.add("CREATE TABLE FlagOverrides (packageName TEXT NOT NULL, user TEXT NOT NULL, " +
                        "name TEXT NOT NULL, flagType INTEGER NOT NULL, intVal INTEGER, boolVal INTEGER, " +
                        "floatVal REAL, stringVal TEXT, extensionVal BLOB, committed, " +
                        "PRIMARY KEY(packageName, user, name, committed))");
                RootDb.exec(PHENO_DB, stmts);
                appendText(log, "\n\tcleared FlagOverrides/Flags and dropped triggers");
                dialog.dismiss();
            }

        }.start();

        return;
    }

    public void showRebootButton() {
        // Delegate the whole reveal — VISIBLE/GONE, entrance animation and glow —
        // to the controller, which owns the shared RebootFabVisibility policy and
        // guarantees the entrance animation + glow run exactly once, the first
        // time the FAB actually becomes visible (immediately on the Tweaks page,
        // or deferred until the user returns from the Logs page).
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (rebootFabController != null) {
                    rebootFabController.reveal();
                }
            }
        });
    }

    /** Slowly drift, scale and soften the ambient aurora blobs for a dynamic AI feel. */
    private void startAurora() {
        float d = getResources().getDisplayMetrics().density;
        animateBlob(findViewById(R.id.aurora_blob1), 9000, 34f * d, 28f * d, 1.15f);
        animateBlob(findViewById(R.id.aurora_blob2), 13000, -40f * d, 24f * d, 1.25f);
        animateBlob(findViewById(R.id.aurora_blob3), 16000, 26f * d, -32f * d, 1.2f);
    }

    private void animateBlob(View v, long duration, float dx, float dy, float maxScale) {
        if (v == null) return;
        float blur = 40f * getResources().getDisplayMetrics().density;
        v.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL));
        ObjectAnimator tx = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, dx);
        ObjectAnimator ty = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, dy);
        ObjectAnimator sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, maxScale);
        ObjectAnimator sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, maxScale);
        for (ObjectAnimator a : new ObjectAnimator[]{tx, ty, sx, sy}) {
            a.setDuration(duration);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(tx, ty, sx, sy);
        set.start();
    }

    /**
     * Tint a view's elevation shadow with the logo azure so a raised surface
     * casts a soft blue glow instead of a flat gray shadow (API 28+; minSdk 31).
     */
    private void applyAzureGlow(View v) {
        if (v == null) return;
        int azure = ContextCompat.getColor(this, R.color.accent_blue);
        v.setOutlineSpotShadowColor(azure);
        v.setOutlineAmbientShadowColor(azure);
    }

    public static void openApp(Context context, String packageName) {
        if (isAppInstalled(context, packageName))
            if (isAppEnabled(context, packageName)) {
                PackageManager pm = context.getPackageManager();
                Intent launchIntent = new Intent("com.google.android.projection.gearhead.SETTINGS");
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            } else
                Toast.makeText(context, context.getString(R.string.not_enabled_warning), Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(context, context.getString(R.string.not_installed_warning), Toast.LENGTH_SHORT).show();
    }

    private static boolean isAppInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    private static boolean isAppEnabled(Context context, String packageName) {
        Boolean appStatus = false;
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (ai != null) {
                appStatus = ai.enabled;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return appStatus;
    }

    public static String replaceAll(StringBuilder builder, String from, String to) {
        Pattern p = Pattern.compile(from);
        Matcher m = p.matcher(builder);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, to);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void changeStatus(ImageView resource, int status, boolean doAnimation) {
        final RotateAnimation rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(400);
        rotate.setInterpolator(new LinearInterpolator());
        switch (status) {
            case 2: {
                resource.setImageDrawable(getDrawable(R.drawable.ic_baseline_check_circle_24));
                resource.setColorFilter(Color.argb(255, 0, 255, 0));
                break;
            }
            case 0: {
                resource.setImageDrawable(getDrawable(R.drawable.ic_baseline_remove_circle_24));
                resource.setColorFilter(Color.argb(255, 255, 0, 0));
                break;
            }
            case 1: {
                resource.setImageDrawable(getDrawable(R.drawable.ic_baseline_remove_circle_24));
                resource.setColorFilter(Color.argb(255, 255, 255, 0));
                break;
            }
        }
        if (doAnimation) {
            resource.startAnimation(rotate);
        }
    }


}
