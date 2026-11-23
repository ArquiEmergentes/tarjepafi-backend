package com.pafisolutions.tarjepafi.credentials.infrastructure.persistence;

import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialStatusChangeRepository
    extends JpaRepository<CredentialStatusChange, String> {

  List<CredentialStatusChange> findByCredentialIdOrderByChangedAtDescIdDesc(String credentialId);
}
