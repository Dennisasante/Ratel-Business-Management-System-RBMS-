package com.ratel.rbms.service;

import com.ratel.rbms.dto.DayCount;
import com.ratel.rbms.dto.PlatformBillingStatusCount;
import com.ratel.rbms.dto.PlatformPlanMixEntry;
import com.ratel.rbms.dto.PlatformStatsResponse;
import com.ratel.rbms.entity.ActivityLog;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.SubscriptionPlan;
import com.ratel.rbms.repository.ActivityLogRepository;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomWigRequestRepository;
import com.ratel.rbms.repository.EcommerceOrderRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.SubscriptionPlanRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlatformStatsService {

    private static final int WINDOW_DAYS = 30;

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BookingRepository bookingRepository;
    private final EcommerceOrderRepository ecommerceOrderRepository;
    private final CustomWigRequestRepository customWigRequestRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public PlatformStatsService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            ActivityLogRepository activityLogRepository,
            BookingRepository bookingRepository,
            EcommerceOrderRepository ecommerceOrderRepository,
            CustomWigRequestRepository customWigRequestRepository,
            ServiceOrderRepository serviceOrderRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
        this.bookingRepository = bookingRepository;
        this.ecommerceOrderRepository = ecommerceOrderRepository;
        this.customWigRequestRepository = customWigRequestRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public PlatformStatsResponse getStats() {
        Instant cutoff = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);

        int totalBusinesses = (int) businessRepository.count();
        int activeBusinesses = (int) businessRepository.countByActive(true);
        int totalUsers = (int) userRepository.count();
        // Every business's own money collected from their own customers — Sale,
        // ServiceOrder, Booking, PurchaseOrder, CustomWigRequest, or anything
        // added later — not just Sale.totalAmount, which used to leave every
        // other entity type out of "platform revenue" entirely.
        BigDecimal totalRevenue = paymentTransactionRepository.sumAmountAllTime(PaymentTransaction.Direction.INCOMING, "SUCCESS");

        List<Business> recentBusinesses = businessRepository.findAllByCreatedAtAfter(cutoff);
        Map<String, Long> signupsByDay = recentBusinesses.stream()
                .collect(Collectors.groupingBy(b -> dayKey(b.getCreatedAt()), TreeMap::new, Collectors.counting()));

        List<ActivityLog> recentActivity = activityLogRepository.findAllByCreatedAtAfter(cutoff);
        Map<String, Long> activityByDay = recentActivity.stream()
                .collect(Collectors.groupingBy(a -> dayKey(a.getCreatedAt()), TreeMap::new, Collectors.counting()));

        List<PlatformBillingStatusCount> billingStatusBreakdown = businessRepository.countGroupedByBillingStatus().stream()
                .map(row -> new PlatformBillingStatusCount(row.getBillingStatus().name(), row.getTotal()))
                .toList();

        // Non-test usage, platform-wide — Booking/CustomWigRequest have a test-mode
        // flag (same one BookingService checks before sending real notifications);
        // EcommerceOrder/ServiceOrder don't, so their plain count() is already real usage.
        long totalBookings = bookingRepository.countByTest(false);
        long totalEcommerceOrders = ecommerceOrderRepository.count();
        long totalCustomWigRequests = customWigRequestRepository.countByTest(false);
        long totalServiceOrders = serviceOrderRepository.count();

        return new PlatformStatsResponse(
                totalBusinesses,
                activeBusinesses,
                totalUsers,
                totalRevenue,
                fillGaps(signupsByDay),
                fillGaps(activityByDay),
                billingStatusBreakdown,
                buildPlanMix(),
                totalBookings,
                totalEcommerceOrders,
                totalCustomWigRequests,
                totalServiceOrders
        );
    }

    private List<PlatformPlanMixEntry> buildPlanMix() {
        List<BusinessRepository.PlanBusinessCount> totalCounts = businessRepository.countGroupedBySubscriptionPlan();
        if (totalCounts.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> activeCounts = businessRepository.countActiveGroupedBySubscriptionPlan().stream()
                .collect(Collectors.toMap(BusinessRepository.PlanBusinessCount::getPlanId, BusinessRepository.PlanBusinessCount::getTotal));

        Map<UUID, SubscriptionPlan> plans = subscriptionPlanRepository
                .findAllById(totalCounts.stream().map(BusinessRepository.PlanBusinessCount::getPlanId).toList())
                .stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, p -> p));

        return totalCounts.stream()
                .map(row -> {
                    SubscriptionPlan plan = plans.get(row.getPlanId());
                    String planName = plan != null ? plan.getName() : "Unknown plan";
                    long activeCount = activeCounts.getOrDefault(row.getPlanId(), 0L);
                    BigDecimal mrr = plan != null
                            ? plan.getPrice().multiply(BigDecimal.valueOf(activeCount))
                            : BigDecimal.ZERO;
                    return new PlatformPlanMixEntry(planName, row.getTotal(), mrr);
                })
                .toList();
    }

    private String dayKey(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Fills in zero-count days so the chart doesn't have gaps for quiet days. */
    private List<DayCount> fillGaps(Map<String, Long> counts) {
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(WINDOW_DAYS - 1L);
        return java.util.stream.IntStream.range(0, WINDOW_DAYS)
                .mapToObj(start::plusDays)
                .map(date -> new DayCount(date.toString(), counts.getOrDefault(date.toString(), 0L)))
                .toList();
    }
}
