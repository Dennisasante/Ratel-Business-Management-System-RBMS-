package com.ratel.rbms.entity.enums;

public enum Role {
    OWNER,
    MANAGER,
    SALES_PERSON,
    ACCOUNTANT,
    // Performs services, not "management" — scoped to only their own assigned
    // service orders. See ServiceOrderService for the enforcement.
    STAFF
}
