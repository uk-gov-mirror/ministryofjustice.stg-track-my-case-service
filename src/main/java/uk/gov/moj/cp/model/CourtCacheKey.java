package uk.gov.moj.cp.model;

import java.util.Objects;

public final class CourtCacheKey {

    private final String courtHouseId;
    private final String courtRoomId;

    public CourtCacheKey(final String courtHouseId, final String courtRoomId) {
        this.courtHouseId = courtHouseId;
        this.courtRoomId = courtRoomId;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CourtCacheKey courtCacheKey)) {
            return false;
        }
        return Objects.equals(courtHouseId, courtCacheKey.courtHouseId)
            && Objects.equals(courtRoomId, courtCacheKey.courtRoomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtHouseId, courtRoomId);
    }
}
