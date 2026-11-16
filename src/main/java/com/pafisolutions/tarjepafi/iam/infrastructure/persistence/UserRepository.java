package com.pafisolutions.tarjepafi.iam.infrastructure.persistence;

import com.pafisolutions.tarjepafi.iam.domain.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
