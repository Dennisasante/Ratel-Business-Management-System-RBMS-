package com.ratel.rbms.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talia Unified Platform, Phase 5D Stage 0 hardening §3 — proves V60's backfill produces
 * deterministic {@code connection_state} values against a database that already has REAL V59 data
 * in every shape the existing schema permits, not just a fresh empty database (already covered by
 * {@code Phase5CFreshMigrationTest}). Migrates to V59 first, inserts four
 * {@code ai_channel_bindings} rows (every combination of configured/unconfigured x active/inactive
 * the pre-Stage-0 schema allows), THEN applies V60, and asserts each row's backfilled state.
 *
 * <p>No external provider call is made anywhere in this test or in V60 itself — the migration
 * backfill is a pure SQL CASE expression over existing columns (see V60's own header comment).
 * "CONNECTED after migration" for an active+configured legacy row means exactly "was configured
 * and active before Stage 0 existed, with no historical health signal available" — NOT "Meta was
 * actually contacted and confirmed this token still works." That distinction is asserted directly
 * below (no {@code last_verified_at} is ever set by the migration).
 */
class V60ConnectionStateBackfillTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/ratel_db";
    private static final String USER = System.getenv().getOrDefault("DB_USERNAME", "ratel_user");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "ratel_pass");

    @Test
    void v60BackfillsDeterministicConnectionStateForEveryPreExistingBindingShape() throws Exception {
        String dbName = "rbms_v60_backfill_" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");

        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
             Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        }

        String freshUrl = "jdbc:postgresql://localhost:5432/" + dbName;
        try {
            // Step 1 — migrate ONLY to V59 (Phase 5C's own frozen endpoint), matching the real
            // upgrade path any existing production database would actually take.
            Flyway toV59 = Flyway.configure()
                    .dataSource(freshUrl, USER, PASSWORD)
                    .locations("classpath:db/migration")
                    .target("59")
                    .load();
            var v59Result = toV59.migrate();
            assertTrue(v59Result.success);
            assertEquals("59", v59Result.targetSchemaVersion);

            UUID activeConfiguredId = UUID.randomUUID();
            UUID inactiveConfiguredId = UUID.randomUUID();
            UUID activeUnconfiguredId = UUID.randomUUID();
            UUID inactiveUnconfiguredId = UUID.randomUUID();
            UUID businessId = UUID.randomUUID();

            try (Connection conn = DriverManager.getConnection(freshUrl, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO businesses (id, name, slug, industry, currency) VALUES "
                        + "('" + businessId + "', 'V60 Backfill Test Biz', 'v60-backfill-" + businessId + "', 'OTHER', 'GHS')");

                insertBinding(stmt, activeConfiguredId, businessId, "phone-active-configured-" + UUID.randomUUID(), "encrypted-token-value", true);
                insertBinding(stmt, inactiveConfiguredId, businessId, "phone-inactive-configured-" + UUID.randomUUID(), "encrypted-token-value", false);
                insertBinding(stmt, activeUnconfiguredId, businessId, "phone-active-unconfigured-" + UUID.randomUUID(), null, true);
                insertBinding(stmt, inactiveUnconfiguredId, businessId, "phone-inactive-unconfigured-" + UUID.randomUUID(), null, false);
            }

            // Step 2 — apply the rest of the chain (V60).
            Flyway toLatest = Flyway.configure()
                    .dataSource(freshUrl, USER, PASSWORD)
                    .locations("classpath:db/migration")
                    .load();
            var latestResult = toLatest.migrate();
            assertTrue(latestResult.success, "V60 must apply cleanly on top of real pre-existing V59 data");
            assertEquals("60", latestResult.targetSchemaVersion);

            try (Connection conn = DriverManager.getConnection(freshUrl, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {
                assertConnectionState(stmt, activeConfiguredId, "CONNECTED");
                assertConnectionState(stmt, inactiveConfiguredId, "DISCONNECTED");
                assertConnectionState(stmt, activeUnconfiguredId, "NOT_CONNECTED");
                assertConnectionState(stmt, inactiveUnconfiguredId, "NOT_CONNECTED");

                // The critical distinction: "CONNECTED" here must never be confused with "actually
                // verified" — no historical health signal exists for a pre-Stage-0 row, and the
                // migration itself makes no network call, so last_verified_at must be NULL.
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT last_verified_at, last_failure_at, connection_method FROM ai_channel_bindings WHERE id = '" + activeConfiguredId + "'")) {
                    assertTrue(rs.next());
                    assertNull(rs.getTimestamp(1), "the migration must never fabricate a verification timestamp");
                    assertNull(rs.getTimestamp(2));
                    assertEquals("MANUAL", rs.getString(3), "every pre-Stage-0 binding was, by construction, manually configured");
                }
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                 Statement stmt = admin.createStatement()) {
                stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid()");
                stmt.execute("DROP DATABASE IF EXISTS " + dbName);
            }
        }
    }

    private static void insertBinding(Statement stmt, UUID id, UUID businessId, String externalAccountId, String credentialsOrNull, boolean active) throws Exception {
        stmt.execute("INSERT INTO ai_channel_bindings (id, business_id, channel, external_account_id, credentials_encrypted, is_active) VALUES ("
                + "'" + id + "', '" + businessId + "', 'WHATSAPP', '" + externalAccountId + "', "
                + (credentialsOrNull == null ? "NULL" : "'" + credentialsOrNull + "'") + ", " + active + ")");
    }

    private static void assertConnectionState(Statement stmt, UUID id, String expected) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT connection_state FROM ai_channel_bindings WHERE id = '" + id + "'")) {
            assertTrue(rs.next());
            assertEquals(expected, rs.getString(1), "unexpected backfilled connection_state for binding " + id);
        }
    }
}
