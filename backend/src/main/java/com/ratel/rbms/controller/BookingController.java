package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingListResponse;
import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.dto.RescheduleBookingRequest;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.service.BookingListService;
import com.ratel.rbms.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingListService bookingListService;
    private final BookingService bookingService;

    public BookingController(BookingListService bookingListService, BookingService bookingService) {
        this.bookingListService = bookingListService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<BookingListResponse> list(@RequestParam(required = false) ServiceOrderStatus status) {
        return bookingListService.list(status);
    }

    // No @PreAuthorize — mirrors ServiceOrderController.create(), which any
    // authenticated role (including STAFF) can already call to log a walk-in.
    @PostMapping
    public ResponseEntity<BookingCreatedResponse> create(@Valid @RequestBody CreateStaffBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createStaffBooking(request));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<Void> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleBookingRequest request) {
        bookingService.rescheduleById(id, request.scheduledAt());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mark-arrived")
    public ResponseEntity<Void> markArrived(@PathVariable UUID id) {
        bookingService.markArrived(id);
        return ResponseEntity.noContent().build();
    }
}
