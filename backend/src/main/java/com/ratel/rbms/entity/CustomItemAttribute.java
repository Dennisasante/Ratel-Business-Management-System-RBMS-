package com.ratel.rbms.entity;

import com.ratel.rbms.entity.enums.AttributeSelectionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "custom_item_attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomItemAttribute {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    // SINGLE = radio-style (the only mode that existed before this field).
    // MULTIPLE = customer can pick more than one option (e.g. "Finishing
    // options") — no submission-format change needed, see CustomWigRequestService.
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 10)
    @Builder.Default
    private AttributeSelectionType selectionType = AttributeSelectionType.SINGLE;

    // Purely a UI grouping hint for the hosted configurator page — attributes
    // sharing the same non-null stepGroup render together as one step.
    @Column(name = "step_group", length = 60)
    private String stepGroup;
}
