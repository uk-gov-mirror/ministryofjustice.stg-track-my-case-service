package uk.gov.moj.cp.service;

import com.moj.generated.hmcts.Address;
import com.moj.generated.hmcts.CourtHouse;
import com.moj.generated.hmcts.CourtHouse.CourtHouseType;
import com.moj.generated.hmcts.CourtRoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.moj.cp.client.api.CourtHouseAPIClient;
import uk.gov.moj.cp.dto.outbound.CourtHouseDto;

import java.util.List;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtHouseServiceTest {

    @Mock
    private CourtHouseAPIClient courtHouseAPIClient;

    @InjectMocks
    private CourtHouseService courtHouseService;
    private final String accessToken = "testToken";

    @Test
    void testGetCourtHouseByCourtHouseById_successfulCourtHouseDetails() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();

        final Address address = new Address(
            "1 High Street",
            "Court Road",
            "London",
            null,
            "AA1 2BB",
            "UK"
        );

        final CourtHouse courtHouse = new CourtHouse(
            CourtHouseType.CROWN,
            "CHC123",
            "Lavender Hill",
            address,
            List.of(new CourtRoom(10, "CourtRoom 10"), new CourtRoom(20, "CourtRoom 20"))
        );

        final ResponseEntity<CourtHouse> entity = ResponseEntity.ok(courtHouse);
        when(courtHouseAPIClient.getCourtHouseById(accessToken, courtHouseId, courtRoomId)).thenReturn(entity);

        CourtHouseDto dto = courtHouseService.getCourtHouseById(accessToken, courtHouseId, courtRoomId);

        assertNotNull(dto);
        assertEquals(courtHouseId, dto.getCourtHouseId());
        assertEquals(courtRoomId, dto.getCourtRoomId());
        assertEquals("crown", dto.getCourtHouseType());
        assertEquals("CHC123", dto.getCourtHouseCode());
        assertEquals("Lavender Hill", dto.getCourtHouseName());

        assertNotNull(dto.getAddress());
        assertEquals("1 High Street", dto.getAddress().getAddress1());
        assertEquals("Court Road", dto.getAddress().getAddress2());
        assertEquals("London", dto.getAddress().getAddress3());
        assertNull(dto.getAddress().getAddress4());
        assertEquals("AA1 2BB", dto.getAddress().getPostalCode());
        assertEquals("UK", dto.getAddress().getCountry());

        assertNotNull(dto.getCourtRooms());
        assertEquals(2, dto.getCourtRooms().size());
        assertEquals(10, dto.getCourtRooms().getFirst().getCourtRoomId());
        assertEquals("CourtRoom 10", dto.getCourtRooms().getFirst().getCourtRoomName());
        assertEquals(20, dto.getCourtRooms().get(1).getCourtRoomId());
        assertEquals("CourtRoom 20", dto.getCourtRooms().get(1).getCourtRoomName());
    }

    @Test
    void testGetCourtHouseByCourtHouseById_returnsNull() {
        when(courtHouseAPIClient.getCourtHouseById(eq(accessToken), eq("courtId"), eq("courtRoomId")))
            .thenReturn(ResponseEntity.status(200).build());

        CourtHouseDto result = courtHouseService.getCourtHouseById(accessToken, "courtId", "courtRoomId");

        assertNull(result);
    }

}


