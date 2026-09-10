package com.ratel.rbms.controller;

import com.ratel.rbms.entity.enums.BookingCutoverState;
import com.ratel.rbms.service.BookingCutoverStateResolver;
import com.ratel.rbms.service.PackageContentBackfillService;
import com.ratel.rbms.service.Stage1VerificationService;
import com.ratel.rbms.service.VerificationResult;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5C — the only operator surface for the Booking cutover lifecycle
 * (frozen design, Revision 4 §4/§21). Deliberately thin: every real decision is made by
 * {@link BookingCutoverStateResolver} (state transitions) or {@link Stage1VerificationService}
 * (parity/policy comparison) — this controller only wires an authenticated Owner request to them.
 * Owner-only throughout: this is the surface that turns canonical pricing/eligibility on for real
 * customer-facing bookings, not a routine staff operation.
 *
 * <p>Does NOT expose legacy retirement (Revision 4 §6 — dormant, never activated by Phase 5C) and
 * does NOT expose any way to bypass verification (frozen implementation instruction §21).
 */
@RestController
@RequestMapping("/api/booking-cutover")
@PreAuthorize("hasRole('OWNER')")
public class BookingCutoverController {

    private final BookingCutoverStateResolver bookingCutoverStateResolver;
    private final Stage1VerificationService stage1VerificationService;
    private final PackageContentBackfillService packageContentBackfillService;

    public BookingCutoverController(
            BookingCutoverStateResolver bookingCutoverStateResolver,
            Stage1VerificationService stage1VerificationService,
            PackageContentBackfillService packageContentBackfillService
    ) {
        this.bookingCutoverStateResolver = bookingCutoverStateResolver;
        this.stage1VerificationService = stage1VerificationService;
        this.packageContentBackfillService = packageContentBackfillService;
    }

    @GetMapping("/status")
    public Map<String, BookingCutoverState> status() {
        UUID businessId = TenantContext.getBusinessId();
        return Map.of("state", bookingCutoverStateResolver.resolve(businessId));
    }

    // Prerequisite step (Revision 4 §3/§6/§19 risk 5) — must run (and its own zero-orphan/
    // reconciled result reviewed) before /verify can ever report clean for a business with
    // packages. dryRun defaults true so a first call is always safe to inspect before writing.
    @PostMapping("/backfill-packages")
    public List<PackageContentBackfillService.PackageBackfillResult> backfillPackages(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        UUID businessId = TenantContext.getBusinessId();
        return packageContentBackfillService.backfillBusiness(businessId, dryRun);
    }

    // Stage 1 offline verification (frozen design §1/§3) — the outcome-application logic itself
    // lives once, on BookingCutoverStateResolver.applyVerificationOutcome, so every caller (this
    // endpoint, a future scheduled job, this project's own tests) gets identical behaviour.
    @PostMapping("/verify")
    public VerificationResult verify() {
        UUID businessId = TenantContext.getBusinessId();
        VerificationResult result = stage1VerificationService.verifyBusiness(businessId);
        bookingCutoverStateResolver.applyVerificationOutcome(businessId, result);
        return result;
    }

    // Requires VERIFIED (BookingCutoverStateResolver enforces the precondition itself).
    @PostMapping("/enable")
    public Map<String, BookingCutoverState> enable() {
        UUID businessId = TenantContext.getBusinessId();
        bookingCutoverStateResolver.enableCanonical(businessId);
        return Map.of("state", bookingCutoverStateResolver.resolve(businessId));
    }

    // Requires CANONICAL_ENABLED — a pure deployment toggle, verification state is never lost.
    @PostMapping("/revert")
    public Map<String, BookingCutoverState> revert() {
        UUID businessId = TenantContext.getBusinessId();
        bookingCutoverStateResolver.revertToVerified(businessId);
        return Map.of("state", bookingCutoverStateResolver.resolve(businessId));
    }
}
