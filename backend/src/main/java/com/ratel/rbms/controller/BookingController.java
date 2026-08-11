package com.ratel.rbms.controller;

import com.ratel.rbms.dto.BookingListResponse;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.service.BookingListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingListService bookingListService;

    public BookingController(BookingListService bookingListService) {
        this.bookingListService = bookingListService;
    }

    @GetMapping
    public List<BookingListResponse> list(@RequestParam(required = false) ServiceOrderStatus status) {
        return bookingListService.list(status);
    }
}
