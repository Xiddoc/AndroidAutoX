package com.xiddoc.androidautox.CarRemoverActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Plain-JUnit tests for the {@link CarInfo} POJO (both constructors + accessors). */
public class CarInfoTest {

    @Test
    public void twoArgCtor_setsNameAndChecked_idNull() {
        CarInfo c = new CarInfo("My Car", true);
        assertEquals("My Car", c.getName());
        assertTrue(c.isChecked());
        assertTrue(c.getIsChecked());
        assertNull(c.getId());
    }

    @Test
    public void threeArgCtor_setsAllFields() {
        CarInfo c = new CarInfo("Car", false, "id-42");
        assertEquals("Car", c.getName());
        assertFalse(c.isChecked());
        assertFalse(c.getIsChecked());
        assertEquals("id-42", c.getId());
    }

    @Test
    public void setters() {
        CarInfo c = new CarInfo("Car", false);
        c.setName("Renamed");
        c.setChecked(true);
        c.setId("xyz");
        assertEquals("Renamed", c.getName());
        assertTrue(c.isChecked());
        assertTrue(c.getIsChecked());
        assertEquals("xyz", c.getId());
    }
}
