package uk.gov.moj.cp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SecurityConfigTest {

    private static final String usersAuthorizationHeader = "valid-token-123";
    private static final int rateLimitMaximumTokensCapacity = 100;
    private static final int rateLimitRefillTokens = 60;
    private static final int rateLimitRefillPeriodMinutes = 1;

    @Test
    @DisplayName("Should register RateLimitInterceptor when addInterceptors is called")
    void testAddInterceptors_ShouldRegisterRateLimitInterceptor() {
        // Given
        SecurityConfig config = new SecurityConfig(
            usersAuthorizationHeader,
            rateLimitMaximumTokensCapacity,
            rateLimitRefillTokens,
            rateLimitRefillPeriodMinutes
        );
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class, RETURNS_DEEP_STUBS);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);

        // When
        config.addInterceptors(registry);

        // Then
        ArgumentCaptor<HandlerInterceptor> captor = ArgumentCaptor.forClass(HandlerInterceptor.class);
        verify(registry, times(2)).addInterceptor(captor.capture());
        List<HandlerInterceptor> interceptors = captor.getAllValues();
        assertThat(interceptors).hasAtLeastOneElementOfType(RateLimitInterceptor.class);
    }

    @Test
    @DisplayName("Should register UsersAuthorizationInterceptor when addInterceptors is called")
    void testAddInterceptors_ShouldRegisterUsersAuthorizationInterceptor() {
        // Given
        SecurityConfig config = new SecurityConfig(
            usersAuthorizationHeader,
            rateLimitMaximumTokensCapacity,
            rateLimitRefillTokens,
            rateLimitRefillPeriodMinutes
        );
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class, RETURNS_DEEP_STUBS);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);

        // When
        config.addInterceptors(registry);

        // Then
        ArgumentCaptor<HandlerInterceptor> captor = ArgumentCaptor.forClass(HandlerInterceptor.class);
        verify(registry, times(2)).addInterceptor(captor.capture());
        List<HandlerInterceptor> interceptors = captor.getAllValues();
        assertThat(interceptors).hasAtLeastOneElementOfType(UsersAuthorizationInterceptor.class);
    }

    @Test
    @DisplayName("Should register exactly two interceptors when addInterceptors is called")
    void testAddInterceptors_ShouldRegisterExactlyTwoInterceptors() {
        // Given
        SecurityConfig config = new SecurityConfig(
            usersAuthorizationHeader,
            rateLimitMaximumTokensCapacity,
            rateLimitRefillTokens,
            rateLimitRefillPeriodMinutes
        );
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class, RETURNS_DEEP_STUBS);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);

        // When
        config.addInterceptors(registry);

        // Then
        verify(registry, times(2)).addInterceptor(any(HandlerInterceptor.class));
    }

    @Test
    @DisplayName("Should register RateLimitInterceptor before UsersAuthorizationInterceptor")
    void testAddInterceptors_RateLimitInterceptorRegisteredFirst() {
        // Given
        SecurityConfig config = new SecurityConfig(
            usersAuthorizationHeader,
            rateLimitMaximumTokensCapacity,
            rateLimitRefillTokens,
            rateLimitRefillPeriodMinutes
        );
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class, RETURNS_DEEP_STUBS);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);

        // When
        config.addInterceptors(registry);

        // Then
        ArgumentCaptor<HandlerInterceptor> captor = ArgumentCaptor.forClass(HandlerInterceptor.class);
        verify(registry, times(2)).addInterceptor(captor.capture());
        List<HandlerInterceptor> interceptors = captor.getAllValues();
        assertThat(interceptors.get(0)).isInstanceOf(RateLimitInterceptor.class);
        assertThat(interceptors.get(1)).isInstanceOf(UsersAuthorizationInterceptor.class);
    }
}
