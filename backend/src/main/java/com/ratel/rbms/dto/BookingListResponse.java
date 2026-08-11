package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.ServiceOrder;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingListResponse(
        Long bookingNumber,
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        String customerLocation,
        String serviceName,
        String orderStatus,
        Instant scheduledAt,
        BigDecimal price,
        String paymentStatus,
        String assignedStaffName,
        Instant createdAt
) {
    public static BookingListResponse from(Booking booking, ServiceOrder order, String serviceName, String assignedStaffName) {
        return new BookingListResponse(
                booking.getBookingNumber(),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                booking.getCustomerWhatsapp(),
                booking.getCustomerLocation(),
                serviceName,
                order != null ? order.getStatus().name() : null,
                order != null ? order.getScheduledAt() : null,
                order != null ? order.getPrice() : null,
                booking.getPaymentStatus(),
                assignedStaffName,
                booking.getCreatedAt()
        );
    }
}
