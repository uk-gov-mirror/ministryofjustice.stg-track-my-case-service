package uk.gov.moj.cp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import uk.gov.moj.cp.model.CourtCacheKey;
import uk.gov.moj.cp.dto.inbound.CourtScheduleDto;
import uk.gov.moj.cp.dto.inbound.CourtSittingDto;
import uk.gov.moj.cp.dto.inbound.HearingDto;
import uk.gov.moj.cp.dto.inbound.WeekCommencingDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsCourtScheduleDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsCourtSittingDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsHearingDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsWeekCommencingDto;
import uk.gov.moj.cp.dto.outbound.ProsecutionCaseDTO;
import uk.gov.moj.cp.dto.outbound.CourtHouseDto;
import uk.gov.moj.cp.metrics.TrackMyCaseMetricsService;
import uk.gov.moj.cp.model.HearingType;
import uk.gov.moj.cp.model.AmpApiType;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.time.LocalDateTime.parse;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseDetailsService {

    private final CourtScheduleService courtScheduleService;
    private final ProsectionCaseService prosectionCaseService;
    private final CourtHouseService courtHouseService;
    private final OAuthTokenService oauthTokenService;
    private final TrackMyCaseMetricsService trackMyCaseMetricsService;

    private static final Pattern HEARING_TYPE_TRIAL_PATTERN = Pattern.compile(
        "\\b" + HearingType.TRIAL.getValue() + "\\b",
        Pattern.CASE_INSENSITIVE
    );

    public CaseDetailsDto getCaseDetailsByCaseUrn(final String caseUrn) {
        try {
            return fetchCaseDetails(caseUrn);
        } catch (HttpStatusCodeException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            if (statusCode == HttpStatus.UNAUTHORIZED || statusCode == HttpStatus.FORBIDDEN) {
                log.warn("Token rejected with status {} for caseUrn: {}, evicting caches and retrying", statusCode, caseUrn);
                oauthTokenService.evictAllTokenCaches();
                return fetchCaseDetails(caseUrn);
            }
            throw e;
        }
    }

    private CaseDetailsDto fetchCaseDetails(final String caseUrn) {
        final String courtScheduleAccessToken = oauthTokenService.getJwtToken(AmpApiType.SLC);
        final String courtHouseAccessToken = oauthTokenService.getJwtToken(AmpApiType.RCC);
        final String prosecutionCaseAccessToken = oauthTokenService.getJwtToken(AmpApiType.PCD);

        final List<CourtScheduleDto> courtSchedule = courtScheduleService.getCourtScheduleByCaseUrn(courtScheduleAccessToken, caseUrn);
        final ProsecutionCaseDTO prosecutionCaseDto = prosectionCaseService.getCaseStatus(prosecutionCaseAccessToken, caseUrn);

        final Map<CourtCacheKey, CourtHouseDto> courtHouseCache = new HashMap<>();

        // Step 1: get the next hearings as a flat list
        final List<CaseDetailsHearingDto> nextHearings =
            buildNextHearings(caseUrn, courtHouseAccessToken, courtSchedule, courtHouseCache);

        // Step 2: get the past hearings as a flat list
        final List<CaseDetailsHearingDto> pastHearings =
            buildPastHearings(caseUrn, courtHouseAccessToken, courtSchedule, courtHouseCache);

        // Step 3: build List<CaseDetailsCourtScheduleDto> from both
        final List<CaseDetailsCourtScheduleDto> allSchedules = new ArrayList<>();
        allSchedules.add(CaseDetailsCourtScheduleDto.builder().hearings(nextHearings).build());
        if (!pastHearings.isEmpty()) {
            allSchedules.add(CaseDetailsCourtScheduleDto.builder().timeline(pastHearings).build());
        }

        trackMyCaseMetricsService.incrementCaseDetailsCount(caseUrn);
        return CaseDetailsDto.builder()
            .caseUrn(caseUrn)
            .caseStatus(prosecutionCaseDto.getCaseStatus())
            .courtSchedules(allSchedules)
            .build();
    }


    private List<CaseDetailsHearingDto> buildNextHearings(
            final String caseUrn, final String courtHouseAccessToken,
            final List<CourtScheduleDto> courtSchedule,
            final Map<CourtCacheKey, CourtHouseDto> courtHouseCache) {
        return courtSchedule.stream()
            .map(schedule -> schedule.getHearings().stream()
                .map(this::getHearingDetails)
                .filter(Objects::nonNull)
                .min(getCaseDetailsHearingDtoComparator())
                .map(h -> enrichHearingWithCourtDetails(caseUrn, courtHouseAccessToken, h, courtHouseCache))
                .orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<CaseDetailsHearingDto> buildPastHearings(
            final String caseUrn, final String courtHouseAccessToken,
            final List<CourtScheduleDto> courtSchedule,
            final Map<CourtCacheKey, CourtHouseDto> courtHouseCache) {
        return courtSchedule.stream()
            .map(schedule -> schedule.getHearings().stream()
                .map(this::getPastHearingDetails)
                .filter(Objects::nonNull)
                .map(h -> enrichHearingWithCourtDetails(caseUrn, courtHouseAccessToken, h, courtHouseCache))
                .toList())
            .flatMap(List::stream)
            .toList();
    }

    private CaseDetailsHearingDto getHearingDetails(final HearingDto hearing) {
        CaseDetailsWeekCommencingDto weekCommencing = null;
        List<CaseDetailsCourtSittingDto> courtSittings = null;

        if (isNull(hearing) || !isValidHearingType(hearing.getHearingType())) {
            return null;
        }

        if (Optional.ofNullable(hearing.getWeekCommencing()).isPresent()) {
            weekCommencing = getWeekCommencing(hearing);
            if (isNull(weekCommencing)) {
                return null;
            }
        } else {
            courtSittings = getCourtSittings(hearing);
            if (isNull(courtSittings)) {
                return null;
            }
        }

        return CaseDetailsHearingDto.builder()
            .hearingId(hearing.getHearingId())
            .hearingType(hearing.getHearingType())
            .hearingDescription(hearing.getHearingDescription())
            .listNote(hearing.getListNote())
            .courtSittings(courtSittings)
            .weekCommencing(weekCommencing)
            .build();
    }

    /**
     * Returns a past hearing for inclusion in the timeline, or null if the hearing should be excluded.
     * Past hearings always carry explicit court sitting dates (sittingStart / sittingEnd) rather than
     * a week-commencing range, so weekCommencing is not checked here. A hearing is considered past only
     * when every one of its sittings falls strictly before today; any current or future sitting means
     * the hearing is still active and belongs to the next-hearing section instead.
     */
    private CaseDetailsHearingDto getPastHearingDetails(final HearingDto hearing) {
        if (isNull(hearing) || !isValidHearingType(hearing.getHearingType())) {
            return null;
        }

        final List<CourtSittingDto> sittings = hearing.getCourtSittings();
        if (isNull(sittings) || sittings.isEmpty()) {
            return null;
        }

        boolean hasAnyCurrentOrFutureSitting = sittings.stream()
            .anyMatch(s -> {
                try {
                    return !parse(s.getSittingStart()).toLocalDate().isBefore(LocalDate.now());
                } catch (Exception e) {
                    return false;
                }
            });
        if (hasAnyCurrentOrFutureSitting) {
            return null;
        }

        return CaseDetailsHearingDto.builder()
            .hearingId(hearing.getHearingId())
            .hearingType(hearing.getHearingType())
            .hearingDescription(hearing.getHearingDescription())
            .listNote(hearing.getListNote())
            .courtSittings(sittings.stream().map(this::populateCourtSittings).toList())
            .build();
    }

    private static Comparator<CaseDetailsHearingDto> getCaseDetailsHearingDtoComparator() {
        return Comparator.comparing(
                CaseDetailsService::getEarliestHearingDate,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparing((CaseDetailsHearingDto dto) -> hasFixedDateHearing(dto) ? 0 : 1)
            .thenComparingInt((CaseDetailsHearingDto dto) ->
                                  nonNull(dto.getHearingType())
                                      && HEARING_TYPE_TRIAL_PATTERN.matcher(dto.getHearingType()).find()
                                      ? 0 : 1
            );
    }

    private static boolean hasFixedDateHearing(final CaseDetailsHearingDto hearingDto) {
        return nonNull(hearingDto.getCourtSittings())
            && !hearingDto.getCourtSittings().isEmpty()
            && hearingDto.getCourtSittings().stream()
            .anyMatch(s -> nonNull(s.getSittingStart()) && !s.getSittingStart().isEmpty());
    }

    private static LocalDate getEarliestHearingDate(final CaseDetailsHearingDto hearingDto) {
        LocalDate weekCommencingStartDate = null;
        LocalDate weekCommencingEndDate = null;

        LocalDate earliestSittingDate = getEarliestCourtSittingDate(hearingDto);
        try {
            weekCommencingStartDate = Optional.ofNullable(hearingDto.getWeekCommencing())
                .map(weekCommencing -> LocalDate.parse(weekCommencing.getStartDate()))
                .orElse(null);
            weekCommencingEndDate = Optional.ofNullable(hearingDto.getWeekCommencing())
                .map(weekCommencing -> LocalDate.parse(weekCommencing.getEndDate()))
                .orElse(null);
        } catch (Exception e) {
            log.error("parsing error for hearing {} : {}", hearingDto.getHearingId(), e.getMessage());
        }

        if (nonNull(earliestSittingDate) && nonNull(weekCommencingStartDate)) {
            return earliestSittingDate.isBefore(weekCommencingStartDate)
                || earliestSittingDate.equals(weekCommencingStartDate)
                ||
                (
                    earliestSittingDate.isAfter(weekCommencingStartDate)
                        && earliestSittingDate.isBefore(weekCommencingEndDate)
                )
                ? earliestSittingDate : weekCommencingStartDate;
        } else if (nonNull(earliestSittingDate)) {
            return earliestSittingDate;
        } else {
            return weekCommencingEndDate;
        }
    }

    private static LocalDate getEarliestCourtSittingDate(final CaseDetailsHearingDto hearingDto) {
        LocalDate earliestSittingDate = null;
        if (nonNull(hearingDto.getCourtSittings()) && !hearingDto.getCourtSittings().isEmpty()) {
            Optional<LocalDate> sittingDate = hearingDto.getCourtSittings()
                .stream()
                .filter(s -> nonNull(s.getSittingStart()))
                .map(s -> {
                    try {
                        return parse(s.getSittingStart()).toLocalDate();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());

            if (sittingDate.isPresent()) {
                earliestSittingDate = sittingDate.get();
            }
        }
        return earliestSittingDate;
    }

    private CaseDetailsWeekCommencingDto getWeekCommencing(final HearingDto hearing) {
        final WeekCommencingDto weekCommencingDto = hearing.getWeekCommencing();
        if (Strings.isEmpty(weekCommencingDto.getStartDate()) || Strings.isEmpty(weekCommencingDto.getEndDate())) {
            return null;
        }

        final boolean hasValidWeekCommencingDate = validateDateNotInPastAndNotAfterTenYears(LocalDate.parse(weekCommencingDto.getStartDate()))
            || validateDateNotInPastAndNotAfterTenYears(LocalDate.parse(weekCommencingDto.getEndDate()));
        if (!hasValidWeekCommencingDate) {
            return null;
        }

        final CourtHouseDto courtHouseDto = CourtHouseDto.builder()
            .courtHouseId(weekCommencingDto.getCourtHouse())
            .build();

        return CaseDetailsWeekCommencingDto.builder()
            .startDate(weekCommencingDto.getStartDate())
            .endDate(weekCommencingDto.getEndDate())
            .durationInWeeks(weekCommencingDto.getDurationInWeeks())
            .courtHouse(courtHouseDto)
            .build();

    }

    private List<CaseDetailsCourtSittingDto> getCourtSittings(final HearingDto hearing) {

        final List<CourtSittingDto> sittings = hearing.getCourtSittings();
        final boolean hasAnyCurrentOrFutureSitting = (nonNull(sittings) && !sittings.isEmpty())
            && sittings.stream()
            .anyMatch(s -> validateDateNotInPastAndNotAfterTenYears(parse(s.getSittingStart()).toLocalDate()));

        if (!hasAnyCurrentOrFutureSitting) {
            return null;
        }

        return sittings.stream()
            .map(this::populateCourtSittings)
            .toList();
    }

    private CaseDetailsCourtSittingDto populateCourtSittings(final CourtSittingDto courtSitting) {
        final CourtHouseDto courtHouseDto = CourtHouseDto.builder()
            .courtHouseId(courtSitting.getCourtHouse())
            .courtRoomId(courtSitting.getCourtRoom())
            .build();

        return CaseDetailsCourtSittingDto.builder()
            .judiciaryId(courtSitting.getJudiciaryId())
            .sittingStart(courtSitting.getSittingStart())
            .sittingEnd(courtSitting.getSittingEnd())
            .courtHouse(courtHouseDto)
            .build();
    }

    private boolean validateDateNotInPastAndNotAfterTenYears(final LocalDate hearingDate) {
        if (nonNull(hearingDate)) {
            try {
                return !(hearingDate.isBefore(LocalDate.now()) || hearingDate.isAfter(LocalDate.now().plusYears(10)));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isValidHearingType(final String hearingType) {
        if (isNull(hearingType)) {
            return false;
        }
        HearingType fromValue = HearingType.fromValue(hearingType);
        if (nonNull(fromValue)) {
            return true;
        }
        final String hearingTypeInLowerCase = hearingType.toLowerCase();
        if (hearingTypeInLowerCase.contains(HearingType.TRIAL.getValue().toLowerCase())
            || hearingTypeInLowerCase.contains(HearingType.SENTENCE.getValue().toLowerCase())) {
            // this is a case if you missed any hearing type in HearingType enum, which has "trial" or "sentence" in the value
            log.info("Hearing type does match Trail or Sentence filtering and not included in the enum [{}]", hearingType);
            return false;
        }
        return false;
    }

    private CaseDetailsHearingDto enrichHearingWithCourtDetails(final String caseUrn, final String courtHouseAccessToken,
                                                               CaseDetailsHearingDto hearing, final Map<CourtCacheKey, CourtHouseDto> courtHouseCache) {
        CaseDetailsWeekCommencingDto enrichedWeekCommencing = enrichWeekCommencingWithCourtDetails(
            courtHouseAccessToken,
            hearing.getWeekCommencing(),
            courtHouseCache
        );

        List<CaseDetailsCourtSittingDto> enrichedCourtSittings =
            (isNull(enrichedWeekCommencing) && nonNull(hearing.getCourtSittings()))
                ? enrichCourtSittingsWithCourtDetails(courtHouseAccessToken, hearing.getCourtSittings(), courtHouseCache)
                : null;

        if (nonNull(enrichedWeekCommencing)) {
            log.info(
                "caseUrn -{} : hearingId (W/C) - {} : CourtHouse Id - {} ",
                caseUrn,
                hearing.getHearingId(),
                enrichedWeekCommencing.getCourtHouse().getCourtHouseId()
            );
        } else {
            if (nonNull(enrichedCourtSittings)) {
                log.info(
                    "caseUrn [{}] : hearingId [{}] : CourtHouse Id [{}] :  CourtRoom Id [{}]",
                    caseUrn,
                    hearing.getHearingId(),
                    enrichedCourtSittings.getFirst().getCourtHouse().getCourtHouseId(),
                    enrichedCourtSittings.getFirst().getCourtHouse().getCourtRoomId()
                );
            } else {
                log.info(
                    "caseUrn -{} : hearingId - {} : CourtHouse details are null",
                    caseUrn,
                    hearing.getHearingId()
                );
            }
        }

        HearingType filteredHearingType = HearingType.filterHearingType(hearing.getHearingType());
        return CaseDetailsHearingDto.builder()
            .hearingId(hearing.getHearingId())
            .hearingType(filteredHearingType.getValue())
            .hearingDescription(hearing.getHearingDescription())
            .listNote(hearing.getListNote())
            .courtSittings(enrichedCourtSittings)
            .weekCommencing(enrichedWeekCommencing)
            .build();
    }

    private CaseDetailsWeekCommencingDto enrichWeekCommencingWithCourtDetails(final String accessToken,
                                                                              final CaseDetailsWeekCommencingDto weekCommencing,
                                                                              final Map<CourtCacheKey, CourtHouseDto> courtHouseCache) {
        if (isNull(weekCommencing)) {
            return null;
        }

        final String courtHouseId = weekCommencing.getCourtHouse().getCourtHouseId();
        final CourtHouseDto courtHouseDto = courtHouseCache.computeIfAbsent(
            new CourtCacheKey(courtHouseId, null), k -> courtHouseService.getCourtHouseById(accessToken, courtHouseId, null)
        );

        return CaseDetailsWeekCommencingDto.builder()
            .startDate(weekCommencing.getStartDate())
            .endDate(weekCommencing.getEndDate())
            .durationInWeeks(weekCommencing.getDurationInWeeks())
            .courtHouse(courtHouseDto)
            .build();
    }

    private List<CaseDetailsCourtSittingDto> enrichCourtSittingsWithCourtDetails(final String accessToken,
                                                                                 final List<CaseDetailsCourtSittingDto> courtSittings,
                                                                                 final Map<CourtCacheKey, CourtHouseDto> courtHouseCache) {
        CourtHouseDto courtHouse = courtSittings.getFirst().getCourtHouse();
        final String courtHouseId = courtHouse.getCourtHouseId();
        final String courtRoomId = courtHouse.getCourtRoomId();
        final CourtHouseDto courtHouseDto = courtHouseCache.computeIfAbsent(
            new CourtCacheKey(courtHouseId, courtRoomId), k -> courtHouseService.getCourtHouseById(accessToken, courtHouseId, courtRoomId)
        );

        return courtSittings.stream()
            .map(cs ->
                     CaseDetailsCourtSittingDto.builder()
                         .judiciaryId(cs.getJudiciaryId())
                         .sittingStart(cs.getSittingStart())
                         .sittingEnd(cs.getSittingEnd())
                         .courtHouse(courtHouseDto)
                         .build()
            )
            .toList();
    }

}

