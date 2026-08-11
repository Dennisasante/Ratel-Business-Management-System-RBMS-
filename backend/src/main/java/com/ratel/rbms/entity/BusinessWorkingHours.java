package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * One row per day a business takes online bookings on — presence of a row
 * for a given dayOfWeek means "open that day"; absence means closed. Lets a
 * day like Sunday carry different hours than the rest of the week, unlike
 * the single business-wide start/end pair this replaced (see V19).
 */
@Entity
@Table(name = "business_working_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessWorkingHours {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // ISO weekday, 1=Mon..7=Sun.
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Mon-Sat 9-6 — applied whenever a business has never saved any hours
    // rows at all, so bookings (and the settings page that describes them)
    // don't silently look closed just because nobody's opened Booking
    // Settings yet. Once a business has ANY row, absence of a row for a
    // given day means that day is closed — no per-day fallback beyond this
    // all-or-nothing default. Shared by BookingService (enforcement/widget)
    // and BookingSettingsService (what the owner sees) so the two can never
    // disagree about what "default" means.
    public static List<BusinessWorkingHours> defaultHours() {
        return List.of(1, 2, 3, 4, 5, 6).stream()
                .map(day -> BusinessWorkingHours.builder()
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(18, 0))
                        .build())
                .toList();
    }
}
