package uk.gov.moj.cp.service;

import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.moj.cp.dto.outbound.CaseDetailsDto;
import uk.gov.moj.cp.dto.outbound.CaseDetailsHearingDto;
import uk.gov.moj.cp.dto.outbound.CaseStatus;
import uk.gov.moj.cp.dto.outbound.ProsecutionCaseDTO;
import uk.gov.moj.cp.dto.outbound.CourtHouseDto;
import uk.gov.moj.cp.dto.outbound.CourtRoomDto;
import uk.gov.moj.cp.dto.outbound.AddressDto;
import uk.gov.moj.cp.dto.inbound.CourtScheduleDto;
import uk.gov.moj.cp.dto.inbound.HearingDto;
import uk.gov.moj.cp.dto.inbound.CourtSittingDto;
import uk.gov.moj.cp.dto.inbound.WeekCommencingDto;
import uk.gov.moj.cp.metrics.TrackMyCaseMetricsService;
import uk.gov.moj.cp.model.HearingType;
import uk.gov.moj.cp.model.AmpApiType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseDetailsServiceTest {

    @Mock
    private CourtScheduleService courtScheduleService;

    @Mock
    private ProsectionCaseService prosectionCaseService;

    @Mock
    private CourtHouseService courtHouseService;
    @Mock
    private OAuthTokenService oauthTokenService;

    @Mock
    private TrackMyCaseMetricsService trackMyCaseMetricsService;

    @InjectMocks
    private CaseDetailsService caseDetailsService;
    private final String accessToken = "testToken";

    private String caseUrn;
    private String courtHouseId;
    private String courtRoomId;
    private String judgeId;
    private String hearingId;
    private String pastSittingStartDate;
    private String pastSittingEndDate;
    private String currentSittingStartDate;
    private String currentSittingEndDate;
    private String futureSittingStartDate;
    private String futureSittingEndDate;
    private String datePlus7;
    private String datePlus13;
    private String datePlus14;
    private String datePlus21;
    private String datePlus42;
    private String datePlus49;
    private String datePlus43;
    private String datePlus50;

    private CourtRoomDto courtRoomDto;
    private AddressDto addressDto;

    @BeforeEach
    void setUp() {
        caseUrn = "CASE123";
        courtHouseId = randomUUID().toString();
        courtRoomId = randomUUID().toString();
        judgeId = randomUUID().toString();
        hearingId = randomUUID().toString();
        pastSittingStartDate = LocalDateTime.now().minusDays(1).toString();
        pastSittingEndDate = LocalDateTime.now().minusDays(1).plusHours(2).toString();
        currentSittingStartDate = LocalDateTime.now().toString();
        currentSittingEndDate = LocalDateTime.now().toString();
        futureSittingStartDate = LocalDateTime.now().plusDays(1).toString();
        futureSittingEndDate = LocalDateTime.now().plusDays(1).plusHours(2).toString();

        datePlus7 = LocalDate.now().plusDays(7).toString();
        datePlus13 = LocalDate.now().plusDays(13).toString();
        datePlus14 = LocalDate.now().plusDays(14).toString();
        datePlus21 = LocalDate.now().plusDays(21).toString();
        datePlus42 = LocalDate.now().plusDays(42).toString();
        datePlus49 = LocalDate.now().plusDays(49).toString();
        datePlus43 = LocalDate.now().plusDays(43).toString();
        datePlus50 = LocalDate.now().plusDays(50).toString();

        courtRoomDto = CourtRoomDto.builder()
            .courtRoomId(123)
            .courtRoomName("CourtRoom 01")
            .build();

        addressDto = AddressDto.builder()
            .address1("53")
            .address2("Court Street")
            .address3("London")
            .postalCode("CB4 3MX")
            .country("UK")
            .build();
        lenient().when(prosectionCaseService.getCaseStatus(
            accessToken,
            caseUrn
        )).thenReturn(ProsecutionCaseDTO.builder().caseStatus(
            CaseStatus.ACTIVE).build());
        when(oauthTokenService.getJwtToken(eq(AmpApiType.SLC))).thenReturn(accessToken);
        when(oauthTokenService.getJwtToken(eq(AmpApiType.RCC))).thenReturn(accessToken);
        when(oauthTokenService.getJwtToken(eq(AmpApiType.PCD))).thenReturn(accessToken);
    }

    @Test
    @DisplayName("includes hearings when sitting date is in the future")
    void testGetCaseDetailsByCaseUrnWithValidHearingScheduleDetailsForFutureSittingDate() {
        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), futureCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));
        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        var caseHearingDetails = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(hearingId, caseHearingDetails.getHearingId());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingType());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingDescription());
        assertEquals("Note1", caseHearingDetails.getListNote());

        var schedule = caseHearingDetails.getCourtSittings().getFirst();
        assertEquals(judgeId, schedule.getJudiciaryId());
        assertEquals(courtHouseId, schedule.getCourtHouse().getCourtHouseId());
        assertEquals(courtRoomId, schedule.getCourtHouse().getCourtRoomId());
        assertEquals(futureSittingStartDate, schedule.getSittingStart());
        assertEquals(futureSittingEndDate, schedule.getSittingEnd());

        assertEquals("Lavender Hill", schedule.getCourtHouse().getCourtHouseName());
        assertEquals("53", schedule.getCourtHouse().getAddress().getAddress1());
        assertEquals("Court Street", schedule.getCourtHouse().getAddress().getAddress2());
        assertEquals("London", schedule.getCourtHouse().getAddress().getAddress3());

        assertEquals(123, schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomId());
        assertEquals("CourtRoom 01", schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomName());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    @DisplayName("excludes hearings when sittings are in the past")
    void testGetCaseDetailsByCaseUrnWithEmptyHearingScheduleDetailsForPastSittingDate() {
        final List<CourtSittingDto> pastCourtSittings = List.of(createCourtSitting(
            pastSittingStartDate,
            pastSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.SENTENCE.getValue(), pastCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(0, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    @DisplayName("includes only hearings that have at least one future sitting")
    void testGetCaseDetailsByCaseUrnWithValidHearingScheduleFutureAndPastSittingDateCombined() {
        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL_BACKER.getValue(), futureCourtSittings);

        List<CourtSittingDto> pastCourtSittings = List.of(createCourtSitting(pastSittingStartDate, pastSittingEndDate));
        final HearingDto hearingDto1 = createHearing(HearingType.TRIAL.getValue(), pastCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));
        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        var caseHearingDetails = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(hearingId, caseHearingDetails.getHearingId());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingType());
        assertEquals(HearingType.TRIAL_BACKER.getValue(), caseHearingDetails.getHearingDescription());
        assertEquals("Note1", caseHearingDetails.getListNote());

        var schedule = caseHearingDetails.getCourtSittings().getFirst();
        assertEquals(judgeId, schedule.getJudiciaryId());
        assertEquals(courtHouseId, schedule.getCourtHouse().getCourtHouseId());
        assertEquals(courtRoomId, schedule.getCourtHouse().getCourtRoomId());
        assertEquals(futureSittingStartDate, schedule.getSittingStart());
        assertEquals(futureSittingEndDate, schedule.getSittingEnd());

        assertEquals("53", schedule.getCourtHouse().getAddress().getAddress1());
        assertEquals("Court Street", schedule.getCourtHouse().getAddress().getAddress2());
        assertEquals("London", schedule.getCourtHouse().getAddress().getAddress3());

        assertEquals(123, schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomId());
        assertEquals("CourtRoom 01", schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomName());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    @DisplayName("includes hearings when sitting date is current date")
    void testGetCaseDetailsByCaseUrnWithValidHearingScheduleDetailsForCurrentDate() {
        final List<CourtSittingDto> currentCourtSittings = List.of(createCourtSitting(
            currentSittingStartDate,
            currentSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), currentCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        var caseHearingDetails = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(hearingId, caseHearingDetails.getHearingId());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingType());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingDescription());
        assertEquals("Note1", caseHearingDetails.getListNote());

        var schedule = caseHearingDetails.getCourtSittings().getFirst();
        assertEquals(judgeId, schedule.getJudiciaryId());
        assertEquals(courtHouseId, schedule.getCourtHouse().getCourtHouseId());
        assertEquals(courtRoomId, schedule.getCourtHouse().getCourtRoomId());
        assertEquals(currentSittingStartDate, schedule.getSittingStart());
        assertEquals(currentSittingEndDate, schedule.getSittingEnd());

        assertEquals("53", schedule.getCourtHouse().getAddress().getAddress1());
        assertEquals("Court Street", schedule.getCourtHouse().getAddress().getAddress2());
        assertEquals("London", schedule.getCourtHouse().getAddress().getAddress3());

        assertEquals(123, schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomId());
        assertEquals("CourtRoom 01", schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomName());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes hearings when sitting date is current date")
    void testGetCaseDetailsByCaseUrnWithValidHearingScheduleDetailsForCurrentDateAndOtherTrialAndSentenceTypes() {
        final List<CourtSittingDto> currentCourtSittings = List.of(createCourtSitting(
            currentSittingStartDate,
            currentSittingEndDate
        ));
        final HearingDto hearingDto1 = createHearing(HearingType.TRIAL_BACKER.getValue(), currentCourtSittings);
        final HearingDto hearingDto2 = createHearing(
            HearingType.SENTENCE_OFFICER_TO_ATTEND.getValue(),
            currentCourtSittings
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto1, hearingDto2))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        var caseHearingDetails = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(hearingId, caseHearingDetails.getHearingId());
        assertEquals(HearingType.TRIAL.getValue(), caseHearingDetails.getHearingType());
        assertEquals(HearingType.TRIAL_BACKER.getValue(), caseHearingDetails.getHearingDescription());
        assertEquals("Note1", caseHearingDetails.getListNote());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes hearings when sitting date is today, but before now")
    void testGetCaseDetailsByCaseUrnWithValidHearingScheduleDetailsForCurrentDateButBeforeCurrentTime() {
        final String todayStartDate = LocalDateTime.now().minusHours(2).toString();
        final String todayEndDate = LocalDateTime.now().minusHours(1).toString();

        final List<CourtSittingDto> courtSittings = List.of(createCourtSitting(todayStartDate, todayEndDate));
        final HearingDto hearingDto = createHearing(HearingType.SENTENCE.getValue(), courtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        var caseHearingDetails = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(hearingId, caseHearingDetails.getHearingId());
        assertEquals(HearingType.SENTENCE.getValue(), caseHearingDetails.getHearingType());
        assertEquals(HearingType.SENTENCE.getValue(), caseHearingDetails.getHearingDescription());
        assertEquals("Note1", caseHearingDetails.getListNote());

        var schedule = caseHearingDetails.getCourtSittings().getFirst();
        assertEquals(judgeId, schedule.getJudiciaryId());
        assertEquals(courtHouseId, schedule.getCourtHouse().getCourtHouseId());
        assertEquals(courtRoomId, schedule.getCourtHouse().getCourtRoomId());
        assertEquals(todayStartDate, schedule.getSittingStart());
        assertEquals(todayEndDate, schedule.getSittingEnd());

        assertEquals("53", schedule.getCourtHouse().getAddress().getAddress1());
        assertEquals("Court Street", schedule.getCourtHouse().getAddress().getAddress2());
        assertEquals("London", schedule.getCourtHouse().getAddress().getAddress3());

        assertEquals(123, schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomId());
        assertEquals("CourtRoom 01", schedule.getCourtHouse().getCourtRooms().getFirst().getCourtRoomName());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes only hearings that have at least one future sitting")
    void testGetCaseDetailsByCaseUrnWithValidMultipleHearingSchedule() {
        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), futureCourtSittings);

        List<CourtSittingDto> currentCourtSittings = List.of(createCourtSitting(
            currentSittingStartDate,
            currentSittingEndDate
        ));
        final HearingDto hearingDto1 = createHearing(HearingType.SENTENCE.getValue(), currentCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.SENTENCE.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("should not includes  hearings that have only past sitting")
    void testGetCaseDetailsByCaseUrnWithNoHearingScheduleWithMultipleHearingWithPastSittings() {
        final List<CourtSittingDto> pastCourtSittings = List.of(createCourtSitting(
            pastSittingStartDate,
            pastSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), pastCourtSittings);
        final HearingDto hearingDto1 = createHearing(HearingType.SENTENCE.getValue(), pastCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(0, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("should  includes  hearings that have only future sitting and should not include hearing that has past sitting")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleWithMultipleHearingWithPastAndFutureHearing() {
        final List<CourtSittingDto> pastCourtSittings = List.of(createCourtSitting(
            pastSittingStartDate,
            pastSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), pastCourtSittings);

        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto1 = createHearing(HearingType.TRIAL_NO_WITNESSES.getValue(), futureCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.TRIAL_NO_WITNESSES.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingDescription()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes only hearings that multiday hearing with current and future date sittings")
    void testGetCaseDetailsByCaseUrnWithValidMultiDayHearingScheduleWithCurrentAndFutureSitting() {
        final CourtSittingDto futureSittingDto = createCourtSitting(futureSittingStartDate, futureSittingEndDate);
        final CourtSittingDto currentSittingDto = createCourtSitting(currentSittingStartDate, currentSittingEndDate);

        final List<CourtSittingDto> courtSittings = List.of(futureSittingDto, currentSittingDto);
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), courtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(2, caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getCourtSittings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes only hearings that multiday hearing with future date sittings")
    void testGetCaseDetailsByCaseUrnWithValidMultiDayHearingScheduleWithPastAndFutureSitting() {
        final CourtSittingDto futureSittingDto = createCourtSitting(futureSittingStartDate, futureSittingEndDate);
        final List<CourtSittingDto> futureCourtSittings = List.of(futureSittingDto, futureSittingDto);
        final HearingDto hearingDto = createHearing(
            HearingType.SENTENCE_OFFICER_TO_ATTEND.getValue(),
            futureCourtSittings
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(2, caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getCourtSittings().size());

        assertEquals(
            HearingType.SENTENCE.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );
        assertEquals(
            HearingType.SENTENCE_OFFICER_TO_ATTEND.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingDescription()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes only hearings that multiday hearing with past date sittings")
    void testGetCaseDetailsByCaseUrnWithNoHearingScheduleWithPastMultiDaySitting() {
        final CourtSittingDto pastSittingDto = createCourtSitting(pastSittingStartDate, pastSittingEndDate);

        final List<CourtSittingDto> pastCourtSittings = List.of(pastSittingDto, pastSittingDto);
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), pastCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(0, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes only hearings that multiday hearing with past and future date sittings")
    void testGetCaseDetailsByCaseUrnWithMultiDayHearingScheduleWithPastAndFutureSitting() {
        final CourtSittingDto pastSittingDto = createCourtSitting(pastSittingStartDate, pastSittingEndDate);
        final CourtSittingDto futureSittingDto = createCourtSitting(futureSittingStartDate, futureSittingEndDate);

        final List<CourtSittingDto> courtSittings = List.of(pastSittingDto, futureSittingDto);
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), courtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(2, caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getCourtSittings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    @DisplayName("includes only hearings that have at least one future sitting")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleWithMultipleHearingOneWithSingleDayAndAnotherWithMultiDay() {
        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), futureCourtSittings);


        final List<CourtSittingDto> pastAndFutureCourtSittings = List.of(
            createCourtSitting(pastSittingStartDate, pastSittingEndDate),
            createCourtSitting(futureSittingStartDate, futureSittingEndDate)
        );
        final HearingDto hearingDto1 = createHearing(HearingType.SENTENCE.getValue(), pastAndFutureCourtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.SENTENCE.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes hearing of types Trial or SENTENCE")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleWithHearingTypeSentenceOrTrial() {
        final CourtSittingDto futureSittingDto = createCourtSitting(futureSittingStartDate, futureSittingEndDate);
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), List.of(futureSittingDto));
        final HearingDto hearingDto1 = createHearing(HearingType.SENTENCE.getValue(), List.of(futureSittingDto));
        final HearingDto hearingDto2 = createHearing("Invalid Type", List.of(futureSittingDto));

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1, hearingDto2))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.TRIAL.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    @DisplayName("includes hearing of types Trial or SENTENCE")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleInSortedOrderOfSittingDate() {
        final CourtSittingDto futureSittingDto1 = createCourtSitting(futureSittingStartDate, futureSittingEndDate);
        final List<CourtSittingDto> courtSittings1 = List.of(futureSittingDto1);
        final HearingDto hearingDto = createHearing(HearingType.TRIAL.getValue(), courtSittings1);

        final CourtSittingDto futureSittingDto2 = createCourtSitting(
            LocalDateTime.now().toString(),
            LocalDateTime.now().plusHours(2).toString()
        );
        final List<CourtSittingDto> courtSittings2 = List.of(futureSittingDto2);
        final HearingDto hearingDto1 = createHearing(HearingType.SENTENCE.getValue(), courtSittings2);

        final HearingDto hearingDto2 = createHearing("Invalid Type", courtSittings1);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto, hearingDto1, hearingDto2))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.SENTENCE.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes hearing of types Trial or SENTENCE")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleInSortedOrderOfSittingDateAndHearingType() {
        final CourtSittingDto currentSittingDto = createCourtSitting(currentSittingStartDate, currentSittingEndDate);
        final List<CourtSittingDto> courtSittings = List.of(currentSittingDto);

        final HearingDto hearingDto1 = createHearing(
            "1",
            HearingType.SENTENCE_OFFICER_TO_ATTEND.getValue(),
            courtSittings
        );
        final HearingDto hearingDto2 = createHearing(
            "2",
            HearingType.SENTENCE_OFFICER_TO_ATTEND.getValue(),
            courtSittings
        );
        final HearingDto hearingDto3 = createHearing("3", HearingType.TRIAL_RESERVE.getValue(), courtSittings);
        final HearingDto hearingDto4 = createHearing("4", HearingType.TRIAL_BACKER.getValue(), courtSittings);
        final HearingDto hearingDto5 = createHearing("Invalid Type", courtSittings);

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto1, hearingDto3, hearingDto2, hearingDto4, hearingDto5))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        List<CaseDetailsHearingDto> hearings = caseDetails.getCourtSchedules().getFirst().getHearings();
        assertEquals(1, hearings.size());

        assertEquals(HearingType.TRIAL.getValue(), hearings.getFirst().getHearingType());
        assertEquals(HearingType.TRIAL_RESERVE.getValue(), hearings.getFirst().getHearingDescription());
        assertEquals("3", hearings.getFirst().getHearingId());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("includes hearing with weekCommencing dates sorted by sitting date")
    void testGetCaseDetailsByCaseUrnWithHearingScheduleInSortedOrderOfSittingDateWithWeeks() {
        final HearingDto hearingDto = createHearingWithWeeks(
            "0",
            HearingType.TRIAL.getValue(),
            datePlus7,
            datePlus13, 1
        );

        final HearingDto hearingDto1 = createHearingWithWeeks(
            "1",
            HearingType.SENTENCE.getValue(),
            datePlus21,
            datePlus21,
            1
        );

        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            "Invalid Type",
            datePlus42,
            datePlus49,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto2, hearingDto1, hearingDto))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), isNull())).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.TRIAL.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        // Verify weekCommencing fields are set
        final CaseDetailsHearingDto firstHearing = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals(datePlus7, firstHearing.getWeekCommencing().getStartDate());
        assertEquals(datePlus13, firstHearing.getWeekCommencing().getEndDate());
        assertEquals(1, firstHearing.getWeekCommencing().getDurationInWeeks());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testGetCaseDetailsByCaseUrnWhenCourtSittingAndWeeCommencingDateAreSame() {
        futureSittingStartDate = LocalDateTime.now().plusDays(7).toString();
        futureSittingEndDate = LocalDateTime.now().plusDays(7).plusHours(2).toString();

        final CourtSittingDto currentSittingDto = createCourtSitting(futureSittingStartDate, futureSittingEndDate);
        final List<CourtSittingDto> courtSittings = List.of(currentSittingDto);

        final HearingDto hearingDto1 = createHearing("1", HearingType.SENTENCE.getValue(), courtSittings);

        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            datePlus7,
            datePlus13,
            1
        );

        final HearingDto hearingDto3 = createHearingWithWeeks(
            "3",
            HearingType.SENTENCE.getValue(),
            datePlus21,
            datePlus21,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto3, hearingDto1, hearingDto2))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());
        assertEquals(
            HearingType.SENTENCE.getValue(),
            caseDetails.getCourtSchedules().getFirst().getHearings().getFirst().getHearingType()
        );

        final CaseDetailsHearingDto firstHearing = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals("1", firstHearing.getHearingId());
        assertNull(firstHearing.getWeekCommencing());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testExceptionalGetCaseDetailsByCaseUrnWithHearingWithWeeks() {
        final HearingDto hearingDto1 = createHearingWithWeeks(
            "1",
            HearingType.SENTENCE.getValue(),
            datePlus7,
            datePlus14,
            1
        );
        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            datePlus21,
            datePlus21,
            1
        );
        final HearingDto hearingDto3 = createHearingWithWeeks(
            "3",
            HearingType.TRIAL.getValue(),
            datePlus42,
            datePlus49,
            1
        );
        final HearingDto hearingDto4 = createHearingWithWeeks(
            "4",
            HearingType.TRIAL.getValue(),
            datePlus43,
            datePlus50,
            1
        );
        final HearingDto hearingDto5 = createHearingWithWeeks(
            hearingId,
            "Invalid Type",
            datePlus43,
            datePlus50,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto3, hearingDto2, hearingDto4, hearingDto1, hearingDto5))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), isNull())).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        List<CaseDetailsHearingDto> hearings = caseDetails.getCourtSchedules().getFirst().getHearings();
        assertEquals(1, hearings.size());

        assertEquals(HearingType.SENTENCE.getValue(), hearings.getFirst().getHearingType());
        assertEquals("1", hearings.getFirst().getHearingId());
        assertEquals(datePlus7, hearings.getFirst().getWeekCommencing().getStartDate());
        assertEquals(datePlus14, hearings.getFirst().getWeekCommencing().getEndDate());
        assertEquals(1, hearings.getFirst().getWeekCommencing().getDurationInWeeks());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testGetCaseDetailsByCaseUrnWhenValidSittingAndWeekDateIsNull() {
        final HearingDto hearingDto = createHearingWithWeeks(
            "0",
            HearingType.TRIAL.getValue(),
            Strings.EMPTY,
            Strings.EMPTY,
            1
        );

        final HearingDto hearingDto1 = createHearingWithWeeks(
            "1",
            HearingType.SENTENCE.getValue(),
            Strings.EMPTY,
            Strings.EMPTY,
            1
        );

        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            HearingType.TRIAL.getValue(),
            Strings.EMPTY,
            Strings.EMPTY,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto2, hearingDto1, hearingDto))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(0, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("weekCommencing hearing should come first when its start date is earlier than courtSitting date")
    void testWithWeekCommencingBeforeCourtSitting() {
        // Hearing with weekCommencing: startDate = today - 2 days, endDate = today + 5 days
        String weekCommencingStartDate = LocalDate.now().minusDays(2).toString();
        String weekCommencingEndDate = LocalDate.now().plusDays(5).toString();
        final HearingDto hearingWithWeekCommencing = createHearingWithWeeks(
            "1",
            HearingType.TRIAL.getValue(),
            weekCommencingStartDate,
            weekCommencingEndDate,
            1
        );

        // Hearing with courtSitting: sittingStart = today + 3 days
        String sittingStartDate = LocalDateTime.now().plusDays(3).toString();
        String sittingEndDate = LocalDateTime.now().plusDays(3).plusHours(2).toString();
        final CourtSittingDto courtSittingDto = createCourtSitting(sittingStartDate, sittingEndDate);
        final List<CourtSittingDto> courtSittings = List.of(courtSittingDto);
        final HearingDto hearingWithCourtSitting = createHearing(
            "2",
            HearingType.SENTENCE_AT_ANOTHER_COURT.getValue(),
            courtSittings
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingWithCourtSitting, hearingWithWeekCommencing))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        // Verify that the weekCommencing hearing comes first (because today - 2 days < today + 3 days)
        final CaseDetailsHearingDto firstHearing = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals("2", firstHearing.getHearingId());
        assertEquals(HearingType.SENTENCE.getValue(), firstHearing.getHearingType());
        assertEquals(HearingType.SENTENCE_AT_ANOTHER_COURT.getValue(), firstHearing.getHearingDescription());

        assertNull(firstHearing.getWeekCommencing());
        assertNotNull(firstHearing.getCourtSittings());
        assertEquals(1, firstHearing.getCourtSittings().size());
        assertEquals(sittingStartDate, firstHearing.getCourtSittings().getFirst().getSittingStart());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testWithWeekCommencingBeforeCourtSittingAndEndsOnSameDateWithMutiDayHearing() {
        String weekCommencingStartDate = LocalDate.now().minusDays(6).toString();
        String weekCommencingEndDate = LocalDate.now().plusDays(1).toString();
        final HearingDto hearingWithWeekCommencing = createHearingWithWeeks(
            "1",
            HearingType.TRIAL.getValue(),
            weekCommencingStartDate,
            weekCommencingEndDate,
            3
        );

        String sittingStartDate = LocalDateTime.now().plusDays(1).toString();
        String sittingEndDate = LocalDateTime.now().plusDays(1).plusHours(2).toString();
        final CourtSittingDto courtSittingDto = createCourtSitting(sittingStartDate, sittingEndDate);
        final List<CourtSittingDto> courtSittings = List.of(courtSittingDto);
        final HearingDto hearingWithCourtSitting = createHearing(
            "2",
            HearingType.TRIAL.getValue(),
            courtSittings
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingWithCourtSitting, hearingWithWeekCommencing))
            .build();


        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        // Verify that the weekCommencing hearing comes first (because today - 2 days < today + 3 days)
        final CaseDetailsHearingDto firstHearing = caseDetails.getCourtSchedules().getFirst().getHearings().getFirst();
        assertEquals("2", firstHearing.getHearingId());
        assertEquals(HearingType.TRIAL.getValue(), firstHearing.getHearingType());
        assertNull(firstHearing.getWeekCommencing());
        assertEquals(1, firstHearing.getCourtSittings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testGetCaseDetailsByCaseUrnWhenValidButEmptySittingAndWeekDateIsValid() {
        final HearingDto hearingDto1 = createHearingWithWeeks(
            "1",
            HearingType.SENTENCE.getValue(),
            datePlus7,
            datePlus14,
            1
        );
        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE_PROSECUTION_TO_ATTEND.getValue(),
            datePlus21,
            datePlus21,
            1
        );
        final HearingDto hearingDto3 = createHearingWithWeeks(
            "3",
            HearingType.TRIAL.getValue(),
            datePlus42,
            datePlus49,
            1
        );
        final HearingDto hearingDto4 = createHearingWithWeeks(
            "4",
            HearingType.TRIAL_FLOATER.getValue(),
            datePlus43,
            datePlus50,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto3, hearingDto1, hearingDto2, hearingDto4))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));
        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), isNull())).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        List<CaseDetailsHearingDto> hearings = caseDetails.getCourtSchedules().getFirst().getHearings();
        assertEquals(1, hearings.size());

        assertEquals(HearingType.SENTENCE.getValue(), hearings.getFirst().getHearingType());
        assertEquals(HearingType.SENTENCE.getValue(), hearings.getFirst().getHearingDescription());
        assertEquals("1", hearings.getFirst().getHearingId());
        assertEquals(datePlus7, hearings.getFirst().getWeekCommencing().getStartDate());
        assertEquals(datePlus14, hearings.getFirst().getWeekCommencing().getEndDate());
        assertEquals(1, hearings.getFirst().getWeekCommencing().getDurationInWeeks());
    }

    @Test
    void testGetCaseDetailsByCaseUrnWithHearingScheduleInSortedOrderOfSittingDateAndHearingTypeWithWeeks() {
        final List<CourtSittingDto> futureCourtSittings = List.of(createCourtSitting(
            futureSittingStartDate,
            futureSittingEndDate
        ));
        final HearingDto hearingDto = createHearing("0", HearingType.TRIAL.getValue(), futureCourtSittings);
        final List<CourtSittingDto> pastAndFutureCourtSittings = List.of(
            createCourtSitting(pastSittingStartDate, pastSittingEndDate),
            createCourtSitting(futureSittingStartDate, futureSittingEndDate)
        );
        final HearingDto hearingDto1 = createHearing("1", HearingType.SENTENCE.getValue(), pastAndFutureCourtSittings);

        final HearingDto hearingDto2 = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            datePlus21,
            datePlus21,
            1
        );
        final HearingDto hearingDto3 = createHearingWithWeeks(
            "3",
            HearingType.TRIAL.getValue(),
            datePlus42,
            datePlus49,
            1
        );
        final HearingDto hearingDto4 = createHearingWithWeeks(
            "4",
            HearingType.TRIAL.getValue(),
            datePlus43,
            datePlus50,
            1
        );
        final HearingDto hearingDto5 = createHearingWithWeeks(
            hearingId,
            "Invalid Type",
            datePlus43,
            datePlus50,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingDto3, hearingDto2, hearingDto, hearingDto1, hearingDto4, hearingDto5))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(accessToken, caseUrn)).thenReturn(List.of(scheduleDto));
        when(prosectionCaseService.getCaseStatus(
            accessToken,
            caseUrn
        )).thenReturn(ProsecutionCaseDTO.builder().caseStatus(CaseStatus.ACTIVE).build());

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        List<CaseDetailsHearingDto> hearings = caseDetails.getCourtSchedules().getFirst().getHearings();
        assertEquals(1, hearings.size());

        assertEquals(HearingType.SENTENCE.getValue(), hearings.getFirst().getHearingType());
        assertEquals("1", hearings.getFirst().getHearingId());
        assertNull(hearings.getFirst().getWeekCommencing());
        assertEquals(2, hearings.getFirst().getCourtSittings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    void testGetCaseDetailsByCaseUrnWhenSittingDateAndWeekCommencingDateAreAfter10Years() {
        final LocalDate now = LocalDate.now();
        final LocalDateTime in10Years = now.plusYears(10).plusDays(1).atStartOfDay();
        final LocalDateTime in1Years = now.plusYears(1).plusDays(1).atStartOfDay();

        String sittingStart10Years = in10Years.toString();
        String sittingEnd10Years = in10Years.plusHours(2).toString();
        String weekCommencingStart10Years = now.plusYears(10).plusDays(1).toString();
        String weekCommencingEnd10Years = now.plusYears(10).plusDays(7).toString();

        String sittingStart1Years = in1Years.toString();
        String sittingEnd1Years = in1Years.plusHours(2).toString();
        String weekCommencingStart5Years = now.plusYears(5).plusDays(1).toString();
        String weekCommencingEnd5Years = now.plusYears(5).plusDays(7).toString();

        final List<CourtSittingDto> courtSittings = List.of(
            createCourtSitting(sittingStart10Years, sittingEnd10Years)
        );
        final HearingDto hearingWithSitting = createHearing("1", HearingType.TRIAL.getValue(), courtSittings);
        final HearingDto hearingWithSitting1 = createHearing(
            "3", HearingType.TRIAL.getValue(), List.of(
                createCourtSitting(sittingStart1Years, sittingEnd1Years)
            )
        );
        final HearingDto hearingWithWeekCommencing = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            weekCommencingStart10Years,
            weekCommencingEnd10Years,
            1
        );
        final HearingDto hearingWithWeekCommencing1 = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            weekCommencingStart5Years,
            weekCommencingEnd5Years,
            1
        );


        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(
                hearingWithSitting,
                hearingWithWeekCommencing,
                hearingWithSitting1,
                hearingWithWeekCommencing1
            ))
            .build();

        when(courtHouseService.getCourtHouseById(eq(accessToken), eq(courtHouseId), eq(courtRoomId))).thenReturn(
            createCourtHouse(courtRoomDto, addressDto));

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(1, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        CaseDetailsHearingDto sittingHearing = caseDetails.getCourtSchedules().getFirst().getHearings().stream()
            .filter(h -> "3".equals(h.getHearingId()))
            .findFirst()
            .orElseThrow();
        assertEquals(HearingType.TRIAL.getValue(), sittingHearing.getHearingType());
        assertNull(sittingHearing.getWeekCommencing());
        assertNotNull(sittingHearing.getCourtSittings());
        assertEquals(1, sittingHearing.getCourtSittings().size());
        assertEquals(sittingStart1Years, sittingHearing.getCourtSittings().getFirst().getSittingStart());
        assertEquals(sittingEnd1Years, sittingHearing.getCourtSittings().getFirst().getSittingEnd());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }


    @Test
    void testGetCaseDetailsWithWeekCommencingDateAreAfter10Years() {
        final LocalDate now = LocalDate.now();
        final LocalDateTime in10Years = now.plusYears(10).plusDays(1).atStartOfDay();

        String sittingStart10Years = in10Years.toString();
        String sittingEnd10Years = in10Years.plusHours(2).toString();
        String weekCommencingStart10Years = now.plusYears(10).plusDays(1).toString();
        String weekCommencingEnd10Years = now.plusYears(10).plusDays(7).toString();

        final List<CourtSittingDto> courtSittings = List.of(
            createCourtSitting(sittingStart10Years, sittingEnd10Years)
        );
        final HearingDto hearingWithSitting = createHearing("1", HearingType.TRIAL.getValue(), courtSittings);

        final HearingDto hearingWithWeekCommencing = createHearingWithWeeks(
            "2",
            HearingType.SENTENCE.getValue(),
            weekCommencingStart10Years,
            weekCommencingEnd10Years,
            1
        );

        final CourtScheduleDto scheduleDto = CourtScheduleDto.builder()
            .hearings(List.of(hearingWithSitting, hearingWithWeekCommencing))
            .build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn))).thenReturn(List.of(
            scheduleDto));

        final CaseDetailsDto caseDetails = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertEquals(caseUrn, caseDetails.getCaseUrn());
        assertEquals(1, caseDetails.getCourtSchedules().size());
        assertEquals(0, caseDetails.getCourtSchedules().getFirst().getHearings().size());

        verify(trackMyCaseMetricsService).incrementCaseDetailsCount(caseUrn);
    }

    @Test
    @DisplayName("should evict token caches and succeed on retry after 401 Unauthorized")
    void shouldRetryAndSucceedOnUnauthorizedException() {
        CourtScheduleDto scheduleDto = CourtScheduleDto.builder().hearings(List.of()).build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn)))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
            .thenReturn(List.of(scheduleDto));

        CaseDetailsDto result = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertNotNull(result);
        assertEquals(caseUrn, result.getCaseUrn());
        verify(oauthTokenService).evictAllTokenCaches();
        verify(courtScheduleService, times(2)).getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn));
    }

    @Test
    @DisplayName("should evict token caches and succeed on retry after 403 Forbidden")
    void shouldRetryAndSucceedOnForbiddenException() {
        CourtScheduleDto scheduleDto = CourtScheduleDto.builder().hearings(List.of()).build();

        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn)))
            .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
            .thenReturn(List.of(scheduleDto));

        CaseDetailsDto result = caseDetailsService.getCaseDetailsByCaseUrn(caseUrn);

        assertNotNull(result);
        assertEquals(caseUrn, result.getCaseUrn());
        verify(oauthTokenService).evictAllTokenCaches();
        verify(courtScheduleService, times(2)).getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn));
    }

    @Test
    @DisplayName("should propagate immediately without retry for non-auth HTTP errors")
    void shouldNotRetryOnNonAuthHttpErrors() {
        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> caseDetailsService.getCaseDetailsByCaseUrn(caseUrn))
            .isInstanceOf(HttpClientErrorException.class);

        verify(oauthTokenService, never()).evictAllTokenCaches();
        verify(courtScheduleService, times(1)).getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn));
    }

    @Test
    @DisplayName("should propagate exception if retry also fails with 401")
    void shouldPropagateExceptionIfRetryAlsoFails() {
        when(courtScheduleService.getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn)))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> caseDetailsService.getCaseDetailsByCaseUrn(caseUrn))
            .isInstanceOf(HttpClientErrorException.class);

        verify(oauthTokenService, times(1)).evictAllTokenCaches();
        verify(courtScheduleService, times(2)).getCourtScheduleByCaseUrn(eq(accessToken), eq(caseUrn));
    }

    private CourtSittingDto createCourtSitting(final String sittingStartDate, final String sittingEndDate) {
        return CourtSittingDto.builder()
            .sittingStart(sittingStartDate)
            .sittingEnd(sittingEndDate)
            .judiciaryId(judgeId)
            .courtHouse(courtHouseId)
            .courtRoom(courtRoomId)
            .build();
    }

    private CourtHouseDto createCourtHouse(final CourtRoomDto courtRoomDto, final AddressDto addressDto) {
        return CourtHouseDto.builder()
            .courtHouseId(courtHouseId)
            .courtRoomId(courtRoomId)
            .courtHouseType("CROWN")
            .courtHouseCode("123")
            .courtHouseName("Lavender Hill")
            .address(addressDto)
            .courtRooms(Arrays.asList(courtRoomDto))
            .build();
    }

    private HearingDto createHearing(String hearingType, List<CourtSittingDto> courtSittings) {
        return createHearing(hearingId, hearingType, courtSittings);
    }

    private HearingDto createHearing(String hearingId, String hearingType, List<CourtSittingDto> courtSittings) {
        return HearingDto.builder()
            .hearingId(hearingId)
            .hearingType(hearingType)
            .hearingDescription(hearingType)
            .listNote("Note1")
            .courtSittings(courtSittings)
            .build();
    }

    private HearingDto createHearingWithWeeks(String hearingId, String hearingType,
                                              String startDate, String endDate,
                                              int durationInWeeks) {

        final WeekCommencingDto weekCommencingDto = WeekCommencingDto.builder()
            .courtHouse(courtHouseId)
            .startDate(startDate)
            .endDate(endDate)
            .durationInWeeks(durationInWeeks)
            .build();

        return HearingDto.builder()
            .hearingId(hearingId)
            .hearingType(hearingType)
            .hearingDescription(hearingType)
            .listNote("Note1")
            .weekCommencing(weekCommencingDto)
            .build();
    }
}
