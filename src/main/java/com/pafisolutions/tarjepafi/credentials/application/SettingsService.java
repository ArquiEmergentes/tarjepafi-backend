package com.pafisolutions.tarjepafi.credentials.application;

import com.pafisolutions.tarjepafi.credentials.domain.model.CampusSettings;
import com.pafisolutions.tarjepafi.credentials.infrastructure.persistence.CampusSettingsRepository;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

  private final CampusSettingsRepository settings;

  public SettingsService(CampusSettingsRepository settings) {
    this.settings = settings;
  }

  @Transactional(readOnly = true)
  public CampusSettings current() {
    return settings
        .findById(CampusSettings.ID)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "SETTINGS_NOT_FOUND", "The campus settings are not configured"));
  }
}
