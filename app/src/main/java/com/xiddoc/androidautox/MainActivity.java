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
import android.view.animation.AnimationUtils;
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
    private View rebootGlow;
    private View rebootGlowOuter;
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
    private boolean animationRun;
    private boolean  urlprototype;


    ProgressDialog progress;

    SharedPreferences accountsPrefs;
    private URL url;



    public static Context getContext() {
        return mContext;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


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
        adapter.insertViewId(R.id.page_two, getString(R.string.tab_logs));
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

        // Phase 2 depth pass: a real two-layer RenderEffect glow behind the FAB —
        // a wide soft bloom plus a bright tight neon rim (API 31+). The glow views
        // live in a non-clipping FrameLayout (reboot_container) so they can grow
        // past the FAB; their inset-puck drawables give the blur room to bloom.
        float density = getResources().getDisplayMetrics().density;
        rebootContainer = findViewById(R.id.reboot_container);
        rebootGlowOuter = findViewById(R.id.reboot_glow_outer);
        configureGlowLayer(rebootGlowOuter, 30f * density, 0.6f);
        rebootGlow = findViewById(R.id.reboot_glow);
        configureGlowLayer(rebootGlow, 10f * density, 1.0f);
        applyAzureGlow(rebootButton);

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


        animationRun = false;

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

        verticalBarStatus = findViewById(R.id.vertical_bar_tweak_status);

        verticalBarTweakButton = findViewById(R.id.vertical_bar_tweak);
        if (load("aa_vertical_bar")) {
            verticalBarTweakButton.setText(getString(R.string.disable_tweak_string) + getString(R.string.vertical_bar_tweak));
            changeStatus(verticalBarStatus, 2, false);
        } else {
            verticalBarTweakButton.setText(getString(R.string.enable_tweak_string) + getString(R.string.vertical_bar_tweak));
            changeStatus(verticalBarStatus, 0, false);
        }

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
                // Migrated tweaks: restore the captured baseline via the phixit engine.
                if (PhixitTweaks.has(toRevert) && hasBaseline(toRevert)) {
                    appendText(logs, "\n\n--  Reverting (phixit): " + toRevert + "  --");
                    revertPhixitSpecs(logs, PhixitTweaks.specs(toRevert));
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
                if (ok) save(true, key);
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
        new Thread() {
            @Override
            public void run() {
                SharedPreferences appsListPref =
                        getApplicationContext().getSharedPreferences("appsListPref", 0);
                Map<String, ?> allEntries = appsListPref.getAll();
                appendText(logs, "--  Apps which will be added to whitelist: --\n");
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    appendText(logs, "\t\t- " + entry.getValue() + " (" + entry.getKey() + ")\n");

                    String pathResult = runSuWithCmd("pm path " + entry.getKey()).getInputStreamLogWithLabel();
                    String actualPath = pathResult.substring(pathResult.lastIndexOf(":") + 1);

                    appendText(logs , runSuWithCmd("mv " + actualPath + " /data/local/tmp/tmpapk" + entry.getKey() + ".apk").getStreamLogsWithLabels());
                    appendText(logs , runSuWithCmd("pm uninstall " + entry.getKey()).getStreamLogsWithLabels());
                    appendText(logs, runSuWithCmd("pm install -t -i \"com.android.vending\" -r" + " /data/local/tmp/tmpapk" + entry.getKey() + ".apk" ).getStreamLogsWithLabels());
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
                if (ok) save(true, "battery_saver_warning");

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
                String outFile = filesDir + "/all_flags.txt";
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                    fos.write(file.toString().getBytes("UTF-8"));
                    fos.close();
                } catch (Exception e) {
                    appendText(logs, "\n  dump write ERR: " + e + "\n");
                }
                runSuWithCmd("cp " + outFile + " /sdcard/Download/androidautox_flags.txt && chmod 644 /sdcard/Download/androidautox_flags.txt");
                appendText(logs, "\n\n==== DUMPED " + total +
                        " flags to /sdcard/Download/androidautox_flags.txt ====\n");
            }
        }.start();
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

    /** True if root access has actually been granted (a non-root libsu shell would
     *  still happily echo "1", so callers must check this rather than command output). */
    public static boolean hasRootAccess() {
        try {
            return com.topjohnwu.superuser.Shell.getShell().isRoot();
        } catch (Throwable t) {
            return false;
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
        runOnUiThread(new Thread() {
            @Override
            public void run() {
                final Animation anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.reboot_button_anim);

                if (!animationRun) {
                    // Reveal the FAB + both glow layers together (they share the
                    // reboot_container) and only then start the breathing pulse,
                    // so the entrance animation and the alpha animator don't fight.
                    if (rebootContainer != null) {
                        rebootContainer.setVisibility(View.VISIBLE);
                        rebootContainer.startAnimation(anim);
                    } else {
                        rebootButton.setVisibility(View.VISIBLE);
                        rebootButton.startAnimation(anim);
                    }
                    startGlowBreathing(rebootGlowOuter);
                    animationRun = true;
                }
            }
        });

    }

    /**
     * Turn a solid-colour puck into a real glow layer by blurring it at the
     * given radius (px) and dialing its intensity via alpha. Used for the FAB's
     * stacked outer-bloom / inner-rim halo.
     */
    private void configureGlowLayer(View v, float blurPx, float alpha) {
        if (v == null) return;
        v.setRenderEffect(RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL));
        v.setAlpha(alpha);
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

    /** Slow alpha pulse so the outer bloom gently "breathes" like Gemini's orb. */
    private void startGlowBreathing(View v) {
        if (v == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(v, View.ALPHA, 0.45f, 0.85f);
        pulse.setDuration(2200);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        pulse.start();
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
