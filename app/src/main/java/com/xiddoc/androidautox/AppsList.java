package com.xiddoc.androidautox;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;

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

        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        ArrayList<AppInfo> appsList = new ArrayList<>();

        SharedPreferences appsListPref = getApplicationContext().getSharedPreferences("appsListPref", 0);
        Map<String, ?> allEntries = appsListPref.getAll();

        for (ApplicationInfo packageInfo : packages) {

            Bundle bundle = packageInfo.metaData;
            try {
                int carApp = bundle.getInt("com.google.android.gms.car.application");
                if (carApp != 0) {
                    if (allEntries.containsKey(packageInfo.packageName)) {

                        appsList.add(new AppInfo(packageInfo.loadLabel(getPackageManager()).toString(), packageInfo.packageName, true));
                        allEntries.remove(packageInfo.packageName);
                    } else {
                        appsList.add(new AppInfo(packageInfo.loadLabel(getPackageManager()).toString(), packageInfo.packageName, false));
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }


            }




        for (Map.Entry<String, ?> entry : allEntries.entrySet()){
            appsList.add(new AppInfo(entry.getValue().toString(), entry.getKey(), true));
        }

        Collections.sort(appsList);
        recyclerView.setAdapter(new MyAdapter(appsList, recyclerView));
    }
}
