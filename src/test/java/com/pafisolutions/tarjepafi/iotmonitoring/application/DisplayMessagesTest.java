package com.pafisolutions.tarjepafi.iotmonitoring.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DisplayMessagesTest {

  @Test
  void render_fillsTheParams() {
    assertThat(
            DisplayMessages.render(
                "tap.attendanceTaken", Map.of("course", "Aplicaciones Web", "time", "10:00")))
        .isEqualTo("Asistencia tomada · Aplicaciones Web · 10:00");
  }

  @Test
  void render_unknownKey_returnsTheKey() {
    assertThat(DisplayMessages.render("tap.nothing", Map.of())).isEqualTo("tap.nothing");
  }

  @Test
  void lines_splitOnTheSeparatorAndLoseTheAccents() {
    assertThat(
            DisplayMessages.lines(
                DisplayMessages.render("tap.cardInactive", Map.of())))
        .containsExactly("Tarjeta inactiva. Acercate a servicios universitarios");
    assertThat(DisplayMessages.lines("Llegada registrada · Programación · 3 asistencias"))
        .containsExactly("Llegada registrada", "Programacion", "3 asistencias");
  }
}
