package com.ratel.rbms.service;

import com.ratel.rbms.dto.DayCount;
import com.ratel.rbms.dto.PlatformStatsResponse;
import com.ratel.rbms.entity.ActivityLog;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.repository.ActivityLogRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.SaleRepository;
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
import java.util.stream.Collectors;

@Service
public class PlatformStatsService {

    private static final int WINDOW_DAYS = 30;

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final ActivityLogRepository activityLogRepository;

    public PlatformStatsService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            SaleRepository saleRepository,
            ActivityLogRepository activityLogRepository
    ) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.saleRepository = saleRepository;
        this.activityLogRepository = activityLogRepository;
    }

    public PlatformStatsResponse getStats() {
        List<Business> businesses = businessRepository.findAll();
        int totalUsers = userRepository.findAll().size();

        BigDecimal totalRevenue = saleRepository.findAll().stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant cutoff = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);

        Map<String, Long> signupsByDay = businesses.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(cutoff))
                .collect(Collectors.groupingBy(b -> dayKey(b.getCreatedAt()), TreeMap::new, Collectors.counting()));

        List<ActivityLog> recentActivity = activityLogRepository.findAllByCreatedAtAfter(cutoff);
        Map<String, Long> activityByDay = recentActivity.stream()
                .collect(Collectors.groupingBy(a -> dayKey(a.getCreatedAt()), TreeMap::new, Collectors.counting()));

        return new PlatformStatsResponse(
                businesses.size(),
                (int) businesses.stream().filter(Business::isActive).count(),
                totalUsers,
                totalRevenue,
                fillGaps(signupsByDay),
                fillGaps(activityByDay)
        );
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
