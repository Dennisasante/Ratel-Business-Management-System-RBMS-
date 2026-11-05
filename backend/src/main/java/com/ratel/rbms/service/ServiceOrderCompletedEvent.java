package com.ratel.rbms.service;

import java.util.UUID;

/** Published when a service order is completed — a visit for the customer's loyalty points. */
public record ServiceOrderCompletedEvent(UUID orderId, UUID businessId) {
}
