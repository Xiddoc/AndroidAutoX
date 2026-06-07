package com.xiddoc.androidautox;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.rm.rmswitch.RMSwitch;
import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

import java.util.ArrayList;

public class MyAdapter extends RecyclerView.Adapter {

    private final ArrayList<AppInfo> mAppInfo;

    private class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView mName;
        public TextView mPackageName;
        public TextView mBadge;
        public RMSwitch mCheckboxApp;

        public MyViewHolder(View pItem) {
            super(pItem);
            mName = pItem.findViewById(R.id.app_name);
            mPackageName = pItem.findViewById(R.id.app_package_name);
            mBadge = pItem.findViewById(R.id.app_badge);
            mCheckboxApp = pItem.findViewById(R.id.checkbox_app);
        }
    }

    public MyAdapter(ArrayList<AppInfo> pAppsInfo, RecyclerView pRecyclerView){
        mAppInfo = pAppsInfo;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, final int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.app_info_layout, viewGroup, false);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickSaveAppsWhiteList(v, i);
                notifyItemChanged(i);
            }
        });

        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder viewHolder, int i) {
        final AppInfo appInfo = mAppInfo.get(viewHolder.getAdapterPosition());
        ((MyViewHolder) viewHolder).mName.setText(appInfo.getName());
        ((MyViewHolder) viewHolder).mPackageName.setText(appInfo.getPackageName());
        ((MyViewHolder) viewHolder).mCheckboxApp.setChecked(appInfo.getIsChecked());

        TextView badge = ((MyViewHolder) viewHolder).mBadge;
        Category category = appInfo.getCategory();
        badge.setText(badge.getContext().getString(badgeStringRes(category)));
        badge.setBackgroundColor(ContextCompat.getColor(badge.getContext(), badgeColorRes(category)));
        badge.setTextColor(ContextCompat.getColor(badge.getContext(), badgeTextColorRes(category)));

        ((MyViewHolder) viewHolder).mCheckboxApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MyViewHolder) viewHolder).mCheckboxApp.setChecked(appInfo.getIsChecked());
                onClickSaveAppsWhiteList(v, viewHolder.getAdapterPosition());
                notifyItemChanged(viewHolder.getAdapterPosition());
            }
        });
    }

    /** Badge label string resource for a compatibility category. */
    static int badgeStringRes(Category category) {
        switch (category) {
            case NATIVE_AUTO:
                return R.string.badge_native_auto;
            case MIRROR_SHIM:
                return R.string.badge_mirror_shim;
            default:
                return R.string.badge_needs_bridge;
        }
    }

    /** Badge background color resource for a compatibility category. */
    static int badgeColorRes(Category category) {
        switch (category) {
            case NATIVE_AUTO:
                return R.color.status_green;
            case MIRROR_SHIM:
                return R.color.status_yellow;
            default:
                return R.color.status_red;
        }
    }

    /**
     * Badge text color resource for a compatibility category. The MIRROR badge sits
     * on a light amber background, so it uses a dark text color for legible contrast;
     * the others keep near-white text on their darker green/red backgrounds.
     */
    static int badgeTextColorRes(Category category) {
        if (category == Category.MIRROR_SHIM) {
            return R.color.brand_navy_dark;
        }
        return R.color.text_primary;
    }

    private void onClickSaveAppsWhiteList (View v, int position) {
        SharedPreferences appsListPref = v.getContext().getSharedPreferences("appsListPref", 0);
        SharedPreferences.Editor editor = appsListPref.edit();
        if (mAppInfo.get(position).getIsChecked()) {
            editor.remove(mAppInfo.get(position).getPackageName());
            editor.apply();
            mAppInfo.get(position).setIsChecked(false);
            Toast.makeText(v.getContext(), v.getContext().getString(R.string.removed_app_action) + mAppInfo.get(position).getPackageName(), Toast.LENGTH_SHORT).show();
        } else {
            editor.putString(mAppInfo.get(position).getPackageName(), mAppInfo.get(position).getName());
            editor.commit();
            mAppInfo.get(position).setIsChecked(true);
            Toast.makeText(v.getContext(), v.getContext().getString(R.string.added_app_action) + mAppInfo.get(position).getPackageName(), Toast.LENGTH_SHORT).show();
            if (mAppInfo.get(position).getCategory() == Category.NEEDS_BRIDGE) {
                // Show the "won't render natively" hint at most once per install. The
                // flag lives in a SEPARATE prefs file (not appsListPref, whose keys ARE
                // the whitelisted package names) so it cannot corrupt the whitelist.
                SharedPreferences uiHints = v.getContext().getSharedPreferences("uiHintsPref", 0);
                if (!uiHints.getBoolean("needs_bridge_hint_shown", false)) {
                    Toast.makeText(v.getContext(), v.getContext().getString(R.string.badge_needs_bridge_hint), Toast.LENGTH_SHORT).show();
                    uiHints.edit().putBoolean("needs_bridge_hint_shown", true).apply();
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return mAppInfo.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }
}
