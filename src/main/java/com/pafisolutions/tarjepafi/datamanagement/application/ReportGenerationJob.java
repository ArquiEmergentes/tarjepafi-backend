package com.pafisolutions.tarjepafi.datamanagement.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TS26: reports are created GENERATING and consolidated here, so the request answers at once and a restart
 * cannot leave a report stuck (it is picked up on the next run).
 */
@Component
public class ReportGenerationJob {

  private final ReportService reports;

  public ReportGenerationJob(ReportService reports) {
    this.reports = reports;
  }

  @Scheduled(fixedDelayString = "${tarjepafi.reports.job-interval-ms:1000}")
  public void run() {
    reports.completeDue();
  }
}
