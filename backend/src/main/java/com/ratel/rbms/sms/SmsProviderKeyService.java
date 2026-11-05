package com.ratel.rbms.sms;

import com.ratel.rbms.entity.SmsPlatformSettings;
import com.ratel.rbms.repository.SmsPlatformSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Where the SMS provider's API key comes from. A key saved on the Super Admin SMS page wins; the
 * server's MNOTIFY_API_KEY is the fallback. Read fresh on every use (a single-row lookup), so a newly
 * saved key works immediately — no restart.
 */
@Component
public class SmsProviderKeyService implements Supplier<String> {

    private static final Logger log = LoggerFactory.getLogger(SmsProviderKeyService.class);

    public enum Source { SETTINGS, SERVER, NONE }

    public record Description(Source source, String hint) {
    }

    private final SmsPlatformSettingsRepository settingsRepository;
    private final String serverKey;

    public SmsProviderKeyService(
            SmsPlatformSettingsRepository settingsRepository,
            @Value("${app.sms.mnotify-api-key:}") String serverKey
    ) {
        this.settingsRepository = settingsRepository;
        this.serverKey = serverKey == null ? "" : serverKey.trim();
    }

    /** The key to use right now, or "" when there is none. */
    @Override
    public String get() {
        String saved = savedKey();
        return saved.isBlank() ? serverKey : saved;
    }

    /** Which key is in force and a masked hint of it (last four characters) — never the key itself. */
    public Description describe() {
        String saved = savedKey();
        if (!saved.isBlank()) {
            return new Description(Source.SETTINGS, mask(saved));
        }
        if (!serverKey.isBlank()) {
            return new Description(Source.SERVER, mask(serverKey));
        }
        return new Description(Source.NONE, null);
    }

    private String savedKey() {
        try {
            return settingsRepository.findById(SmsPlatformSettings.ROW_ID)
                    .map(SmsPlatformSettings::getMnotifyApiKey)
                    .map(String::trim)
                    .orElse("");
        } catch (RuntimeException e) {
            // e.g. the encryption key isn't configured: fall back to the server key rather than break every send.
            log.error("Couldn't read the saved SMS provider key ({})", e.getClass().getSimpleName());
            return "";
        }
    }

    static String mask(String key) {
        String tail = key.length() <= 4 ? "" : key.substring(key.length() - 4);
        return "\u2022\u2022\u2022\u2022" + tail;
    }
}
