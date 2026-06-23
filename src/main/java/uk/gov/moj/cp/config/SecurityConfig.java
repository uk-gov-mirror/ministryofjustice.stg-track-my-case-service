package uk.gov.moj.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static uk.gov.moj.cp.config.ApiPaths.PATH_API;
import static uk.gov.moj.cp.config.ApiPaths.PATH_API_HEALTH;
import static uk.gov.moj.cp.config.ApiPaths.PATH_API_USERS;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    private final String usersAuthorizationHeader;
    private final int rateLimitMaximumTokensCapacity;
    private final int rateLimitRefillTokens;
    private final int rateLimitRefillPeriodMinutes;

    public SecurityConfig(@Value("${services.users.authorization-header}") String usersAuthorizationHeader,
                          @Value("${services.rate-limit.maximum-tokens-capacity}") int rateLimitMaximumTokensCapacity,
                          @Value("${services.rate-limit.refill-tokens}") int rateLimitRefillTokens,
                          @Value("${services.rate-limit.refill-period-minutes}") int rateLimitRefillPeriodMinutes) {
        this.usersAuthorizationHeader = usersAuthorizationHeader;
        this.rateLimitMaximumTokensCapacity = rateLimitMaximumTokensCapacity;
        this.rateLimitRefillTokens = rateLimitRefillTokens;
        this.rateLimitRefillPeriodMinutes = rateLimitRefillPeriodMinutes;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(
                rateLimitMaximumTokensCapacity,
                rateLimitRefillTokens,
                rateLimitRefillPeriodMinutes
            ))
            .addPathPatterns(PATH_API + "/**")
            .excludePathPatterns(PATH_API_HEALTH);

        registry.addInterceptor(new UsersAuthorizationInterceptor(usersAuthorizationHeader))
            .addPathPatterns(List.of(
                PATH_API_USERS + "/**"
            ));

        // commenting this as we need to remove whitelisting and allow user to access after successful OneLogin

        /* registry.addInterceptor(new CaseAuthorizationInterceptor(userService))
            .addPathPatterns(List.of(
                PATH_API_CASES + "/**"
            ));
       */
    }
}
