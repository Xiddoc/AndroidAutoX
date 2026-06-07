package com.xiddoc.androidautox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppsList extends AppCompatActivity {

    private ExtendedFloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> onApplyClicked());

        RecyclerView recyclerView = findViewById(R.id.apps_info);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        final PackageManager pm = getPackageManager();

        ArrayList<AppInfo> appsList = new ArrayList<>();

        SharedPreferences appsListPref = getApplicationContext().getSharedPreferences("appsListPref", 0);
        Map<String, ?> allEntries = appsListPref.getAll();

        // Enumerate every launchable app (those that expose a MAIN/LAUNCHER entry),
        // de-duped by package while preserving first-seen order.
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = pm.queryIntentActivities(launcherIntent, 0);

        Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo ri : launchables) {
            if (ri.activityInfo == null) {
                continue;
            }
            seen.add(ri.activityInfo.packageName);
        }

        for (String pkg : seen) {
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }

            int marker = ai.metaData != null
                    ? ai.metaData.getInt("com.google.android.gms.car.application")
                    : 0;
            boolean declaresCar = AppCompatibilityClassifier.hasCarMetadata(marker);
            Category cat = AppCompatibilityClassifier.classify(pkg, declaresCar);

            boolean checked = allEntries.containsKey(pkg);
            if (checked) {
                allEntries.remove(pkg);
            }

            appsList.add(new AppInfo(ai.loadLabel(pm).toString(), pkg, checked, cat));
        }

        // Surface any stale prefs entries whose package is no longer launchable, so
        // the user can still un-select them.
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String pkg = entry.getKey();
            appsList.add(new AppInfo(entry.getValue().toString(), pkg, true,
                    AppCompatibilityClassifier.classify(pkg, false)));
        }

        Collections.sort(appsList);
        MyAdapter adapter = new MyAdapter(appsList, recyclerView);
        // Re-evaluate the Apply FAB whenever the user toggles a row, so it tracks pending changes
        // live (Apply when the selection differs from what's applied, Revert when it matches).
        adapter.setOnSelectionChanged(this::refreshFab);
        recyclerView.setAdapter(adapter);

        refreshFab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reflect any change made while we were away (e.g. an apply that ran in MainActivity).
        refreshFab();
    }

    /** Package names the user currently wants patched (the whitelist). */
    private Set<String> desiredPackages() {
        return new LinkedHashSet<>(
                getSharedPreferences("appsListPref", MODE_PRIVATE).getAll().keySet());
    }

    /** Package names whose installer has already been re-stamped (recorded at apply time). */
    private Set<String> appliedPackages() {
        return new LinkedHashSet<>(
                getSharedPreferences(MainActivity.PATCHED_APPS_STATE, MODE_PRIVATE).getAll().keySet());
    }

    /** Updates the Apply FAB label/visibility from the current desired-vs-applied state. */
    private void refreshFab() {
        switch (PatchAppsPlan.decideFab(desiredPackages(), appliedPackages())) {
            case APPLY:
                fab.setText(R.string.apply_patch_apps);
                fab.show();
                break;
            case REVERT_ALL:
                fab.setText(R.string.revert_patch_apps);
                fab.show();
                break;
            default: // HIDDEN
                fab.hide();
                break;
        }
    }

    /**
     * Runs the apply: for a revert-all (selection already fully applied) the whitelist is cleared
     * first so the apply disables everything. The actual patch is performed by {@link MainActivity}
     * (it owns the root shell, phixit engine, logs and reboot prompt), reached via an intent that
     * brings the existing instance forward.
     */
    private void onApplyClicked() {
        boolean revertAll =
                PatchAppsPlan.decideFab(desiredPackages(), appliedPackages())
                        == PatchAppsPlan.FabAction.REVERT_ALL;
        if (revertAll) {
            getSharedPreferences("appsListPref", MODE_PRIVATE).edit().clear().apply();
        }
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PATCH_APPS, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
