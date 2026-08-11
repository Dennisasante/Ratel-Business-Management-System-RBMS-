package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BookingCreatedResponse;
import com.ratel.rbms.dto.BookingListResponse;
import com.ratel.rbms.dto.CreateStaffBookingRequest;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.service.BookingListService;
import com.ratel.rbms.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
