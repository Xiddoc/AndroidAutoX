package com.xiddoc.androidautox.AccountsChooseActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Plain-JUnit tests for the {@link AccountInfo} POJO (constructor + accessors). */
public class AccountInfoTest {

    @Test
    public void ctor_setsNameAndChecked() {
        AccountInfo a = new AccountInfo("user@example.com", true);
        assertEquals("user@example.com", a.getName());
        assertTrue(a.getIsChecked());
    }

    @Test
    public void ctor_uncheckedDefault() {
        AccountInfo a = new AccountInfo("other@example.com", false);
        assertFalse(a.getIsChecked());
    }

    @Test
    public void setters() {
        AccountInfo a = new AccountInfo("a", false);
        a.setName("renamed");
        a.setChecked(true);
        assertEquals("renamed", a.getName());
        assertTrue(a.getIsChecked());
    }
}
