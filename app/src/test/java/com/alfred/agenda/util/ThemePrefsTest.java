package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Test;

/** Escolha de aparência (escuro, claro, sistema) e o modo noturno correspondente. */
public class ThemePrefsTest {

    @Test
    public void eachChoiceMapsToItsNightMode() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemePrefs.nightMode(ThemePrefs.DARK));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemePrefs.nightMode(ThemePrefs.LIGHT));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemePrefs.nightMode(ThemePrefs.SYSTEM));
    }

    @Test
    public void invalidChoiceFallsBackToDark() {
        assertEquals(ThemePrefs.DARK, ThemePrefs.safeMode(-1));
        assertEquals(ThemePrefs.DARK, ThemePrefs.safeMode(3));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemePrefs.nightMode(99));
    }
}
