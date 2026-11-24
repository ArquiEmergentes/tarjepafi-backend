package com.pafisolutions.tarjepafi.datamanagement.domain.model;

/**
 * Summary of a report (rule 6.F).
 *
 * @param attendanceRate percentage of students present or late over the resolved attendances (0 without any)
 * @param peakHour "HH:00" with the most card readings, or null without readings
 */
public record ReportSummary(
    double attendanceRate,
    int accessGranted,
    int accessDenied,
    String peakHour,
    int readersWithIncidents) {}
