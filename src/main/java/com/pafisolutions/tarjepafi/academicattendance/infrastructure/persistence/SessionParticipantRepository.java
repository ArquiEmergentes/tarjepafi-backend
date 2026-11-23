package com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, String> {

  List<SessionParticipant> findBySessionId(String sessionId);

  List<SessionParticipant> findByCardHolderId(String cardHolderId);

  boolean existsBySessionIdAndCardHolderId(String sessionId, String cardHolderId);
}
