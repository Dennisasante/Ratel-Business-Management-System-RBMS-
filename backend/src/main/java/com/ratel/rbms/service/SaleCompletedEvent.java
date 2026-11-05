package com.ratel.rbms.service;

import java.util.UUID;

/** Published when a sale becomes fully paid — the moment its customer's receipt is due. */
public record SaleCompletedEvent(UUID saleId, UUID businessId) {
}
