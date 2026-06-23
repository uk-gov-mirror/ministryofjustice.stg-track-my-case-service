package uk.gov.moj.cp.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearingTypeTest {

    @ParameterizedTest
    @EnumSource(HearingType.class)
    void fromValue_returnsCorrectEnum_forEachExactValue(HearingType expected) {
        assertEquals(expected, HearingType.fromValue(expected.getValue()));
    }

    @ParameterizedTest
    @EnumSource(HearingType.class)
    void fromValue_returnsCorrectEnum_whenLowerCase(HearingType expected) {
        assertEquals(expected, HearingType.fromValue(expected.getValue().toLowerCase()));
    }

    @ParameterizedTest
    @EnumSource(HearingType.class)
    void fromValue_returnsCorrectEnum_whenUpperCase(HearingType expected) {
        assertEquals(expected, HearingType.fromValue(expected.getValue().toUpperCase()));
    }

    @Test
    void fromValue_returnsNull_forNull() {
        assertNull(HearingType.fromValue(null));
    }

    @Test
    void fromValue_returnsNull_forUnknownValue() {
        assertNull(HearingType.fromValue("UnknownType"));
        assertNull(HearingType.fromValue("Not a hearing type"));
    }

    @Test
    void fromValue_returnsNull_forEmptyString() {
        assertNull(HearingType.fromValue(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Trial",
        "Trial (Backer)",
        "Trial - no witnesses",
        "Trial (Floater)",
        "Trial (First Warning)",
        "Trial (Part Heard)",
        "Trial of Preliminary Issue",
        "Trial (Priority)",
        "Trial (Previously Warned)",
        "Trial (Reserve)",
        "Trial Linked",
        "Trial (Fixed for this Week)",
        "trial",
        "TRIAL"
    })
    void filterHearingType_returnsTrial_forTrialVariants(String value) {
        assertEquals(HearingType.TRIAL, HearingType.filterHearingType(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Sentence",
        "Sentence (at another Court)",
        "Sentence (Officer to Attend)",
        "Sentence (Prosecution to Attend)",
        "Sentence (Prosecution and Officer to Attend)",
        "Sentence (Prosecution Released)",
        "Committal for Sentence",
        "Committal for Sentence (Part Heard)",
        "Deferred Sentence",
        "Deferred Sentence (Respondent Released)",
        "Deferred Sentence - Prosecution Released",
        "sentence",
        "SENTENCE"
    })
    void filterHearingType_returnsSentence_forSentenceVariants(String value) {
        assertEquals(HearingType.SENTENCE, HearingType.filterHearingType(value));
    }

    @Test
    void filterHearingType_throws_forUnknownEnumValue() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> HearingType.filterHearingType("Unknown")
        );
        assertTrue(ex.getMessage().contains("is not an expected hearing type Trial or Sentence"));
    }

    @Test
    void filterHearingType_throws_forNullInput() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> HearingType.filterHearingType(null)
        );
        assertTrue(ex.getMessage().contains("is not an expected hearing type Trial or Sentence"));
    }

    @Test
    void filterHearingType_throws_forUnknownValue() {
        String unknown = "UnknownType";
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> HearingType.filterHearingType(unknown)
        );
        assertTrue(ex.getMessage().contains(unknown));
        assertTrue(ex.getMessage().contains("is not an expected hearing type Trial or Sentence"));
    }

    @Test
    void filterHearingType_throws_forEmptyString() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> HearingType.filterHearingType("")
        );
        assertTrue(ex.getMessage().contains("is not an expected hearing type Trial or Sentence"));
    }

}
