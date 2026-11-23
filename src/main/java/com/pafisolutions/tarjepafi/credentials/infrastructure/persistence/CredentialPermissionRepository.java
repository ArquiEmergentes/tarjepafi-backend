package com.pafisolutions.tarjepafi.credentials.infrastructure.persistence;

import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialPermissionRepository extends JpaRepository<CredentialPermission, String> {

  List<CredentialPermission> findByCredentialIdOrderBySpaceId(String credentialId);

  Optional<CredentialPermission> findByCredentialIdAndSpaceId(String credentialId, String spaceId);
}
