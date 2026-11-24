package com.pafisolutions.tarjepafi.iotmonitoring.application;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts a reader shows, in Spanish (the language of the campus readers), keyed like the web app's
 * {@code tap.*} translations. A text may have {{params}} and parts separated by " · ", one per display line.
 * Generated from {@code webapp/public/i18n/es.json}; {@code tap.received} is the only addition (phase 4).
 */
public final class DisplayMessages {

  private static final Pattern PARAM = Pattern.compile("\\{\\{(\\w+)\\}\\}");
  private static final String LINE_SEPARATOR = " · ";

  private static final Map<String, String> TEXTS =
      Map.ofEntries(
          Map.entry("tap.readerOutOfService", "Lector fuera de servicio"),
          Map.entry("tap.cardNotRegistered", "Tarjeta no registrada"),
          Map.entry("tap.cardInactive", "Tarjeta inactiva. Acércate a servicios universitarios"),
          Map.entry("tap.noClassInProgress", "No hay una clase en curso en esta aula"),
          Map.entry("tap.notEnrolled", "No estás matriculado en esta clase"),
          Map.entry("tap.alreadyRegistered", "Tu asistencia ya fue registrada"),
          Map.entry("tap.attendanceTaken", "Asistencia tomada · {{course}} · {{time}}"),
          Map.entry("tap.noAssignedClass", "No tienes una clase asignada en esta aula a esta hora"),
          Map.entry("tap.teacherArrival", "Llegada registrada · {{course}} · {{count}} asistencias confirmadas"),
          Map.entry("tap.teacherArrivalNoRecords", "Llegada registrada · {{course}} · No existen asistencias registradas"),
          Map.entry("tap.teacherExit", "Salida registrada · Duración {{duration}}"),
          Map.entry("tap.exitAlreadyRegistered", "Tu salida ya fue registrada"),
          Map.entry("tap.notInvited", "No estás convocado a esta reunión"),
          Map.entry("tap.meetingRegistered", "Asistencia registrada · {{title}}"),
          Map.entry("tap.meetingRegisteredLate", "Asistencia registrada con tardanza · {{title}}"),
          Map.entry("tap.accessGranted", "Acceso concedido"),
          Map.entry("tap.accessDenied", "Acceso denegado"),
          Map.entry("tap.shiftOpened", "Entrada registrada · Jornada iniciada"),
          Map.entry("tap.shiftAlreadyOpen", "Tu jornada ya está abierta"),
          Map.entry("tap.shiftClosed", "Salida registrada · {{worked}} trabajadas"),
          Map.entry("tap.noEntryFound", "No se encontró una entrada registrada"),
          Map.entry("tap.reservationActivated", "Reserva activada · {{space}} · hasta las {{until}}"),
          Map.entry("tap.reservationAlreadyActive", "Tu reserva ya está activa"),
          Map.entry("tap.reservationNotStarted", "Tu reserva aún no comienza · inicia a las {{start}}"),
          Map.entry("tap.reservationExpired", "Reserva vencida · pasaron los 10 minutos de tolerancia"),
          Map.entry("tap.noReservation", "No tienes una reserva activa para este espacio"),
          Map.entry("tap.duplicateEvent", "Evento ya procesado · no se registró de nuevo"),
          Map.entry("tap.received", "Lectura recibida"));

  private DisplayMessages() {}

  /** The text for {@code key} with its params filled in; the key itself when it is unknown. */
  public static String render(String key, Map<String, ?> params) {
    String template = TEXTS.get(key);
    if (template == null) {
      return key;
    }
    Matcher matcher = PARAM.matcher(template);
    StringBuilder text = new StringBuilder();
    while (matcher.find()) {
      Object value = params == null ? null : params.get(matcher.group(1));
      matcher.appendReplacement(text, Matcher.quoteReplacement(value == null ? "" : value.toString()));
    }
    matcher.appendTail(text);
    return text.toString();
  }

  /** The text split into display lines, each transliterated to plain ASCII for the reader's LCD. */
  public static List<String> lines(String text) {
    return Arrays.stream(text.split(Pattern.quote(LINE_SEPARATOR))).map(DisplayMessages::ascii).toList();
  }

  static String ascii(String text) {
    String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return plain.replace("¿", "").replace("¡", "").replaceAll("[^\\x20-\\x7E]", "?");
  }
}
