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
 * Talia Unified Platform, Phase 5C — proves the FULL migration chain applies cleanly to a
 * genuinely EMPTY PostgreSQL database (frozen implementation instruction §31), independent of the
 * shared local dev database's own already-migrated history. Not a Spring context test
 * deliberately — creates a throwaway database on the SAME local Postgres server this whole
 * project's tests already connect to (via plain JDBC/Flyway, no Spring Boot involved), runs every
 * migration from scratch, spot-checks the schema directly, then drops the throwaway database —
 * leaves no trace behind, never touches the real ratel_db or production.
 *
 * <p>Phase 5D Stage 0: extended forward to V60, exactly as this same class was itself extended at
 * every prior phase boundary in this project (V57->V58->V59) — the target-version assertion and
 * spot-checks below track whatever the current latest migration is; Phase 5C's OWN migrations
 * (V1-V59) and their own assertions above this line are completely unmodified. Freezing Phase 5C
 * means its schema/behavior never changes, not that this proof-of-a-clean-migration-chain test
 * stops being extended as later phases legitimately add to the SAME chain.
 */
class Phase5CFreshMigrationTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/ratel_db";
    private static final String USER = System.getenv().getOrDefault("DB_USERNAME", "ratel_user");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "ratel_pass");

    @Test
    void fullMigrationChainAppliesCleanlyToAFreshEmptyDatabase() throws Exception {
        String dbName = "rbms_phase5c_fresh_" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");

        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
             Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        }

        String freshUrl = "jdbc:postgresql://localhost:5432/" + dbName;
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(freshUrl, USER, PASSWORD)
                    .locations("classpath:db/migration")
                    .load();

            var result = flyway.migrate();
            assertTrue(result.success, "full migration chain must apply cleanly to an empty database");
            assertEquals("61", result.targetSchemaVersion, "must end at V61 (product category subcategories)");

            try (Connection conn = DriverManager.getConnection(freshUrl, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {

                // Spot-check the exact Phase 5C schema additions, directly against information_schema —
                // proves the migration itself, independent of Hibernate's own validate-mode agreement
                // (already separately proven by every @SpringBootTest run in this suite).
                assertTrue(columnExists(stmt, "businesses", "booking_cutover_state"));
                assertTrue(columnExists(stmt, "package_components", "display_order"));
                assertTrue(columnExists(stmt, "package_components", "legacy_service_package_item_id"));
                assertTrue(columnExists(stmt, "service_orders", "offering_id"));
                assertTrue(tableExists(stmt, "service_order_line_snapshots"));

                // Phase 5D Stage 0 additions (V60) — same spot-check pattern, new columns only.
                assertTrue(columnExists(stmt, "ai_channel_bindings", "connection_method"));
                assertTrue(columnExists(stmt, "ai_channel_bindings", "last_verified_at"));
                assertTrue(columnExists(stmt, "ai_channel_bindings", "last_failure_at"));
                assertTrue(columnExists(stmt, "ai_channel_bindings", "connection_state"));

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_name='businesses' AND column_name='booking_cutover_state'")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getString(1).contains("NOT_READY"), "default must be NOT_READY");
                }

                // Immutability trigger proof (Revision 4 §2) — real INSERT then real UPDATE attempt.
                UUID businessId = UUID.randomUUID();
                UUID serviceOrderId = insertMinimalServiceOrderForTrigger(conn, businessId);
                stmt.execute("INSERT INTO service_order_line_snapshots (business_id, service_order_id, label, amount) "
                        + "VALUES ('" + businessId + "', '" + serviceOrderId + "', 'Test Line', 10.00)");
                Exception updateFailure = assertThrows(Exception.class, () -> stmt.execute(
                        "UPDATE service_order_line_snapshots SET amount = 999.00 WHERE business_id = '" + businessId + "'"));
                assertTrue(updateFailure.getMessage().contains("immutable"));
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                 Statement stmt = admin.createStatement()) {
                // Terminate any lingering backend on the throwaway DB before dropping it.
                stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid()");
                stmt.execute("DROP DATABASE IF EXISTS " + dbName);
            }
        }
    }

    private static UUID insertMinimalServiceOrderForTrigger(Connection conn, UUID businessId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO businesses (id, name, slug, industry, currency) VALUES "
                    + "('" + businessId + "', 'Trigger Test Biz', 'trigger-test-" + businessId + "', 'OTHER', 'GHS')");
            UUID customerId = UUID.randomUUID();
            UUID serviceTypeId = UUID.randomUUID();
            stmt.execute("INSERT INTO service_types (id, business_id, name) VALUES "
                    + "('" + serviceTypeId + "', '" + businessId + "', 'Trigger Test Type')");
            stmt.execute("INSERT INTO service_orders (id, business_id, service_type_id, status, price) VALUES "
                    + "('" + id + "', '" + businessId + "', '" + serviceTypeId + "', 'RECEIVED', 10.00)");
        }
        return id;
    }

    private static boolean columnExists(Statement stmt, String table, String column) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name='" + table + "' AND column_name='" + column + "'")) {
            return rs.next();
        }
    }

    private static boolean tableExists(Statement stmt, String table) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM information_schema.tables WHERE table_name='" + table + "'")) {
            return rs.next();
        }
    }
}
