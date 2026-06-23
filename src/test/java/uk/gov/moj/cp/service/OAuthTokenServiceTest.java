package uk.gov.moj.cp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.moj.cp.client.oauth.OAuthTokenClient;
import uk.gov.moj.cp.model.OAuthTokenResponse;
import uk.gov.moj.cp.model.AmpApiType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthTokenServiceTest {

    @Mock
    private OAuthTokenClient oauthTokenClient;

    @InjectMocks
    private OAuthTokenService oauthTokenService;

    private static final OAuthTokenResponse TOKEN_RESPONSE =
        new OAuthTokenResponse("Bearer", 3600, 3600, "access-token-value");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(oauthTokenService, "tokenCacheTtlMinutes", 1440L);
    }

    @Test
    @DisplayName("Should fetch and return token from client on first call")
    void shouldFetchAndReturnTokenFromClientOnFirstCall() {
        when(oauthTokenClient.getJwtToken(eq(AmpApiType.SLC))).thenReturn(TOKEN_RESPONSE);

        String accessToken = oauthTokenService.getJwtToken(AmpApiType.SLC);

        assertThat(accessToken).isEqualTo("access-token-value");
        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.SLC);
    }

    @Test
    @DisplayName("Should return cached token without calling client again")
    void shouldReturnCachedTokenWithoutCallingClientAgain() {
        when(oauthTokenClient.getJwtToken(AmpApiType.SLC)).thenReturn(TOKEN_RESPONSE);

        oauthTokenService.getJwtToken(AmpApiType.SLC);
        String result = oauthTokenService.getJwtToken(AmpApiType.SLC);

        assertThat(result).isEqualTo("access-token-value");
        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.SLC);
    }

    @Test
    @DisplayName("Should fetch new token from client when cached token is expired")
    void shouldFetchNewTokenWhenCacheIsExpired() {
        ReflectionTestUtils.setField(oauthTokenService, "tokenCacheTtlMinutes", -1L);
        when(oauthTokenClient.getJwtToken(AmpApiType.SLC)).thenReturn(TOKEN_RESPONSE);

        oauthTokenService.getJwtToken(AmpApiType.SLC);
        oauthTokenService.getJwtToken(AmpApiType.SLC);

        verify(oauthTokenClient, times(2)).getJwtToken(AmpApiType.SLC);
    }

    @Test
    @DisplayName("Should fetch new token from client after cache eviction for all API types")
    void shouldFetchNewTokenAfterCacheEviction() {
        when(oauthTokenClient.getJwtToken(eq(AmpApiType.SLC))).thenReturn(TOKEN_RESPONSE);
        when(oauthTokenClient.getJwtToken(eq(AmpApiType.RCC))).thenReturn(TOKEN_RESPONSE);
        when(oauthTokenClient.getJwtToken(eq(AmpApiType.PCD))).thenReturn(TOKEN_RESPONSE);

        oauthTokenService.getJwtToken(AmpApiType.SLC);
        oauthTokenService.getJwtToken(AmpApiType.RCC);
        oauthTokenService.getJwtToken(AmpApiType.PCD);

        oauthTokenService.evictAllTokenCaches();

        oauthTokenService.getJwtToken(AmpApiType.SLC);
        oauthTokenService.getJwtToken(AmpApiType.RCC);
        oauthTokenService.getJwtToken(AmpApiType.PCD);

        verify(oauthTokenClient, times(2)).getJwtToken(AmpApiType.SLC);
        verify(oauthTokenClient, times(2)).getJwtToken(AmpApiType.RCC);
        verify(oauthTokenClient, times(2)).getJwtToken(AmpApiType.PCD);
    }

    @Test
    @DisplayName("Should cache tokens independently per API type")
    void shouldCacheTokensIndependentlyPerApiType() {
        when(oauthTokenClient.getJwtToken(AmpApiType.SLC))
            .thenReturn(new OAuthTokenResponse("Bearer", 3600, 3600, "slc-token"));
        when(oauthTokenClient.getJwtToken(AmpApiType.RCC))
            .thenReturn(new OAuthTokenResponse("Bearer", 3600, 3600, "rcc-token"));
        when(oauthTokenClient.getJwtToken(AmpApiType.PCD))
            .thenReturn(new OAuthTokenResponse("Bearer", 3600, 3600, "pcd-token"));

        assertThat(oauthTokenService.getJwtToken(AmpApiType.SLC)).isEqualTo("slc-token");
        assertThat(oauthTokenService.getJwtToken(AmpApiType.RCC)).isEqualTo("rcc-token");
        assertThat(oauthTokenService.getJwtToken(AmpApiType.PCD)).isEqualTo("pcd-token");

        // Second calls should hit the cache
        assertThat(oauthTokenService.getJwtToken(AmpApiType.SLC)).isEqualTo("slc-token");
        assertThat(oauthTokenService.getJwtToken(AmpApiType.RCC)).isEqualTo("rcc-token");
        assertThat(oauthTokenService.getJwtToken(AmpApiType.PCD)).isEqualTo("pcd-token");

        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.SLC);
        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.RCC);
        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.PCD);
    }

    @Test
    @DisplayName("Should propagate exception from token client")
    void shouldPropagateExceptionFromTokenClient() {
        when(oauthTokenClient.getJwtToken(eq(AmpApiType.PCD))).thenThrow(new RuntimeException("accessToken retrieval failed"));

        assertThatThrownBy(() -> oauthTokenService.getJwtToken(AmpApiType.PCD))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("accessToken retrieval failed");

        verify(oauthTokenClient, times(1)).getJwtToken(eq(AmpApiType.PCD));
    }

    @Test
    @DisplayName("Should evict empty cache without throwing an exception")
    void shouldEvictEmptyCacheWithoutException() {
        oauthTokenService.evictAllTokenCaches();

        when(oauthTokenClient.getJwtToken(AmpApiType.SLC)).thenReturn(TOKEN_RESPONSE);
        String token = oauthTokenService.getJwtToken(AmpApiType.SLC);

        assertThat(token).isEqualTo("access-token-value");
        verify(oauthTokenClient, times(1)).getJwtToken(AmpApiType.SLC);
    }
}
