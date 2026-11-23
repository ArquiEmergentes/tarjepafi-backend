package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TelemetrySample;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRepository extends JpaRepository<TelemetrySample, String> {

  List<TelemetrySample> findByReaderIdOrderByCapturedAtDescIdDesc(String readerId, Pageable page);
}
