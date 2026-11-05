package com.ratel.rbms.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the SMS provider from app.sms.provider. Deliberately a plain @Bean
 * method rather than @ConditionalOnProperty on each gateway: Docker Compose
 * injects an EMPTY STRING (present-but-blank, not absent) for an unset
 * ${VAR:-}, and conditional-bean matching treats "" as matching neither
 * provider — which would leave no SmsGateway bean at all and stop the app
 * from starting (the same trap docker-compose.prod.yml documents for
 * AI_PROVIDER). Here a blank or unrecognised value simply means the real
 * provider; only the exact word "mock" selects the mock.
 */
@Configuration
public class SmsGatewayConfig {

    private static final String DEFAULT_MNOTIFY_URL = "https://api.mnotify.com/api";

    @Bean
    public SmsGateway smsGateway(
            @Value("${app.sms.provider:mnotify}") String provider,
            SmsProviderKeyService providerKey,
            @Value("${app.sms.mnotify-base-url:" + DEFAULT_MNOTIFY_URL + "}") String baseUrl
    ) {
        if ("mock".equalsIgnoreCase(provider == null ? "" : provider.trim())) {
            return new MockSmsGateway();
        }
        String url = baseUrl == null || baseUrl.isBlank() ? DEFAULT_MNOTIFY_URL : baseUrl.trim();
        return new MnotifyGateway(providerKey, url);
    }
}
