package com.ratel.rbms.entity.enums;

/**
 * Every channel Tallia AI can, or will eventually, receive a message from.
 * Phase 3A ("Channel Foundation") only ever routes WEB_DEMO — the rest are
 * reserved values so a future phase (WhatsApp/Instagram/Facebook/Phone/SMS/
 * Email integration) never needs another enum-shaped migration to add one.
 * Persisted the same way every other status/category enum in this codebase
 * is (@Enumerated(EnumType.STRING) over a plain varchar column, e.g.
 * ServiceOrderStatus) — never a native Postgres enum type.
 */
public enum AiChannel {
    WEB_DEMO,
    WHATSAPP,
    INSTAGRAM,
    FACEBOOK,
    PHONE,
    SMS,
    EMAIL
}
