package uk.gov.moj.cp.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Getter
public enum HearingType {

    TRIAL("Trial"),
    TRIAL_NO_WITNESSES("Trial - no witnesses"),
    TRIAL_BACKER("Trial (Backer)"),
    TRIAL_FLOATER("Trial (Floater)"),
    TRIAL_FIRST_WARNING("Trial (First Warning)"),
    TRIAL_PART_HEARD("Trial (Part Heard)"),
    TRIAL_OF_PRELIMINARY_ISSUE("Trial of Preliminary Issue"),
    TRIAL_PRIORITY("Trial (Priority)"),
    TRIAL_PREVIOUSLY_WARNED("Trial (Previously Warned)"),
    TRIAL_RESERVE("Trial (Reserve)"),
    TRIAL_LINKED("Trial Linked"),
    TRIAL_FIXED_THIS_WEEK("Trial (Fixed for this Week)"),

    SENTENCE("Sentence"),
    SENTENCE_AT_ANOTHER_COURT("Sentence (at another Court)"),
    SENTENCE_OFFICER_TO_ATTEND("Sentence (Officer to Attend)"),
    SENTENCE_PROSECUTION_TO_ATTEND("Sentence (Prosecution to Attend)"),
    SENTENCE_PROSECUTION_AND_OFFICER_TO_ATTEND("Sentence (Prosecution and Officer to Attend)"),
    SENTENCE_PROSECUTION_RELEASED("Sentence (Prosecution Released)"),
    COMMITTAL_FOR_SENTENCE("Committal for Sentence"),
    COMMITTAL_FOR_SENTENCE_PART_HEARD("Committal for Sentence (Part Heard)"),
    DEFERRED_SENTENCE("Deferred Sentence"),
    DEFERRED_SENTENCE_RESPONDENT_RELEASED("Deferred Sentence (Respondent Released)"),
    DEFERRED_SENTENCE_PROSECUTION_RELEASED("Deferred Sentence - Prosecution Released"),

    UNKNOWN("Unknown");

    private final String value;

    HearingType(String type) {
        this.value = type;
    }

    private static final Map<String, HearingType> HEARING_TYPES_MAP =
        Arrays.stream(values())
            .collect(Collectors.toMap(
                v -> v.value.toLowerCase(),
                v -> v
            ));

    public static HearingType fromValue(String value) {
        if (isNull(value)) {
            return null;
        }
        String key = value.toLowerCase();
        if (HEARING_TYPES_MAP.containsKey(key)) {
            return HEARING_TYPES_MAP.get(key);
        }
        return null;
    }

    public static HearingType filterHearingType(@NotNull String hearingTypeValue) {
        HearingType hearingType = HearingType.fromValue(hearingTypeValue);
        if (hearingType != null) {
            if (hearingType.getValue().toLowerCase().contains(HearingType.TRIAL.getValue().toLowerCase())) {
                return HearingType.TRIAL;
            } else if (hearingType.getValue().toLowerCase().contains(HearingType.SENTENCE.getValue().toLowerCase())) {
                return HearingType.SENTENCE;
            }
        }
        throw new IllegalArgumentException(hearingTypeValue + " is not an expected hearing type Trial or Sentence");
    }
}
