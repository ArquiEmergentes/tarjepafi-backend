package com.pafisolutions.tarjepafi.credentials.infrastructure.persistence;

import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.HolderRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardHolderRepository extends JpaRepository<CardHolder, String> {

  Optional<CardHolder> findByCodeIgnoreCase(String code);

  @Query(
      """
      select h from CardHolder h
      where (:role is null or h.role = :role)
        and (lower(h.fullName) like :pattern or lower(h.code) like :pattern)
      order by h.fullName
      """)
  List<CardHolder> searchByPattern(@Param("role") HolderRole role, @Param("pattern") String pattern);

  /** Holders of {@code role} (any when null) whose name or code contains {@code query} (any when blank). */
  default List<CardHolder> search(HolderRole role, String query) {
    String text = query == null ? "" : query.trim().toLowerCase();
    return searchByPattern(role, "%" + text + "%");
  }
}
