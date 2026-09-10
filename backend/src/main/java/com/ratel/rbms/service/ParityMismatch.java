package com.ratel.rbms.service;

import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — one entry in the deterministic commercial-parity/policy-
 * compatibility mismatch taxonomy (frozen design, Revision 4 §1, tightened per the final
 * implementation instruction §19). {@code code} is always one of the seven frozen commercial
 * codes ({@code PRICE_MISMATCH}, {@code LABEL_MISMATCH}, {@code DURATION_MISMATCH},
 * {@code AVAILABILITY_FLAG_MISMATCH}, {@code PAYMENT_POLICY_MISMATCH}, {@code CONTENTS_MISMATCH},
 * {@code SERVICE_ORDER_FIELD_DRIFT}) or the one policy-compatibility code
 * ({@code POLICY_SCOPE_NARROWED}) — never an invented parallel taxonomy (e.g. max-concurrency
 * mismatches are reported as {@code AVAILABILITY_FLAG_MISMATCH}, per the approved instruction).
 *
 * @param subjectType "SERVICE_CATALOG_ITEM" or "SERVICE_PACKAGE" — which legacy item this
 *                    mismatch was found against.
 * @param subjectId   that legacy item's id.
 */
public record ParityMismatch(String code, String subjectType, UUID subjectId, String detail) {

    public static final String PRICE_MISMATCH = "PRICE_MISMATCH";
    public static final String LABEL_MISMATCH = "LABEL_MISMATCH";
    public static final String DURATION_MISMATCH = "DURATION_MISMATCH";
    public static final String AVAILABILITY_FLAG_MISMATCH = "AVAILABILITY_FLAG_MISMATCH";
    public static final String PAYMENT_POLICY_MISMATCH = "PAYMENT_POLICY_MISMATCH";
    public static final String CONTENTS_MISMATCH = "CONTENTS_MISMATCH";
    public static final String SERVICE_ORDER_FIELD_DRIFT = "SERVICE_ORDER_FIELD_DRIFT";
    public static final String POLICY_SCOPE_NARROWED = "POLICY_SCOPE_NARROWED";
}
