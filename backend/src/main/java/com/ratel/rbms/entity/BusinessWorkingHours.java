package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
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
}
