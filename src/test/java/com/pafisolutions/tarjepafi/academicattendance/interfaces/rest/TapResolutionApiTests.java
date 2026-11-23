package com.pafisolutions.tarjepafi.academicattendance.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 5 Verify block against real PostgreSQL and RabbitMQ: the demo walkthrough of the old web app (its
 * README section 4) and the scenarios of its acceptance features 01 to 04 and 07, replayed through the
 * simulated tap endpoint, which travels the real pipeline. The tests share one database and the campus
 * clock, so the clock is put back at the demo start before each test and the tests are ordered. Skipped
 * when Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TapResolutionApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  private static final String START = "2026-10-07T09:50:00";

  // Cards of the seed (see the old web app README, section 6).
  private static final String STUDENT = "04:3D:9B:12:8F"; // U202312966, enrolled in Aplicaciones Web
  private static final String STUDENT_NOT_ENROLLED = "04:73:3C:F2:5C"; // U202310137
  private static final String STUDENT_LATE = "04:A0:3C:AE:C6"; // U202310685, enrolled
  private static final String STUDENT_NO_CLASS = "04:59:13:A4:5F"; // U202310411
  private static final String STUDENT_ABSENT = "04:EE:6F:CE:8D"; // U202311233, enrolled
  private static final String TEACHER = "04:56:76:31:15"; // D0001
  private static final String TEACHER_NOT_INVITED = "04:6F:41:C8:5F"; // D0004
  private static final String STAFF = "04:F7:D9:2D:73"; // T0001, shift open since 06:56
  private static final String STAFF_DENIED = "04:9A:99:41:AC"; // T0003, DENY in the server room
  private static final String UNKNOWN = "04:FF:12:AB:90";
  private static final String BLOCKED = "04:4D:DA:3D:91";

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;

  MockMvc mvc;
  String admin;
  String teacher;
  String otherTeacher;

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    admin = signIn("admin@upc.edu.pe", "Admin123*");
    teacher = signIn("docente@upc.edu.pe", "Docente123*");
    otherTeacher = signIn("lparedes@upc.edu.pe", "Docente123*");
    moveClock(START);
  }

  // ---------- Demo walkthrough (acceptance 01, 02, 04) ----------

  @Test
  @Order(1)
  void demoWalkthrough_studentTeacherLateStudentExit_givesTheOutcomesOfTheOldWebApp() throws Exception {
    // Step 1: the student is enrolled and the class is in progress; the mark waits for the teacher.
    JsonNode first = tap("RDR-A-301", STUDENT);
    assertThat(first.path("outcome").asString()).isEqualTo("ATTENDANCE");
    assertThat(first.path("messageKey").asString()).isEqualTo("tap.attendanceTaken");
    assertThat(first.path("displayMessage").asString())
        .isEqualTo("Asistencia tomada · Aplicaciones Web · 10:00–11:30");
    assertThat(rosterStatus("ch-u202312966")).isEqualTo("PENDING");

    // Acceptance 01: a second tap, a student who is not enrolled, an empty classroom, an unknown card.
    assertThat(tap("RDR-A-301", STUDENT).path("messageKey").asString()).isEqualTo("tap.alreadyRegistered");
    JsonNode notEnrolled = tap("RDR-A-301", STUDENT_NOT_ENROLLED);
    assertThat(notEnrolled.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(notEnrolled.path("displayMessage").asString()).isEqualTo("No estás matriculado en esta clase");
    assertThat(tap("RDR-A-303", STUDENT_NO_CLASS).path("displayMessage").asString())
        .isEqualTo("No hay una clase en curso en esta aula");
    assertThat(tap("RDR-A-301", UNKNOWN).path("displayMessage").asString()).isEqualTo("Tarjeta no registrada");
    assertThat(tap("RDR-A-301", BLOCKED).path("messageKey").asString()).isEqualTo("tap.cardInactive");

    // Step 2: the teacher arrives; the pending mark is confirmed with its own time.
    JsonNode arrival = tap("RDR-A-301", TEACHER);
    assertThat(arrival.path("messageKey").asString()).isEqualTo("tap.teacherArrival");
    assertThat(arrival.path("displayMessage").asString())
        .isEqualTo(
            "Llegada registrada · Aplicaciones Web – Desarrollo de Software · 1 asistencias confirmadas");
    assertThat(rosterStatus("ch-u202312966")).isEqualTo("PRESENT");
    mvc.perform(as(get("/api/v1/class-sessions/ses-02-7"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.session.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.teacherRecord.status").value("PRESENT"));

    // Step 3: 10:20, a student of the pending-enrollment list taps and is LATE at once.
    moveClock("2026-10-07T10:20:00");
    assertThat(tap("RDR-A-301", STUDENT_LATE).path("messageKey").asString()).isEqualTo("tap.attendanceTaken");
    assertThat(rosterStatus("ch-u202310685")).isEqualTo("LATE");

    // Acceptance 02 / rule 6.E: the teacher justifies an absence with a reason; no reason is refused.
    mvc.perform(
            as(put("/api/v1/class-sessions/ses-02-7/attendance/ch-u202310411"), teacher)
                .content("{\"status\":\"JUSTIFIED\",\"reason\":\"Certificado medico\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("JUSTIFIED"))
        .andExpect(jsonPath("$.source").value("TEACHER_EDIT"))
        .andExpect(jsonPath("$.editReason").value("Certificado medico"));
    mvc.perform(
            as(put("/api/v1/class-sessions/ses-02-7/attendance/ch-u202310822"), teacher)
                .content("{\"status\":\"PRESENT\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            as(put("/api/v1/class-sessions/ses-02-7/attendance/ch-u202310137"), teacher)
                .content("{\"status\":\"PRESENT\",\"reason\":\"not in the class\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"));

    // Step 4: 11:20, the teacher taps again: the exit, 1 h 30 min, and the session closes.
    moveClock("2026-10-07T11:20:00");
    JsonNode exit = tap("RDR-A-301", TEACHER);
    assertThat(exit.path("messageKey").asString()).isEqualTo("tap.teacherExit");
    assertThat(exit.path("displayMessage").asString()).isEqualTo("Salida registrada · Duración 1 h 30 min");
    assertThat(tap("RDR-A-301", TEACHER).path("messageKey").asString()).isEqualTo("tap.exitAlreadyRegistered");

    mvc.perform(as(get("/api/v1/class-sessions/ses-02-7"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.session.status").value("FINISHED"))
        .andExpect(jsonPath("$.counts.present").value(1))
        .andExpect(jsonPath("$.counts.late").value(1))
        .andExpect(jsonPath("$.counts.justified").value(1))
        .andExpect(jsonPath("$.counts.absent").value(17))
        .andExpect(jsonPath("$.counts.pending").value(0))
        .andExpect(jsonPath("$.counts.unmarked").value(0));
    assertThat(rosterStatus("ch-u202311233")).isEqualTo("ABSENT");

    // The teacher's own history and the polling read.
    mvc.perform(as(get("/api/v1/class-sessions/attendance/mine"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].record.durationMinutes").value(90))
        .andExpect(jsonPath("$[0].course.name").value("Aplicaciones Web"));
    JsonNode page = getJson("/api/v1/class-sessions/ses-02-7/attendance?limit=3", teacher);
    assertThat(page.path("items")).hasSize(3);
    String cursor = page.path("nextCursor").asString();
    assertThat(getJson("/api/v1/class-sessions/ses-02-7/attendance?after=" + cursor, teacher).path("items"))
        .isEmpty();

    // Someone else's session and the day list.
    mvc.perform(as(get("/api/v1/class-sessions/ses-02-7"), otherTeacher))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_YOUR_SESSION"));
    mvc.perform(as(get("/api/v1/class-sessions/mine?date=2026-10-07"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].session.id").value("ses-02-7"));
    mvc.perform(as(get("/api/v1/class-sessions/mine"), admin)).andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void confirmPending_whenTheTeacherNeverTapped_confirmsEachByItsOwnTime() throws Exception {
    // ses-04-8 (Thursday 14:00, A-302): a student taps, the teacher never does, the session closes by itself.
    moveClock("2026-10-08T13:55:00");
    assertThat(tap("RDR-A-302", STUDENT).path("messageKey").asString()).isEqualTo("tap.attendanceTaken");
    mvc.perform(as(post("/api/v1/class-sessions/ses-04-8/confirm-pending"), teacher))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_STARTED"));

    moveClock("2026-10-08T15:50:00");
    mvc.perform(as(get("/api/v1/class-sessions/mine?date=2026-10-08"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].session.status").value("FINISHED"))
        .andExpect(jsonPath("$[0].counts.pending").value(1));
    mvc.perform(as(post("/api/v1/class-sessions/ses-04-8/confirm-pending"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed").value(1));
    mvc.perform(as(get("/api/v1/class-sessions/ses-04-8"), teacher))
        .andExpect(jsonPath("$.counts.present").value(1))
        .andExpect(jsonPath("$.counts.pending").value(0));
    mvc.perform(as(post("/api/v1/class-sessions/ses-04-8/confirm-pending"), teacher))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTHING_TO_CONFIRM"));
  }

  // ---------- Acceptance 07: meetings ----------

  @Test
  @Order(3)
  void meeting_invitedTeacherIsRegistered_othersAreRefused() throws Exception {
    moveClock("2026-10-08T15:50:00");

    JsonNode invited = tap("RDR-D-005", TEACHER);
    assertThat(invited.path("outcome").asString()).isEqualTo("ATTENDANCE");
    assertThat(invited.path("messageKey").asString()).isEqualTo("tap.meetingRegistered");
    assertThat(tap("RDR-D-005", TEACHER).path("messageKey").asString()).isEqualTo("tap.alreadyRegistered");

    JsonNode notInvited = tap("RDR-D-005", TEACHER_NOT_INVITED);
    assertThat(notInvited.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(notInvited.path("displayMessage").asString()).isEqualTo("No estás convocado a esta reunión");

    // A student is not attendance's business: the space rules refuse the role.
    JsonNode student = tap("RDR-D-005", STUDENT);
    assertThat(student.path("messageKey").asString()).isEqualTo("tap.accessDenied");

    mvc.perform(as(get("/api/v1/class-sessions/meetings/mine"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.session.id=='mtg-02')].record.status").value("PRESENT"));

    // Without a meeting in progress the same teacher simply gets into the room.
    moveClock(START);
    JsonNode access = tap("RDR-D-005", TEACHER);
    assertThat(access.path("outcome").asString()).isEqualTo("ACCESS");
    assertThat(access.path("messageKey").asString()).isEqualTo("tap.accessGranted");
  }

  // ---------- Acceptance 03: access control ----------

  @Test
  @Order(4)
  void spaceAccess_exceptionsRolesMaintenanceAndPolicy() throws Exception {
    // The student has an ALLOW exception for the laboratory E; another student has none.
    assertThat(tap("RDR-D-002", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessGranted");
    JsonNode denied = tap("RDR-D-002", STUDENT_NOT_ENROLLED);
    assertThat(denied.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(denied.path("displayMessage").asString()).isEqualTo("Acceso denegado");
    assertThat(deniedReasons("ch-u202310137")).contains("UNAUTHORIZED_ROLE");

    // A DENY exception wins over the role.
    assertThat(tap("RDR-D-003", STAFF_DENIED).path("messageKey").asString()).isEqualTo("tap.accessDenied");
    assertThat(tap("RDR-D-003", STAFF).path("messageKey").asString()).isEqualTo("tap.accessGranted");

    // A space under maintenance denies everybody.
    assertThat(tap("RDR-D-004", STAFF).path("messageKey").asString()).isEqualTo("tap.accessDenied");
    assertThat(deniedReasons("ch-t0001")).contains("SPACE_IN_MAINTENANCE");

    // A card that is not registered leaves a denied record at a restricted reader (and none in a classroom).
    assertThat(tap("RDR-D-002", UNKNOWN).path("messageKey").asString()).isEqualTo("tap.cardNotRegistered");
    eventually(
        () ->
            getJson("/api/v1/access-records?spaceId=sp-lab-e&decision=DENIED", admin)
                .path("items")
                .toString()
                .contains("CARD_NOT_REGISTERED"));

    // TS18 / TS28: the administrator puts a space in maintenance and changes a policy; taps follow.
    mvc.perform(as(patch("/api/v1/spaces/sp-lab-c/status"), admin).content("{\"status\":\"MAINTENANCE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    assertThat(tap("RDR-D-001", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessDenied");
    mvc.perform(as(patch("/api/v1/spaces/sp-lab-c/status"), admin).content("{\"status\":\"AVAILABLE\"}"))
        .andExpect(status().isOk());
    assertThat(tap("RDR-D-001", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessGranted");

    mvc.perform(
            as(patch("/api/v1/spaces/sp-bib/access-policy"), admin)
                .content("{\"accessPolicy\":\"RESTRICTED\",\"allowedRoles\":[\"STAFF\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowedRoles[0]").value("STAFF"));
    assertThat(tap("RDR-D-006", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessDenied");
    mvc.perform(
            as(patch("/api/v1/spaces/sp-bib/access-policy"), admin)
                .content("{\"accessPolicy\":\"OPEN\",\"allowedRoles\":[\"STUDENT\",\"TEACHER\",\"STAFF\"]}"))
        .andExpect(status().isOk());
    assertThat(tap("RDR-D-006", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessGranted");
    mvc.perform(
            as(patch("/api/v1/spaces/sp-bib/access-policy"), admin)
                .content("{\"accessPolicy\":\"OPEN\",\"allowedRoles\":[\"PRINCIPAL\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    mvc.perform(as(patch("/api/v1/spaces/nope/status"), admin).content("{\"status\":\"AVAILABLE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    mvc.perform(as(patch("/api/v1/spaces/sp-bib/status"), teacher).content("{\"status\":\"AVAILABLE\"}"))
        .andExpect(status().isForbidden());
  }

  // ---------- Acceptance 04: turnstiles and work shifts ----------

  @Test
  @Order(5)
  void turnstile_staffShiftsAndCampusPresences() throws Exception {
    // T0001 has been in since 06:56: the exit closes the shift (09:50 - 06:56:33 = 173 min).
    JsonNode exit = tap("RDR-T-003", STAFF);
    assertThat(exit.path("outcome").asString()).isEqualTo("WORK_SHIFT");
    assertThat(exit.path("displayMessage").asString()).isEqualTo("Salida registrada · 2 h 53 min trabajadas");
    assertThat(tap("RDR-T-003", STAFF).path("messageKey").asString()).isEqualTo("tap.exitAlreadyRegistered");
    assertThat(tap("RDR-T-001", STAFF).path("messageKey").asString()).isEqualTo("tap.shiftOpened");
    assertThat(tap("RDR-T-001", STAFF).path("messageKey").asString()).isEqualTo("tap.shiftAlreadyOpen");
    mvc.perform(as(get("/api/v1/work-shifts?cardHolderId=ch-t0001&status=OPEN"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("OPEN"));

    // A student entering leaves a CAMPUS_ENTRY record, is counted inside and has an open stay.
    int before = getJson("/api/v1/spaces/sp-ent-01/occupancy", admin).path("currentOccupancy").asInt();
    JsonNode entry = tap("RDR-T-001", STUDENT);
    assertThat(entry.path("outcome").asString()).isEqualTo("ACCESS");
    JsonNode occupancy = getJson("/api/v1/spaces/sp-ent-01/occupancy", admin);
    assertThat(occupancy.path("tracked").asBoolean()).isTrue();
    int inside = occupancy.path("currentOccupancy").asInt();
    assertThat(inside).isGreaterThanOrEqualTo(before);
    assertThat(getJson("/api/v1/spaces/sp-lab-c/occupancy", admin).path("tracked").asBoolean()).isFalse();

    // A tap stamps the simulated time minus its transit, so the range starts a little before 09:50.
    String range = "from=2026-10-07T09:45:00&to=2026-10-07T23:59:59";
    JsonNode open = getJson("/api/v1/card-holders/ch-u202312966/campus-presences?" + range, admin);
    assertThat(open.path("items").get(0).path("status").asString()).isEqualTo("OPEN");
    assertThat(open.path("totalMinutes").asInt()).isZero();

    moveClock("2026-10-07T10:30:00");
    assertThat(tap("RDR-T-003", STUDENT).path("messageKey").asString()).isEqualTo("tap.accessGranted");
    JsonNode closed = getJson("/api/v1/card-holders/ch-u202312966/campus-presences?" + range, admin);
    assertThat(closed.path("items").get(0).path("status").asString()).isEqualTo("CLOSED");
    assertThat(closed.path("totalMinutes").asInt()).isEqualTo(40);
    assertThat(getJson("/api/v1/spaces/sp-ent-01/occupancy", admin).path("currentOccupancy").asInt())
        .isEqualTo(inside - 1);
    mvc.perform(as(get("/api/v1/card-holders/ch-u202312966/campus-presences?from=2026-10-08T00:00:00&to=2026-10-07T00:00:00"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    assertThat(getJson("/api/v1/access-records?spaceId=sp-ent-01&cardHolderId=ch-u202312966", admin).path("items"))
        .isNotEmpty();
  }

  // ---------- TS18: registering spaces ----------

  @Test
  @Order(6)
  void createSpace_validAndInvalidRequests() throws Exception {
    String body =
        "{\"code\":\"EVT-01\",\"name\":\"Auditorio\",\"type\":\"EVENT_VENUE\",\"building\":\"Pabellón A\","
            + "\"floor\":1,\"capacity\":300,\"accessPolicy\":\"OPEN\",\"allowedRoles\":[\"STUDENT\",\"TEACHER\"]}";
    mvc.perform(as(post("/api/v1/spaces"), admin).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.type").value("EVENT_VENUE"));
    mvc.perform(as(post("/api/v1/spaces"), admin).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SPACE_CODE_EXISTS"));
    mvc.perform(as(post("/api/v1/spaces"), admin).content(body.replace("\"capacity\":300", "\"capacity\":0").replace("EVT-01", "EVT-02")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CAPACITY"));
    mvc.perform(as(post("/api/v1/spaces"), admin).content(body.replace("EVENT_VENUE", "GARAGE").replace("EVT-01", "EVT-03")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SPACE_TYPE"));
    mvc.perform(as(post("/api/v1/spaces"), teacher).content(body)).andExpect(status().isForbidden());
  }

  // ---------- Helpers ----------

  private JsonNode tap(String readerId, String cardUid) throws Exception {
    return mapper.readTree(
        mvc.perform(
                as(post("/api/v1/iot/readers/" + readerId + "/taps"), admin)
                    .content("{\"cardUid\":\"" + cardUid + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String rosterStatus(String cardHolderId) throws Exception {
    JsonNode roster = getJson("/api/v1/class-sessions/ses-02-7", teacher).path("roster");
    for (JsonNode row : roster) {
      if (row.path("cardHolderId").asString().equals(cardHolderId)) {
        return row.path("record").isNull() ? "NONE" : row.path("record").path("status").asString();
      }
    }
    return "NOT_IN_ROSTER";
  }

  private String deniedReasons(String cardHolderId) throws Exception {
    return getJson("/api/v1/access-records?decision=DENIED&cardHolderId=" + cardHolderId, admin)
        .path("items")
        .toString();
  }

  private void moveClock(String time) throws Exception {
    mvc.perform(as(put("/api/v1/simulation/clock"), admin).content("{\"now\":\"" + time + "\"}"))
        .andExpect(status().isOk());
  }

  private String signIn(String email, String password) throws Exception {
    return mapper
        .readTree(
            mvc.perform(
                    post("/api/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("token")
        .asString();
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
    return request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON);
  }

  private JsonNode getJson(String url, String token) throws Exception {
    return mapper.readTree(
        mvc.perform(as(get(url), token)).andReturn().getResponse().getContentAsString());
  }

  private static void eventually(Callable<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + 15_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (condition.call()) {
        return;
      }
      Thread.sleep(150);
    }
    assertThat(condition.call()).as("the condition did not hold within 15 seconds").isTrue();
  }
}
