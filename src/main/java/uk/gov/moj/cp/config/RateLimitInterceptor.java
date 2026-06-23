package uk.gov.moj.cp.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int rateLimitMaximumTokensCapacity;
    private final int rateLimitRefillTokens;
    private final int rateLimitRefillPeriodMinutes;

    private final Cache<String, Bucket> userLimitBuckets = Caffeine.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build();

    public RateLimitInterceptor(int rateLimitMaximumTokensCapacity, int rateLimitRefillTokens, int rateLimitRefillPeriodMinutes) {
        this.rateLimitMaximumTokensCapacity = rateLimitMaximumTokensCapacity;
        this.rateLimitRefillTokens = rateLimitRefillTokens;
        this.rateLimitRefillPeriodMinutes = rateLimitRefillPeriodMinutes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        final String rateLimitKey = resolveRateLimitKey(request);
        log.debug("Rate limit key [{}]", rateLimitKey);

        Bucket bucket = userLimitBuckets.get(rateLimitKey, this::newBucket);
        ConsumptionProbe consumptionProbe = null;
        if (bucket != null) {
            consumptionProbe = bucket.tryConsumeAndReturnRemaining(1);
            if (consumptionProbe.isConsumed()) {
                return true;
            }
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        if (consumptionProbe != null) {
            long secondsWaitForRefill = consumptionProbe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(secondsWaitForRefill));
        }
        response.sendError(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Too many requests - please retry after the Retry-After period"
        );
        return false;
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(rateLimitMaximumTokensCapacity)
            .refillGreedy(rateLimitRefillTokens, Duration.ofMinutes(rateLimitRefillPeriodMinutes))
            .build();
        log.debug("Rate limiting bucket created for a user [{}]", key);
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        final String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            log.debug("Rate limiting by X-Session-Id header [{}]", sessionId);
            return sessionId;
        }
        log.error("X-Session-Id header not set, please check request on UI");

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            log.debug("Rate limiting by Authorization header [{}]", authHeader);
            return authHeader.split(" ")[1].trim();
        }
        final String remoteAddr = request.getRemoteAddr();
        log.debug("Rate limiting by remoteAddr [{}]", remoteAddr);
        return remoteAddr;
    }
}
