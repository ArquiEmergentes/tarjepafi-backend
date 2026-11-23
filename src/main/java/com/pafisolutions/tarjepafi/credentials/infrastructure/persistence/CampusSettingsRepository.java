package com.pafisolutions.tarjepafi.credentials.infrastructure.persistence;

import com.pafisolutions.tarjepafi.credentials.domain.model.CampusSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusSettingsRepository extends JpaRepository<CampusSettings, String> {}
