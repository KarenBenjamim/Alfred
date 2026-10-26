package com.alfred.agenda.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Forma de tratamento: vocativo, saudação e troca dos marcadores {voc}/{Voc}. */
public class HonorificTest {

    @Test
    public void vocativeUsesTitleOrName() {
        assertEquals("senhor", Honorific.vocative(Honorific.FORM_SENHOR, "Bruno"));
        assertEquals("senhora", Honorific.vocative(Honorific.FORM_SENHORA, "Ana"));
        assertEquals("senhorita", Honorific.vocative(Honorific.FORM_SENHORITA, "Ana"));
        assertEquals("Bruno", Honorific.vocative(Honorific.FORM_NAME, " Bruno "));
    }

    @Test
    public void greetingNameMatchesExamples() {
        // "Bom dia, Senhor Bruno" / "Bom dia, Bruno"
        assertEquals("Senhor Bruno", Honorific.fullName(Honorific.FORM_SENHOR, "Bruno"));
        assertEquals("Senhorita Ana", Honorific.fullName(Honorific.FORM_SENHORITA, "Ana"));
        assertEquals("Bruno", Honorific.fullName(Honorific.FORM_NAME, "Bruno"));
        assertEquals("Senhora", Honorific.fullName(Honorific.FORM_SENHORA, ""));
    }

    @Test
    public void applyReplacesTokensKeepingCapitalization() {
        assertEquals("Anotado, senhor.", Honorific.apply("Anotado, {voc}.", Honorific.FORM_SENHOR, "Bruno"));
        assertEquals("Senhora, sua refeição aguarda.",
                Honorific.apply("{Voc}, sua refeição aguarda.", Honorific.FORM_SENHORA, "Ana"));
        assertEquals("Anotado, Bruno.", Honorific.apply("Anotado, {voc}.", Honorific.FORM_NAME, "Bruno"));
        assertEquals("Bruno, sua refeição aguarda.",
                Honorific.apply("{Voc}, sua refeição aguarda.", Honorific.FORM_NAME, "Bruno"));
        assertEquals("Sem marcador.", Honorific.apply("Sem marcador.", Honorific.FORM_SENHOR, "Bruno"));
    }

    @Test
    public void missingNameDropsTheVocativeCleanly() {
        assertEquals("Anotado.", Honorific.apply("Anotado, {voc}.", Honorific.FORM_NAME, ""));
        assertEquals("sua refeição aguarda.", Honorific.apply("{Voc}, sua refeição aguarda.", Honorific.FORM_NAME, " "));
    }

    @Test
    public void invalidFormFallsBackToName() {
        assertEquals(Honorific.FORM_NAME, Honorific.safeForm(9));
        assertEquals(Honorific.FORM_NAME, Honorific.safeForm(-1));
        assertEquals("Bruno", Honorific.vocative(42, "Bruno"));
    }

    @Test
    public void normalizeNameCapitalizesWords() {
        assertEquals("Bruno De Souza", Honorific.normalizeName("  bruno   de souza "));
        assertEquals("Ána", Honorific.normalizeName("ána"));
        assertEquals("", Honorific.normalizeName("   "));
    }
}
