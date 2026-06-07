package com.xiddoc.androidautox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.hide();

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
        recyclerView.setAdapter(new MyAdapter(appsList, recyclerView));
    }
}
