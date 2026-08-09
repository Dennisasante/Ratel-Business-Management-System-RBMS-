package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BookableServiceResponse;
import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingDetailResponse;
import com.ratel.rbms.dto.BookingVerifyPaymentResponse;
import com.ratel.rbms.dto.BookingWidgetConfigResponse;
import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.CreateBookingRequest;
import com.ratel.rbms.dto.RescheduleBookingRequest;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * No JWT on any of this — it's what a pasted-in-WordPress embed script calls
 * directly. Every endpoint is scoped by an explicit businessId (from the
 * widget's own config) or a booking's manage_token, never TenantContext.
 */
@RestController
@RequestMapping("/api/public/bookings")
public class PublicBookingController {

    private final BookingService bookingService;

    public PublicBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/widget-config")
    public BookingWidgetConfigResponse widgetConfig(@RequestParam UUID businessId) {
        return bookingService.getWidgetConfig(businessId);
    }

    // Backs the hosted booking page (ratel.app/book/{slug}) for businesses with
    // no website of their own — resolves businessId once, the page reuses the
    // other businessId-based endpoints above for everything after that.
    @GetMapping("/by-slug/{slug}/widget-config")
    public BookingWidgetConfigResponse widgetConfigBySlug(@PathVariable String slug) {
        return bookingService.getWidgetConfigBySlug(slug);
    }

    @GetMapping("/services")
    public List<BookableServiceResponse> listServices(@RequestParam UUID businessId) {
        return bookingService.listBookableServices(businessId);
    }

    @PostMapping
    public ResponseEntity<BookingCreatedResponse> create(@RequestParam UUID businessId, @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(businessId, request));
    }

    @PostMapping("/{token}/pay")
    public CheckoutResponse pay(@PathVariable String token) {
        return bookingService.startPayment(token);
    }

    @PostMapping("/verify")
    public BookingVerifyPaymentResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return bookingService.verifyPayment(request.reference());
    }

    @PostMapping("/{token}/pay-in-person")
    public ResponseEntity<Void> payInPerson(@PathVariable String token) {
        bookingService.payInPerson(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{token}")
    public BookingDetailResponse get(@PathVariable String token) {
        return bookingService.getByManageToken(token);
    }

    @PatchMapping("/{token}/reschedule")
    public ResponseEntity<Void> reschedule(@PathVariable String token, @Valid @RequestBody RescheduleBookingRequest request) {
        bookingService.reschedule(token, request.scheduledAt());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> cancel(@PathVariable String token) {
        bookingService.cancel(token);
        return ResponseEntity.noContent().build();
    }
}
