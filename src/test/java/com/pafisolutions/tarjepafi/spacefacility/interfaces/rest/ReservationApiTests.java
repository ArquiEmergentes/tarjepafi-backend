package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 8 Verify block against real PostgreSQL, RabbitMQ and a mail catcher (Mailpit): a CONFIRMED reservation
 * without check-in becomes FORFEITED 10 minutes after its start by the simulated clock and the holder gets an
 * e-mail; a tap of the holder inside the window checks it in. The tests share one database and the campus clock,
 * so the clock is put back at the demo start before each test and the tests are ordered. Skipped when Docker is
 * not running.
 */
// The connectivity monitor is slowed down: the clock jumps would otherwise make the reader OFFLINE between steps.
@SpringBootTest(
    properties = {"tarjepafi.reservations.sweep-ms=300", "tarjepafi.iot.connectivity-check-ms=3600000"})
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Container
  static GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.21").withExposedPorts(1025, 8025);

  @DynamicPropertySource
  static void mail(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", mailpit::getHost);
    registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
  }

  private static final String START = "2026-10-07T09:50:00";
  private static final String CUBICLE = "sp-cub-l1";
  private static final String READER = "RDR-K-003";

  // Students of the seed with an active card.
  private static final String ANA = "ch-u202312966";
  private static final String ANA_CARD = "04:3D:9B:12:8F";
  private static final String BEN = "ch-u202310685";
  private static final String BEN_CARD = "04:A0:3C:AE:C6";
  private static final String CAL = "ch-u202310411";
  private static final String BLOCKED = "ch-u202311918";
  private static final String NO_RESERVATION_CARD = "04:EE:6F:CE:8D"; // U202311233, never reserves
  private static final String TEACHER = "ch-d0001";

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;

  MockMvc mvc;
  String admin;
  String teacher;

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    admin = signIn("admin@upc.edu.pe", "Admin123*");
    teacher = signIn("docente@upc.edu.pe", "Docente123*");
    moveClock(START);
  }

  // ---------- TS20: register and cancel ----------

  @Test
  @Order(1)
  void create_aValidSlot_isConfirmedWithAWindowOfTenMinutes() throws Exception {
    mvc.perform(
            as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
                .content(reservation(ANA, "2026-10-07T10:00:00", "2026-10-07T11:00:00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.spaceId").value(CUBICLE))
        .andExpect(jsonPath("$.cardHolderId").value(ANA))
        .andExpect(jsonPath("$.startTime").value("2026-10-07T10:00:00"))
        .andExpect(jsonPath("$.checkInWindowEnd").value("2026-10-07T10:10:00"))
        .andExpect(jsonPath("$.checkedInAt").doesNotExist())
        .andExpect(jsonPath("$.reservationCode").value(org.hamcrest.Matchers.matchesPattern("RES-2026-\\d{5}")));
    assertThat(getJson("/api/v1/spaces/" + CUBICLE, admin).path("status").asString()).isEqualTo("AVAILABLE");
  }

  @Test
  @Order(2)
  void create_overlappingSlot_isAConflict_butTouchingOnesAreFine() throws Exception {
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(BEN, "2026-10-07T10:30:00", "2026-10-07T11:30:00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESERVATION_OVERLAP"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(BEN, "2026-10-07T09:00:00", "2026-10-07T10:30:00")))
        .andExpect(status().isUnprocessableEntity()); // starts in the past (campus time is 09:50)
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(BEN, "2026-10-07T11:00:00", "2026-10-07T12:00:00")))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(3)
  void create_withInvalidData_isRefusedWithItsCode() throws Exception {
    String slot = reservation(ANA, "2026-10-07T15:00:00", "2026-10-07T16:00:00");
    mvc.perform(as(post("/api/v1/spaces/sp-a301/reservations"), admin).content(slot))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_RESERVABLE"));
    mvc.perform(as(post("/api/v1/spaces/sp-nope/reservations"), admin).content(slot))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(TEACHER, "2026-10-07T15:00:00", "2026-10-07T16:00:00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(BLOCKED, "2026-10-07T15:00:00", "2026-10-07T16:00:00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("HOLDER_WITHOUT_ACTIVE_CREDENTIAL"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation("ch-nobody", "2026-10-07T15:00:00", "2026-10-07T16:00:00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("HOLDER_NOT_FOUND"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(ANA, "2026-10-07T16:00:00", "2026-10-07T15:00:00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_SLOT"));
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin).content("{\"cardHolderId\":\"" + ANA + "\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(as(patch("/api/v1/spaces/" + CUBICLE + "/status"), admin).content("{\"status\":\"MAINTENANCE\"}"))
        .andExpect(status().isOk());
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin).content(slot))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_RESERVABLE"));
    mvc.perform(as(patch("/api/v1/spaces/" + CUBICLE + "/status"), admin).content("{\"status\":\"AVAILABLE\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @Order(4)
  void cancel_aConfirmedReservation_isCancelled_andOnlyOnce() throws Exception {
    String id =
        postJson(
                "/api/v1/spaces/" + CUBICLE + "/reservations",
                reservation(CAL, "2026-10-07T14:00:00", "2026-10-07T15:00:00"))
            .path("id")
            .asString();

    mvc.perform(as(post("/api/v1/reservations/" + id + "/cancel"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(as(post("/api/v1/reservations/" + id + "/cancel"), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
    mvc.perform(as(post("/api/v1/reservations/res-nope/cancel"), admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    // The slot is free again.
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(CAL, "2026-10-07T14:00:00", "2026-10-07T15:00:00")))
        .andExpect(status().isCreated());
  }

  // ---------- TS21: queries ----------

  @Test
  @Order(5)
  void queries_byIdActiveAndBySpace() throws Exception {
    assertThat(getJson("/api/v1/reservations/active", admin)).isEmpty();

    JsonNode ofSpace =
        getJson(
            "/api/v1/spaces/" + CUBICLE + "/reservations?from=2026-10-07T00:00:00&to=2026-10-07T23:59:59", admin);
    assertThat(ofSpace.size()).isGreaterThanOrEqualTo(3);
    String id = ofSpace.get(0).path("id").asString();
    assertThat(ofSpace.get(0).path("startTime").asString()).isEqualTo("2026-10-07T10:00:00");
    mvc.perform(as(get("/api/v1/reservations/" + id), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    moveClock("2026-10-07T10:05:00");
    assertThat(getJson("/api/v1/reservations/active", admin).findValuesAsString("id")).contains(id);

    mvc.perform(as(get("/api/v1/reservations/res-nope"), admin)).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/spaces/sp-nope/reservations"), admin)).andExpect(status().isNotFound());
    mvc.perform(as(get("/api/v1/spaces/" + CUBICLE + "/reservations?from=2026-10-08T00:00:00&to=2026-10-07T00:00:00"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }

  @Test
  @Order(6)
  void reservations_areForTheAdministratorOnly() throws Exception {
    mvc.perform(as(get("/api/v1/reservations/active"), teacher)).andExpect(status().isForbidden());
    mvc.perform(as(get("/api/v1/spaces/" + CUBICLE + "/reservations"), teacher)).andExpect(status().isForbidden());
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), teacher)
            .content(reservation(ANA, "2026-10-07T18:00:00", "2026-10-07T19:00:00")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations/active")).andExpect(status().isUnauthorized());
  }

  // ---------- TS22: check-in with the card ----------

  @Test
  @Order(7)
  void tap_ofTheHolder_checksTheReservationInDuringTheWindow() throws Exception {
    // Ana's reservation is 10:00 to 11:00. Before it starts, and for someone without a reservation: refused.
    JsonNode early = tap(READER, ANA_CARD);
    assertThat(early.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(early.path("messageKey").asString()).isEqualTo("tap.reservationNotStarted");
    assertThat(tap(READER, NO_RESERVATION_CARD).path("messageKey").asString()).isEqualTo("tap.noReservation");
    assertThat(getJson("/api/v1/access-records?spaceId=" + CUBICLE + "&decision=DENIED", admin).toString())
        .contains("RESERVATION_NOT_STARTED", "NO_RESERVATION");

    // Five minutes after the start she taps the table: CHECKED_IN and the space is OCCUPIED.
    moveClock("2026-10-07T10:05:00");
    JsonNode checkedIn = tap(READER, ANA_CARD);
    assertThat(checkedIn.path("outcome").asString()).isEqualTo("ACCESS");
    assertThat(checkedIn.path("messageKey").asString()).isEqualTo("tap.reservationActivated");
    assertThat(checkedIn.path("displayMessage").asString()).contains("Cubículo").contains("11:00");
    JsonNode reservation = firstReservationOf(ANA);
    assertThat(reservation.path("status").asString()).isEqualTo("CHECKED_IN");
    assertThat(reservation.path("checkedInAt").asString()).startsWith("2026-10-07T10:0");
    assertThat(getJson("/api/v1/spaces/" + CUBICLE, admin).path("status").asString()).isEqualTo("OCCUPIED");
    assertThat(getJson("/api/v1/access-records?spaceId=" + CUBICLE + "&cardHolderId=" + ANA, admin).path("items").get(0)
            .path("decision").asString())
        .isEqualTo("GRANTED");

    // Again: already active.
    assertThat(tap(READER, ANA_CARD).path("messageKey").asString()).isEqualTo("tap.reservationAlreadyActive");

    // When the slot ends the sweep completes it and the space is AVAILABLE again.
    moveClock("2026-10-07T11:00:01");
    eventually(() -> "COMPLETED".equals(firstReservationOf(ANA).path("status").asString()));
    eventually(() -> "AVAILABLE".equals(getJson("/api/v1/spaces/" + CUBICLE, admin).path("status").asString()));
  }

  // ---------- TS23 and TS-CON07: release after 10 minutes and the e-mail ----------

  @Test
  @Order(8)
  void sweep_aReservationWithoutCheckIn_isForfeitedAfterTenMinutes_andTheHolderIsEmailed() throws Exception {
    JsonNode created =
        postJson(
            "/api/v1/spaces/" + CUBICLE + "/reservations",
            reservation(BEN, "2026-10-07T12:00:00", "2026-10-07T13:00:00"));
    String id = created.path("id").asString();
    String code = created.path("reservationCode").asString();

    moveClock("2026-10-07T12:10:00"); // exactly the end of the window: still CONFIRMED
    Thread.sleep(1000);
    assertThat(getJson("/api/v1/reservations/" + id, admin).path("status").asString()).isEqualTo("CONFIRMED");

    moveClock("2026-10-07T12:10:01");
    eventually(() -> "FORFEITED".equals(getJson("/api/v1/reservations/" + id, admin).path("status").asString()));
    eventually(() -> mailTo("u202310685@upc.edu.pe").contains(code));
    // The slot is free again.
    mvc.perform(as(post("/api/v1/spaces/" + CUBICLE + "/reservations"), admin)
            .content(reservation(CAL, "2026-10-07T12:30:00", "2026-10-07T13:00:00")))
        .andExpect(status().isCreated());
    // A tap after the window has nothing to check in.
    moveClock("2026-10-07T12:11:00");
    assertThat(tap(READER, BEN_CARD).path("messageKey").asString()).isEqualTo("tap.noReservation");
  }

  // ---------- Helpers ----------

  private JsonNode firstReservationOf(String holderId) throws Exception {
    for (JsonNode reservation :
        getJson("/api/v1/spaces/" + CUBICLE + "/reservations?from=2026-10-07T00:00:00&to=2026-10-07T23:59:59", admin)) {
      if (holderId.equals(reservation.path("cardHolderId").asString())) {
        return reservation;
      }
    }
    throw new AssertionError("No reservation of " + holderId);
  }

  /** What Mailpit holds for one recipient (subject and snippet of every message to them). */
  private String mailTo(String address) throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    StringBuilder text = new StringBuilder();
    for (JsonNode message : mapper.readTree(response.body()).path("messages")) {
      if (message.path("To").toString().contains(address)) {
        text.append(message.path("Subject").asString()).append(' ').append(message.path("Snippet").asString());
      }
    }
    return text.toString();
  }

  private static String reservation(String holder, String start, String end) {
    return "{\"cardHolderId\":\"" + holder + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
  }

  private JsonNode tap(String readerId, String cardUid) throws Exception {
    JsonNode answer =
        mapper.readTree(
            mvc.perform(as(post("/api/v1/iot/readers/" + readerId + "/taps"), admin).content("{\"cardUid\":\"" + cardUid + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString());
    if ("PENDING".equals(answer.path("outcome").asString())) {
      JsonNode[] last = {answer};
      eventually(
          () -> {
            last[0] = getJson("/api/v1/iot/tap-events/" + answer.path("id").asString(), admin);
            return !"PENDING".equals(last[0].path("outcome").asString());
          });
      return last[0];
    }
    return answer;
  }

  private void moveClock(String time) throws Exception {
    mvc.perform(as(put("/api/v1/simulation/clock"), admin).content("{\"now\":\"" + time + "\"}"))
        .andExpect(status().isOk());
  }

  private JsonNode postJson(String url, String body) throws Exception {
    return mapper.readTree(
        mvc.perform(as(post(url), admin).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
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
    return mapper.readTree(mvc.perform(as(get(url), token)).andReturn().getResponse().getContentAsString());
  }

  private static void eventually(Callable<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (condition.call()) {
        return;
      }
      Thread.sleep(150);
    }
    assertThat(condition.call()).as("the condition did not hold within 20 seconds").isTrue();
  }
}
