package com.xiddoc.androidautox;

import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

public class AppInfo implements Comparable<AppInfo> {
    private String name;
    private String packageName;
    private boolean isChecked;
    private final Category category;

    public AppInfo(String name, String packageName, boolean isChecked) {
        this(name, packageName, isChecked, Category.NEEDS_BRIDGE);
    }

    public AppInfo(String name, String packageName, boolean isChecked, Category category) {
        this.name = name;
        this.packageName = packageName;
        this.isChecked = isChecked;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean getIsChecked() {
        return isChecked;
    }

    public void setIsChecked (boolean isChecked) {
        this.isChecked = isChecked;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public int compareTo(AppInfo o) {
        int comp1;
        int comp2;
        int result;

        comp1 = o.getIsChecked() ? 1 : 0;
        comp2 = this.getIsChecked() ? 1 : 0;

        result = comp1 - comp2;

        if (result != 0) {
            return result;
        }

        comp1 = AppCompatibilityClassifier.isKnownAa(o.getPackageName()) ? 1 : 0;
        comp2 = AppCompatibilityClassifier.isKnownAa(this.getPackageName()) ? 1 : 0;

        result = comp1 - comp2;

        if (result != 0) {
            return result;
        }

        return this.getName().compareTo(o.getName());
    }
}
