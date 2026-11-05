package com.schoolerp.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Master master,
        Tenancy tenancy,
        Platform platform,
        Crypto crypto,
        Jwt jwt,
        RateLimit rateLimit,
        Cache cache,
        Messaging messaging,
        Cors cors) {

    public AppProperties {
        if (cache == null) {
            cache = new Cache(600L, 300L);
        }
        if (messaging == null) {
            messaging = new Messaging(1000L);
        }
    }

    public record Master(String url, String username, String password) {
    }

    public record Tenancy(
            String dbHost,
            int dbPort,
            String dbParams,
            String baseDomain,
            boolean createDbUser,
            String dbUserHost,
            int poolMaxSize,
            boolean migrateOnStartup) {
    }

    public record Platform(String apiKey) {
    }

    public record Crypto(String key) {
    }

    public record Jwt(
            String secret,
            long accessTokenTtlSeconds,
            long refreshTokenTtlSeconds) {
    }

    public record RateLimit(
            int authRequestsPerMinute,
            int publicVerifyRequestsPerMinute,
            int sensitiveMutationRequestsPerMinute,
            int generalApiRequestsPerMinute) {
        public RateLimit {
            if (authRequestsPerMinute <= 0) {
                authRequestsPerMinute = 10;
            }
            if (publicVerifyRequestsPerMinute <= 0) {
                publicVerifyRequestsPerMinute = 30;
            }
            if (sensitiveMutationRequestsPerMinute <= 0) {
                sensitiveMutationRequestsPerMinute = 20;
            }
            if (generalApiRequestsPerMinute <= 0) {
                generalApiRequestsPerMinute = 300;
            }
        }
    }

    public record Cache(
            long settingsTtlSeconds,
            long permissionsTtlSeconds) {
        public Cache {
            if (settingsTtlSeconds <= 0) {
                settingsTtlSeconds = 600L;
            }
            if (permissionsTtlSeconds <= 0) {
                permissionsTtlSeconds = 300L;
            }
        }
    }

    public record Messaging(long relayPollMs) {
        public Messaging {
            if (relayPollMs <= 0) {
                relayPollMs = 1000L;
            }
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
