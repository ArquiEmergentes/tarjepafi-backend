package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Incident;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.IncidentRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

  private final IncidentRepository incidents;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public IncidentService(
      IncidentRepository incidents, CampusClock clock, ApplicationEventPublisher events) {
    this.incidents = incidents;
    this.clock = clock;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<Incident> list(String readerId, Boolean open) {
    return incidents.search(readerId, open);
  }

  /**
   * Opens an incident unless the reader already has an open one of the same type, so a reader that keeps
   * failing does not fill the list.
   */
  @Transactional
  public void openIfAbsent(String readerId, IncidentType type, String description, LocalDateTime at) {
    if (!incidents.findByReaderIdAndTypeAndResolvedAtIsNull(readerId, type).isEmpty()) {
      return;
    }
    Incident incident =
        incidents.save(new Incident("inc-" + UUID.randomUUID(), readerId, type, at, description, null));
    events.publishEvent(
        new IntegrationEvent(
            "iot.incident-opened",
            java.util.Map.of(
                "incidentId", incident.getId(),
                "readerId", readerId,
                "type", type.name(),
                "description", incident.getDescription(),
                "detectedAt", at.toString())));
  }

  /** Resolves the open incidents of a type of a reader, for example DEVICE_OFFLINE when it reconnects. */
  @Transactional
  public void resolveOpen(String readerId, IncidentType type, LocalDateTime at) {
    incidents.findByReaderIdAndTypeAndResolvedAtIsNull(readerId, type).forEach(i -> i.resolve(at));
  }

  @Transactional
  public Incident resolve(String incidentId) {
    Incident incident =
        incidents
            .findById(incidentId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.NOT_FOUND, "INCIDENT_NOT_FOUND", "Unknown incident " + incidentId));
    if (!incident.isOpen()) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "INCIDENT_ALREADY_RESOLVED", "The incident is already resolved");
    }
    incident.resolve(clock.now());
    return incident;
  }
}
