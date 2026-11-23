package com.pafisolutions.tarjepafi.shared.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled} jobs (enrollment verification, connectivity monitor). */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {}
