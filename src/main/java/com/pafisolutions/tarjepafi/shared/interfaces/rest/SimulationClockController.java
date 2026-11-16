package com.pafisolutions.tarjepafi.shared.interfaces.rest;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets the web app's NFC simulator read and move the campus clock during demos. Moving it is only
 * possible when {@code tarjepafi.clock.mode=simulated}.
 */
@RestController
@RequestMapping("/api/v1/simulation/clock")
public class SimulationClockController {

  public record ClockResource(LocalDateTime now, boolean simulated) {}

  public record MoveClockResource(@NotNull LocalDateTime now) {}

  private final CampusClock clock;

  public SimulationClockController(CampusClock clock) {
    this.clock = clock;
  }

  @GetMapping
  public ClockResource current() {
    return new ClockResource(clock.now(), clock.isSimulated());
  }

  @PutMapping
  public ClockResource move(@Valid @RequestBody MoveClockResource body) {
    if (!clock.isSimulated()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "The campus clock follows real time");
    }
    clock.moveTo(body.now());
    return current();
  }
}
