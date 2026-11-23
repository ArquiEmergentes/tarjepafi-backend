package com.pafisolutions.tarjepafi.credentials.infrastructure.persistence;

import com.pafisolutions.tarjepafi.credentials.domain.model.Credential;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialRepository extends JpaRepository<Credential, String> {

  Optional<Credential> findByCardUidIgnoreCase(String cardUid);

  List<Credential> findByCardHolderId(String cardHolderId);

  Optional<Credential> findFirstByCardHolderIdAndStatusAndIdNot(
      String cardHolderId, CredentialStatus status, String id);

  @Query(
      """
      select c from Credential c
      where (:status is null or c.status = :status)
        and (:holderId is null or c.cardHolderId = :holderId)
      order by c.issuedAt desc, c.id
      """)
  List<Credential> search(
      @Param("status") CredentialStatus status, @Param("holderId") String holderId);
}
