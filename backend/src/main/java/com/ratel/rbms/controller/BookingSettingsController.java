package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BlackoutDateRequest;
import com.ratel.rbms.dto.BlackoutDateResponse;
import com.ratel.rbms.dto.BookingSettingsRequest;
import com.ratel.rbms.dto.BookingSettingsResponse;
import com.ratel.rbms.service.BookingSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('OWNER')")
public class BookingSettingsController {

    private final BookingSettingsService bookingSettingsService;

    public BookingSettingsController(BookingSettingsService bookingSettingsService) {
        this.bookingSettingsService = bookingSettingsService;
    }

    @GetMapping("/settings")
    public BookingSettingsResponse get() {
        return bookingSettingsService.get();
    }

    @PutMapping("/settings")
    public BookingSettingsResponse update(@Valid @RequestBody BookingSettingsRequest request) {
        return bookingSettingsService.update(request);
    }

    @GetMapping("/blackout-dates")
    public List<BlackoutDateResponse> listBlackoutDates() {
        return bookingSettingsService.listBlackoutDates();
    }

    @PostMapping("/blackout-dates")
    public ResponseEntity<BlackoutDateResponse> addBlackoutDate(@Valid @RequestBody BlackoutDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingSettingsService.addBlackoutDate(request));
    }

    @DeleteMapping("/blackout-dates/{id}")
    public ResponseEntity<Void> removeBlackoutDate(@PathVariable UUID id) {
        bookingSettingsService.removeBlackoutDate(id);
        return ResponseEntity.noContent().build();
    }
}
