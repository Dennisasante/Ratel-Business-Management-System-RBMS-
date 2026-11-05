package com.ratel.rbms.service;

import com.ratel.rbms.dto.SmsPlatformOverviewResponse;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.repository.SmsPlatformSettingsRepository;
import com.ratel.rbms.sms.SmsProviderKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mNotify key: saved encrypted from the Super Admin page, beats the server's own key, never exposed beyond its last four characters. */
@SpringBootTest
class SmsProviderKeyIntegrationTest {

    @Autowired private SmsPlatformService smsPlatformService;
    @Autowired private SmsPlatformSettingsRepository settingsRepository;
    @Autowired private PlatformAdminRepository platformAdminRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        adminId = platformAdminRepository.save(PlatformAdmin.builder()
                .email("sms-key-admin-" + unique + "@example.com").passwordHash("x").fullName("Key Admin").build()).getId();
        jdbc.update("UPDATE sms_platform_settings SET mnotify_api_key = NULL WHERE id = 1");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE sms_platform_settings SET mnotify_api_key = NULL WHERE id = 1");
        jdbc.update("DELETE FROM platform_audit_logs WHERE admin_id = ?", adminId);
        jdbc.update("DELETE FROM platform_admins WHERE id = ?", adminId);
    }

    @Test
    void withNoKeyAnywhereThereIsNothingToSendWith() {
        SmsProviderKeyService service = new SmsProviderKeyService(settingsRepository, "");

        assertEquals("", service.get());
        assertEquals(SmsProviderKeyService.Source.NONE, service.describe().source());
        assertNull(service.describe().hint());
    }

    @Test
    void theServersOwnKeyIsTheFallback() {
        SmsProviderKeyService service = new SmsProviderKeyService(settingsRepository, "ENV-KEY-abcd1234");

        assertEquals("ENV-KEY-abcd1234", service.get());
        assertEquals(SmsProviderKeyService.Source.SERVER, service.describe().source());
    }

    @Test
    void aKeySavedOnThePageWinsAndWorksStraightAwayWithoutARestart() {
        SmsProviderKeyService service = new SmsProviderKeyService(settingsRepository, "ENV-KEY-abcd1234");

        smsPlatformService.updateProviderKey(adminId, "  SAVED-KEY-wxyz9876  ");

        assertEquals("SAVED-KEY-wxyz9876", service.get(), "saved key is used, whitespace trimmed");
        assertEquals(SmsProviderKeyService.Source.SETTINGS, service.describe().source());
    }

    @Test
    void onlyTheLastFourCharactersAreEverExposed() {
        smsPlatformService.updateProviderKey(adminId, "SECRET-KEY-wxyz9876");

        SmsPlatformOverviewResponse overview = smsPlatformService.overview();

        assertEquals("SETTINGS", overview.keySource());
        assertTrue(overview.keyHint().endsWith("9876"));
        assertFalse(overview.keyHint().contains("SECRET"), "nothing but the tail is shown");
        assertFalse(overview.toString().contains("SECRET-KEY"), "the full key never appears in the response");
    }

    @Test
    void theKeyIsEncryptedInTheDatabaseNotStoredAsPlainText() {
        smsPlatformService.updateProviderKey(adminId, "SECRET-KEY-wxyz9876");

        String raw = jdbc.queryForObject("SELECT mnotify_api_key FROM sms_platform_settings WHERE id = 1", String.class);

        assertNotEquals("SECRET-KEY-wxyz9876", raw);
        assertFalse(raw.contains("SECRET-KEY"), "the stored value must not contain the key");
    }

    @Test
    void savingABlankKeyRemovesItAndTheServerKeyTakesOverAgain() {
        SmsProviderKeyService service = new SmsProviderKeyService(settingsRepository, "ENV-KEY-abcd1234");
        smsPlatformService.updateProviderKey(adminId, "SAVED-KEY-wxyz9876");

        smsPlatformService.updateProviderKey(adminId, "   ");

        assertEquals("ENV-KEY-abcd1234", service.get());
        assertEquals(SmsProviderKeyService.Source.SERVER, service.describe().source());
    }

    @Test
    void aTruncatedKeyIsRefusedAndTheExistingOneIsKept() {
        smsPlatformService.updateProviderKey(adminId, "GOOD-KEY-wxyz9876");

        assertThrows(ApiException.class, () -> smsPlatformService.updateProviderKey(adminId, "abc"));

        SmsProviderKeyService service = new SmsProviderKeyService(settingsRepository, "");
        assertEquals("GOOD-KEY-wxyz9876", service.get());
    }

    @Test
    void changingTheKeyIsAuditedWithoutTheKeyInTheLog() {
        smsPlatformService.updateProviderKey(adminId, "AUDIT-KEY-wxyz9876");

        String logged = jdbc.queryForObject(
                "SELECT string_agg(action, ' | ') FROM platform_audit_logs WHERE admin_id = ?", String.class, adminId);

        assertTrue(logged.contains("SMS provider API key updated"), logged);
        assertFalse(logged.contains("AUDIT-KEY"), "the audit trail must never contain the key");
    }
}
