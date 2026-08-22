package com.ratel.rbms.service;

import com.ratel.rbms.dto.BookingListResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.ServicePackage;
import com.ratel.rbms.entity.StaffMember;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.ServicePackageRepository;
import com.ratel.rbms.repository.StaffMemberRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The authenticated, staff-facing "who booked, when, and did they pay" list —
 * separate from BookingService, which handles the public no-login booking
 * widget flow. A booking is always backed by a ServiceOrder (see Booking
 * entity); this just decorates each Booking row with that order's current
 * status/schedule/price plus the resolved service or package name.
 */
@Service
public class BookingListService {

    private final BookingRepository bookingRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final UserRepository userRepository;
    private final ModuleAccessService moduleAccessService;

    public BookingListService(
            BookingRepository bookingRepository,
            ServiceOrderRepository serviceOrderRepository,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            ServicePackageRepository servicePackageRepository,
            StaffMemberRepository staffMemberRepository,
            UserRepository userRepository,
            ModuleAccessService moduleAccessService
    ) {
        this.bookingRepository = bookingRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.userRepository = userRepository;
        this.moduleAccessService = moduleAccessService;
    }

    public List<BookingListResponse> list(ServiceOrderStatus status) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "BOOKINGS");

        List<Booking> bookings = bookingRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId);

        Map<UUID, ServiceOrder> ordersById = serviceOrderRepository.findAllById(
                bookings.stream().map(Booking::getServiceOrderId).toList()
        ).stream().collect(java.util.stream.Collectors.toMap(ServiceOrder::getId, Function.identity()));

        Map<UUID, String> catalogNames = serviceCatalogItemRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .collect(java.util.stream.Collectors.toMap(ServiceCatalogItem::getId, ServiceCatalogItem::getName));
        Map<UUID, String> packageNames = servicePackageRepository.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .collect(java.util.stream.Collectors.toMap(ServicePackage::getId, ServicePackage::getName));

        return bookings.stream()
                .map(booking -> new BookingOrderPair(booking, ordersById.get(booking.getServiceOrderId())))
                .filter(pair -> status == null || (pair.order != null && pair.order.getStatus() == status))
                .map(pair -> {
                    String serviceName = pair.order == null ? null
                            : pair.order.getServicePackageId() != null
                                    ? packageNames.get(pair.order.getServicePackageId())
                                    : catalogNames.get(pair.order.getServiceCatalogId());
                    // StaffMember first — that's what assignedStaffId points at now.
                    // Falls back to User only for a pre-migration edge case.
                    String assignedStaffName = pair.order != null && pair.order.getAssignedStaffId() != null
                            ? staffMemberRepository.findById(pair.order.getAssignedStaffId()).map(StaffMember::getFullName)
                                    .orElseGet(() -> userRepository.findById(pair.order.getAssignedStaffId()).map(User::getFullName).orElse(null))
                            : null;
                    return BookingListResponse.from(pair.booking, pair.order, serviceName, assignedStaffName);
                })
                .toList();
    }

    private record BookingOrderPair(Booking booking, ServiceOrder order) {
    }
}
