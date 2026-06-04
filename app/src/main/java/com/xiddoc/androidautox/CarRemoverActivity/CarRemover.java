package com.xiddoc.androidautox.CarRemoverActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.rm.rmswitch.RMSwitch;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.xiddoc.androidautox.MainActivity;
import com.xiddoc.androidautox.NotSuccessfulDialog;
import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.RootDb;
import com.xiddoc.androidautox.Utils.RecyclerItemClickListener;

import static com.xiddoc.androidautox.MainActivity.runSuWithCmd;


public class CarRemover extends AppCompatActivity {

    SharedPreferences.OnSharedPreferenceChangeListener listener;


    @NonNull
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        final CarAdapter rvAdapter;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        Bundle b = getIntent().getExtras();
        String path = b.getString("path");

        final SharedPreferences accountsPrefs =  getSharedPreferences("idList", 0);
        SharedPreferences.Editor removeAll = accountsPrefs.edit();
        removeAll.clear();
        removeAll.apply();

        final ArrayList<CarInfo> allCars = new ArrayList<>();
        final Map<String, String> idsToBeRemoved = null;
        final int[] selected = {0};

        rvAdapter = new CarAdapter(allCars);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        ab.setTitle(R.string.choose_cars);

        final FloatingActionButton fab = findViewById(R.id.fab);
        fab.setBackgroundTintList(ColorStateList.valueOf(Color.argb(255, 255, 0, 0)));
        fab.setImageResource(R.drawable.trashcan);
        fab.hide();

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                final ProgressDialog dialog = ProgressDialog.show(CarRemover.this, "",
                        getString(R.string.tweak_loading), true);

                final java.util.List<String> deletes = new java.util.ArrayList<String>();
                Map<String, ?> allEntries = accountsPrefs.getAll();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    deletes.add("DELETE FROM allowedcars WHERE vehicleidclient='" + entry.getKey() + "'");
                }

                new Thread() {
                    @Override
                    public void run() {
                        RootDb.exec("/data/data/com.google.android.projection.gearhead/databases/carservicedata.db",
                                deletes);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(CarRemover.this, getString(R.string.removed_app_action),
                                        Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                                finish();
                            }
                        });
                    }
                }.start();

            }
        });


        final RecyclerView recyclerView = findViewById(R.id.apps_info);
        recyclerView.setHasFixedSize(true);


        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(getApplicationContext(), recyclerView ,new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        RMSwitch rSwitch = view.findViewById(R.id.checkbox_app);
                        rSwitch.toggle();
                        rvAdapter.onClickSaveAppsWhiteList(view, position);
                        if (rvAdapter.getChecked(position)){
                            selected[0]++;
                            fab.show();
                        } else {
                            selected[0]--;
                            if (selected[0] == 0) {
                                fab.hide();
                            }
                        }
                    }


                    @Override
                    public void onLongItemClick(View view, int position) {
                        //no need
                    }

                })
        );

        recyclerView.setAdapter(rvAdapter);

        // Load the paired-car list off the main thread, then populate the adapter.
        new Thread() {
            @Override
            public void run() {
                final String carDb =
                        "/data/data/com.google.android.projection.gearhead/databases/carservicedata.db";
                final String[] carRows =
                        RootDb.query(carDb, "SELECT manufacturer,model FROM allowedcars").split("\\r?\\n");
                final String[] idRows =
                        RootDb.query(carDb, "SELECT vehicleidclient FROM allowedcars").split("\\r?\\n");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (String str : carRows) {
                            if (!str.trim().isEmpty()) allCars.add(new CarInfo(str, false));
                        }
                        for (int i = 0; i < idRows.length && i < allCars.size(); i++) {
                            allCars.get(i).setId(idRows[i]);
                        }
                        rvAdapter.notifyDataSetChanged();
                    }
                });
            }
        }.start();
    }



}