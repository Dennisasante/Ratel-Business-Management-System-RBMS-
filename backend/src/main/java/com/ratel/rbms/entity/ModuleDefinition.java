package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * Talia Unified Platform, Phase 1 — the canonical module vocabulary, as a
 * pure reference/lookup table. Nothing in the application reads this yet:
 * {@code businesses.enabled_modules} and ModuleAccessService/
 * PlanFeatureService are completely unchanged by this table's existence.
 * See the architecture proposal §E for what a future phase does with it —
 * consulting {@code requiredModules}/{@code defaultForProfiles} to gate
 * activation is explicitly out of scope for Phase 1.
 *
 * {@code code} is the primary key — same flat-string convention as
 * {@code Business.enabledModules} itself, deliberately not a native Postgres
 * enum (a new module must never require a migration to the enum type).
 */
@Entity
@Table(name = "module_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleDefinition {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "industry_group", nullable = false, length = 30)
    private String industryGroup;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_modules", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> requiredModules = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "default_for_profiles", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> defaultForProfiles = List.of();

    @Column(name = "is_core", nullable = false)
    @Builder.Default
    private boolean core = false;

    // TRUE only for the 10 codes ModuleAccessService/PlatformBusinessService
    // already gate/validate in production today. Every other row is
    // reserved vocabulary — its presence here does not enable anything.
    @Column(name = "already_enforced", nullable = false)
    @Builder.Default
    private boolean alreadyEnforced = false;

    @Column(name = "source_system", nullable = false, length = 20)
    private String sourceSystem;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
