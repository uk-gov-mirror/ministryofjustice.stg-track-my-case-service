package uk.gov.moj.cp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ResponseFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private HttpServletRequest request;

    private HttpServletResponse response;

    @Mock
    private Object handler;

    private RateLimitInterceptor interceptor;

    private static final String SESSION_ID = "session-abc-123";
    private static final String AUTH_HEADER = "Bearer valid-token-456";
    private static final String REMOTE_ADDR = "1.1.1.1";

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(100, 100, 1);
        response = mock(ResponseFacade.class);
    }

    @Test
    @DisplayName("Should allow request within rate limit when X-Session-Id header is present")
    void testPreHandle_WithSessionId_WithinLimit_ShouldAllow() throws Exception {
        // Given
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When
        boolean result = interceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request within rate limit when Authorization header is present and no X-Session-Id")
    void testPreHandle_WithAuthHeader_NoSessionId_WithinLimit_ShouldAllow() throws Exception {
        // Given
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(AUTH_HEADER);

        // When
        boolean result = interceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow request within rate limit when falling back to remoteAddr")
    void testPreHandle_NoHeaders_FallbackToRemoteAddr_WithinLimit_ShouldAllow() throws Exception {
        // Given
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(REMOTE_ADDR);

        // When
        boolean result = interceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false and set 429 status when rate limit is exceeded")
    void testPreHandle_ExceedingRateLimit_ShouldReturnFalseAndSet429() throws Exception {
        // Given - capacity of 1 to easily trigger the limit
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When - first request exhausts the bucket, second exceeds limit
        limitedInterceptor.preHandle(request, response, handler);
        boolean result = limitedInterceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Should set X-Rate-Limit-Retry-After-Seconds header when rate limit is exceeded")
    void testPreHandle_ExceedingRateLimit_ShouldSetRetryAfterHeader() throws Exception {
        // Given
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When
        limitedInterceptor.preHandle(request, response, handler);
        limitedInterceptor.preHandle(request, response, handler);

        // Then
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("X-Rate-Limit-Retry-After-Seconds"), headerValueCaptor.capture());
        assertThat(Long.parseLong(headerValueCaptor.getValue())).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Should call sendError with 429 and correct message when rate limit is exceeded")
    void testPreHandle_ExceedingRateLimit_ShouldCallSendError() throws Exception {
        // Given
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When
        limitedInterceptor.preHandle(request, response, handler);
        limitedInterceptor.preHandle(request, response, handler);

        // Then
        verify(response).sendError(eq(429), eq("Too many requests - please retry after the Retry-After period"));
    }

    @Test
    @DisplayName("Should set content type to application/json when rate limit exceeded")
    void testPreHandle_ExceedingRateLimit_ShouldSetJsonContentType() throws Exception {
        // Given
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When
        limitedInterceptor.preHandle(request, response, handler);
        limitedInterceptor.preHandle(request, response, handler);

        // Then
        verify(response).setContentType("application/json");
    }

    @Test
    @DisplayName("Should give independent buckets to different session IDs")
    void testPreHandle_DifferentSessionIds_HaveIndependentBuckets() throws Exception {
        // Given
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        when(request.getHeader("X-Session-Id")).thenReturn("session-1");
        when(request2.getHeader("X-Session-Id")).thenReturn("session-2");

        // When - session-1 exhausts its bucket
        limitedInterceptor.preHandle(request, response, handler);
        boolean result = limitedInterceptor.preHandle(request2, response, handler);

        // Then - session-2 is unaffected by session-1's usage
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should share bucket between requests with the same session ID")
    void testPreHandle_SameSessionId_SharesBucket() throws Exception {
        // Given - capacity of 2
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(2, 2, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(SESSION_ID);

        // When
        boolean first = limitedInterceptor.preHandle(request, response, handler);
        boolean second = limitedInterceptor.preHandle(request, response, handler);
        boolean third = limitedInterceptor.preHandle(request, response, handler);

        // Then - first two succeed, third blocked
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
    }

    @Test
    @DisplayName("Should use Authorization token part as rate limit key when X-Session-Id is absent")
    void testPreHandle_AuthorizationHeader_UsedAsRateLimitKey_WhenNoSessionId() throws Exception {
        // Given
        RateLimitInterceptor limitedInterceptor = new RateLimitInterceptor(1, 1, 1);
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(AUTH_HEADER);

        // When - same auth token shares same bucket
        limitedInterceptor.preHandle(request, response, handler);
        boolean result = limitedInterceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should fall back to Authorization header when X-Session-Id is blank")
    void testPreHandle_BlankSessionId_FallsBackToAuthorizationHeader() throws Exception {
        // Given
        when(request.getHeader("X-Session-Id")).thenReturn("   ");
        when(request.getHeader("Authorization")).thenReturn(AUTH_HEADER);

        // When
        boolean result = interceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should fall back to remoteAddr when both X-Session-Id and Authorization are absent")
    void testPreHandle_NoSessionIdNoAuthHeader_FallsBackToRemoteAddr() throws Exception {
        // Given
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(REMOTE_ADDR);

        // When
        boolean result = interceptor.preHandle(request, response, handler);

        // Then
        assertThat(result).isTrue();
        verify(request).getRemoteAddr();
    }
}
