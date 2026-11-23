package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import com.pafisolutions.tarjepafi.credentials.application.SettingsService;
import com.pafisolutions.tarjepafi.credentials.interfaces.rest.CredentialResources.SettingsResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings")
public class SettingsController {

  private final SettingsService settings;

  public SettingsController(SettingsService settings) {
    this.settings = settings;
  }

  @GetMapping
  @Operation(
      summary = "Campus settings: term, enrollment deadline and default attendance tolerance",
      description = "Any signed-in user. 404 SETTINGS_NOT_FOUND when nothing is configured.")
  public SettingsResource get() {
    return SettingsResource.from(settings.current());
  }
}
