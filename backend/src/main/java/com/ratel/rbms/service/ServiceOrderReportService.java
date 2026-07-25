package com.ratel.rbms.service;

import com.ratel.rbms.dto.ServiceOrderReportResponse;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServiceType;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServiceTypeRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kept separate from ReportService (sales/expenses) — service orders have their own
 * revenue/turnaround shape that doesn't fit the sales report's columns. */
@Service
public class ServiceOrderReportService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public ServiceOrderReportService(ServiceOrderRepository serviceOrderRepository, ServiceTypeRepository serviceTypeRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    public ServiceOrderReportResponse report(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Status distribution of orders that entered the shop in this window —
        // i.e. "of what came in this period, where does it stand now."
        List<ServiceOrder> received = serviceOrderRepository.findAllByBusinessIdAndReceivedAtBetween(businessId, fromInstant, toInstant);
        Map<ServiceOrderStatus, Integer> statusCounts = new EnumMap<>(ServiceOrderStatus.class);
        for (ServiceOrderStatus status : ServiceOrderStatus.values()) statusCounts.put(status, 0);
        for (ServiceOrder order : received) {
            statusCounts.merge(order.getStatus(), 1, Integer::sum);
        }

        // Revenue and turnaround are both scoped to pickup date, not receipt date —
        // an order only "counts" financially once the customer has collected it.
        List<ServiceOrder> pickedUp = serviceOrderRepository.findAllByBusinessIdAndPickedUpAtBetween(businessId, fromInstant, toInstant);

        // Every service type the business has defined shows up, even at GH₵0 — same
        // "show every category" behavior as when this was a fixed 4-value enum.
        List<ServiceType> types = serviceTypeRepository.findAllByBusinessIdOrderByNameAsc(businessId);
        Map<UUID, BigDecimal> revenueByTypeId = new LinkedHashMap<>();
        for (ServiceType type : types) revenueByTypeId.put(type.getId(), BigDecimal.ZERO);
        for (ServiceOrder order : pickedUp) {
            revenueByTypeId.merge(order.getServiceTypeId(), order.getPrice(), BigDecimal::add);
        }

        var avgMinutes = pickedUp.stream()
                .filter(o -> o.getReceivedAt() != null && o.getPickedUpAt() != null)
                .mapToLong(o -> Duration.between(o.getReceivedAt(), o.getPickedUpAt()).toMinutes())
                .average();
        double avgTurnaroundHours = avgMinutes.isPresent() ? avgMinutes.getAsDouble() / 60.0 : 0.0;

        Map<String, Integer> statusCountsOut = new LinkedHashMap<>();
        for (var entry : statusCounts.entrySet()) statusCountsOut.put(entry.getKey().name(), entry.getValue());

        Map<UUID, String> typeNames = new LinkedHashMap<>();
        for (ServiceType type : types) typeNames.put(type.getId(), type.getName());

        List<ServiceOrderReportResponse.TypeRevenue> revenueByType = revenueByTypeId.entrySet().stream()
                .map(e -> new ServiceOrderReportResponse.TypeRevenue(e.getKey(), typeNames.get(e.getKey()), e.getValue()))
                .toList();

        return new ServiceOrderReportResponse(from, to, revenueByType, statusCountsOut, avgTurnaroundHours);
    }
}
