package com.xiddoc.androidautox;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class CommonPageAdapter extends PagerAdapter {

    private final List<Integer> pageIds = new ArrayList<>();
    private final List<CharSequence> pageTitles = new ArrayList<>();


    /**
     * Append a page and return the index it was inserted at. Returning the
     * index lets callers derive page positions (e.g. the Logs page index) from
     * insertion order instead of hardcoding magic numbers that can drift.
     */
    public int insertViewId(@IdRes int pageId, CharSequence title) {
        pageIds.add(pageId);
        pageTitles.add(title);
        return pageIds.size() - 1;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return pageTitles.get(position);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        return container.findViewById(pageIds.get(position));
    }


    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    @Override
    public int getCount() {
        return pageIds.size();
    }


    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @Override
    public int getItemPosition(@NonNull Object object) {
        return POSITION_NONE;
    }

}
