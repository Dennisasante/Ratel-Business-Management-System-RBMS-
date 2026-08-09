package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A real service_catalog item and quantity making up part of a ServicePackage. */
@Entity
@Table(name = "service_package_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackageItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "service_catalog_id", nullable = false)
    private UUID serviceCatalogId;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;
}
