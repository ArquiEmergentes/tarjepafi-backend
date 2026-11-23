package com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceEventHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * The IoT pipeline end to end against real PostgreSQL and RabbitMQ: a reader publishes in the device
 * contract (v1), the backend stores and resolves the tap, and the reply reaches the display topic. Also the
 * reader management API. The tests share one database, so each uses its own reader or card. Skipped when
 * Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class IotApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  private static final String STUDENT_CARD = "04:3D:9B:12:8F"; // cred-001, ACTIVE
  private static final String BLOCKED_CARD = "04:4D:DA:3D:91"; // cred-015, BLOCKED

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;
  @Autowired RabbitTemplate template;
  @Autowired RabbitAdmin admin;
  @Autowired DeviceEventHandler handler;

  MockMvc mvc;
  String token;

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    token =
        mapper
            .readTree(
                mvc.perform(
                        post("/api/v1/auth/sign-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@upc.edu.pe\",\"password\":\"Admin123*\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("token")
            .asString();
  }

  // ---------- Verify block: the tap pipeline ----------

  @Test
  void tap_validCard_isStoredResolvedAndAnsweredOnTheDisplayTopic() throws Exception {
    Queue display = bindDisplayQueue("RDR-A-301");
    String eventId = UUID.randomUUID().toString();

    publish("RDR-A-301", "tap", tapPayload(eventId, "RDR-A-301", STUDENT_CARD));

    JsonNode event = awaitResolved(eventId);
    // The student is enrolled in the class of RDR-A-301 that is in progress at the simulated 09:50; another
    // test may have tapped before, so it is either the first mark or "already registered".
    assertThat(event.path("outcome").asString()).isEqualTo("ATTENDANCE");
    assertThat(event.path("messageKey").asString())
        .isIn("tap.attendanceTaken", "tap.alreadyRegistered");
    assertThat(event.path("displayMessage").asString())
        .isIn("Asistencia tomada · Aplicaciones Web · 10:00–11:30", "Tu asistencia ya fue registrada");
    assertThat(event.path("source").asString()).isEqualTo("READER");
    assertThat(event.path("cardUid").asString()).isEqualTo(STUDENT_CARD);

    Message reply = template.receive(display.getName(), 5000);
    assertThat(reply).isNotNull();
    JsonNode body = mapper.readTree(new String(reply.getBody(), StandardCharsets.UTF_8));
    assertThat(body.path("eventId").asString()).isEqualTo(eventId);
    assertThat(body.path("tone").asString()).isIn("success", "info");
    assertThat(body.path("lines").get(0).asString())
        .isIn("Asistencia tomada", "Tu asistencia ya fue registrada");
  }

  @Test
  void tap_sameEventPublishedTwice_yieldsOneEventAndTheReaderIsToldItWasProcessed() throws Exception {
    Queue display = bindDisplayQueue("RDR-A-303");
    String eventId = UUID.randomUUID().toString();
    String payload = tapPayload(eventId, "RDR-A-303", STUDENT_CARD);

    publish("RDR-A-303", "tap", payload);
    awaitResolved(eventId);
    publish("RDR-A-303", "tap", payload);
    Thread.sleep(1500);
    // The first reply is the resolution; the second says the event was already processed.
    var replies = new java.util.ArrayList<String>();
    Message reply;
    while ((reply = template.receive(display.getName())) != null) {
      replies.add(new String(reply.getBody(), StandardCharsets.UTF_8));
    }
    assertThat(replies).hasSize(2);
    assertThat(replies.get(1)).contains("Evento ya procesado");

    JsonNode page = getJson("/api/v1/iot/tap-events?readerId=RDR-A-303&limit=200");
    long copies = 0;
    for (JsonNode item : page.path("items")) {
      copies += item.path("id").asString().equals(eventId) ? 1 : 0;
    }
    assertThat(copies).isEqualTo(1);
  }

  @Test
  void tap_unknownCard_isRejected() throws Exception {
    String eventId = UUID.randomUUID().toString();

    publish("RDR-A-301", "tap", tapPayload(eventId, "RDR-A-301", "04:AA:BB:CC:DD"));

    JsonNode event = awaitResolved(eventId);
    assertThat(event.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(event.path("tone").asString()).isEqualTo("error");
    assertThat(event.path("messageKey").asString()).isEqualTo("tap.cardNotRegistered");
  }

  @Test
  void tap_blockedCard_isRejectedAsInactive() throws Exception {
    String eventId = UUID.randomUUID().toString();

    publish("RDR-A-301", "tap", tapPayload(eventId, "RDR-A-301", BLOCKED_CARD));

    assertThat(awaitResolved(eventId).path("messageKey").asString()).isEqualTo("tap.cardInactive");
  }

  @Test
  void tap_fromAnUnregisteredReader_isRejectedAndOpensAnIncident() throws Exception {
    String eventId = UUID.randomUUID().toString();

    publish("RDR-Z-999", "tap", tapPayload(eventId, "RDR-Z-999", STUDENT_CARD));

    JsonNode event = awaitResolved(eventId);
    assertThat(event.path("outcome").asString()).isEqualTo("REJECTED");
    assertThat(event.path("messageKey").asString()).isEqualTo("tap.readerOutOfService");
    mvc.perform(as(get("/api/v1/iot/incidents?readerId=RDR-Z-999&open=true")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("CONFIGURATION_ERROR"));
  }

  @Test
  void tap_fromAnInactiveReader_isRejectedAndOpensAnIncident() throws Exception {
    String eventId = UUID.randomUUID().toString();

    publish("RDR-K-002", "tap", tapPayload(eventId, "RDR-K-002", STUDENT_CARD));

    assertThat(awaitResolved(eventId).path("outcome").asString()).isEqualTo("REJECTED");
    mvc.perform(as(get("/api/v1/iot/readers/RDR-K-002/incidents?open=true")))
        .andExpect(jsonPath("$[?(@.type=='READ_ERROR')]").isNotEmpty());
  }

  @Test
  void malformedPayload_endsInTheDeadLetterQueue() {
    template.send(
        "tarjepafi.devices",
        "tarjepafi.readers.RDR-A-301.tap",
        MessageBuilder.withBody("this is not json".getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
            .build());

    Message dead = template.receive("iot.device-events.queue.dlq", 20000);

    assertThat(dead).isNotNull();
    assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("this is not json");
  }

  // ---------- Heartbeats, telemetry and errors (TS11) ----------

  @Test
  void telemetry_weakSignalDegradesAReaderAndAGoodOneRestoresIt() throws Exception {
    publish("RDR-A-302", "telemetry", telemetryPayload("RDR-A-302", -82, Instant.now().minusSeconds(5)));
    eventually(() -> readerStatus("RDR-A-302").equals("DEGRADED"));

    publish("RDR-A-302", "telemetry", telemetryPayload("RDR-A-302", -50, Instant.now()));
    eventually(() -> readerStatus("RDR-A-302").equals("ACTIVE"));

    mvc.perform(as(get("/api/v1/iot/readers/RDR-A-302/telemetry?limit=5")))
        .andExpect(jsonPath("$[0].signalStrength").value(-50))
        .andExpect(jsonPath("$[1].signalStrength").value(-82));
  }

  @Test
  void heartbeat_fromAnOfflineReader_bringsItBackAndResolvesItsIncident() throws Exception {
    assertThat(readerStatus("RDR-C-107")).isEqualTo("OFFLINE");

    publish(
        "RDR-C-107",
        "heartbeat",
        "{\"v\":1,\"type\":\"Heartbeat\",\"readerId\":\"RDR-C-107\",\"sentAt\":\""
            + Instant.now()
            + "\"}");

    eventually(() -> readerStatus("RDR-C-107").equals("ACTIVE"));
    mvc.perform(as(get("/api/v1/iot/readers/RDR-C-107/incidents?open=true")))
        .andExpect(jsonPath("$[?(@.type=='DEVICE_OFFLINE')]").isEmpty());
  }

  @Test
  void errorReport_opensAnIncidentOfTheReportedType() throws Exception {
    publish(
        "RDR-A-303",
        "error",
        "{\"v\":1,\"type\":\"ReaderError\",\"readerId\":\"RDR-A-303\",\"code\":\"HARDWARE_FAILURE\","
            + "\"message\":\"PN532 not found\",\"occurredAt\":\""
            + Instant.now()
            + "\"}");

    eventually(
        () ->
            getJson("/api/v1/iot/readers/RDR-A-303/incidents?open=true").toString().contains("HARDWARE_FAILURE"));
  }

  @Test
  void markOffline_readerSilentForMoreThanThreeHeartbeats_goesOfflineWithAnIncident() throws Exception {
    LocalDateTime later = LocalDateTime.parse("2026-10-07T10:30:00");

    assertThat(handler.markOffline("RDR-D-004", later)).isTrue();

    assertThat(readerStatus("RDR-D-004")).isEqualTo("OFFLINE");
    mvc.perform(as(get("/api/v1/iot/readers/RDR-D-004/incidents?open=true")))
        .andExpect(jsonPath("$[?(@.type=='DEVICE_OFFLINE')]").isNotEmpty());
    assertThat(handler.markOffline("RDR-D-004", later)).isFalse();
  }

  // ---------- API: simulated taps and polling ----------

  @Test
  void simulatedTap_throughTheApi_travelsThePipelineAndAnswersResolved() throws Exception {
    mvc.perform(as(post("/api/v1/iot/readers/RDR-A-301/taps")).content("{\"cardUid\":\"043d9b128f\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("ATTENDANCE"))
        .andExpect(jsonPath("$.source").value("SIMULATOR"))
        .andExpect(jsonPath("$.cardUid").value(STUDENT_CARD));
    mvc.perform(as(post("/api/v1/iot/readers/RDR-Z-998/taps")).content("{\"cardUid\":\"043d9b128f\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("REJECTED"));
    mvc.perform(as(post("/api/v1/iot/readers/RDR-A-301/taps")).content("{\"cardUid\":\"04:3D\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CARD_UID"));
  }

  @Test
  void tapEvents_afterTheLastCursor_returnOnlyWhatIsNew() throws Exception {
    JsonNode first = getJson("/api/v1/iot/tap-events?readerId=RDR-B-201");
    String cursor = first.path("nextCursor").asString();

    String eventId = UUID.randomUUID().toString();
    publish("RDR-B-201", "tap", tapPayload(eventId, "RDR-B-201", STUDENT_CARD));
    awaitResolved(eventId);

    JsonNode news = getJson("/api/v1/iot/tap-events?readerId=RDR-B-201&after=" + cursor);
    assertThat(news.path("items")).hasSize(1);
    assertThat(news.path("items").get(0).path("id").asString()).isEqualTo(eventId);
    String next = news.path("nextCursor").asString();
    assertThat(Long.parseLong(next)).isGreaterThan(Long.parseLong(cursor));

    JsonNode nothing = getJson("/api/v1/iot/tap-events?readerId=RDR-B-201&after=" + next);
    assertThat(nothing.path("items")).isEmpty();
    assertThat(nothing.path("nextCursor").asString()).isEqualTo(next);

    mvc.perform(as(get("/api/v1/iot/tap-events?after=abc")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
  }

  @Test
  void seedHistory_isAvailableWithItsOutcomes() throws Exception {
    mvc.perform(as(get("/api/v1/iot/tap-events?limit=200")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(200)));
    mvc.perform(as(get("/api/v1/iot/incidents?open=true")))
        .andExpect(jsonPath("$[*].readerId", hasItem("RDR-B-202")));
  }

  // ---------- Reader management (TS05 to TS09) ----------

  @Test
  void readers_listFilterAndDetail() throws Exception {
    mvc.perform(as(get("/api/v1/iot/readers")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='RDR-A-301')].configuration.heartbeatInterval").value(60));
    mvc.perform(as(get("/api/v1/iot/readers?status=INACTIVE")))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("RDR-K-002"))
        .andExpect(jsonPath("$[0].availableActions[0]").value("ACTIVATE"));
    mvc.perform(as(get("/api/v1/iot/readers/RDR-B-202")))
        .andExpect(jsonPath("$.reader.status").value("DEGRADED"))
        .andExpect(jsonPath("$.telemetry").isArray())
        .andExpect(jsonPath("$.incidents[0].type").value("READ_ERROR"))
        .andExpect(jsonPath("$.recentTaps").isArray());
    mvc.perform(as(get("/api/v1/iot/readers/RDR-NOPE")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("READER_NOT_FOUND"));
  }

  @Test
  void register_validReader_startsRegisteredAndCanBeActivatedAndServiced() throws Exception {
    String body = readerBody("RDR-X-001", "sp-a301", 30, 60, "1.0.0");

    mvc.perform(as(post("/api/v1/iot/readers")).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REGISTERED"))
        .andExpect(jsonPath("$.lastSeenAt").doesNotExist());
    mvc.perform(as(post("/api/v1/iot/readers")).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("READER_ID_TAKEN"));

    mvc.perform(as(post("/api/v1/iot/readers/RDR-X-001/activate")))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.lastSeenAt").exists());
    mvc.perform(as(post("/api/v1/iot/readers/RDR-X-001/activate")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));
    mvc.perform(as(post("/api/v1/iot/readers/RDR-X-001/start-maintenance")))
        .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    mvc.perform(as(post("/api/v1/iot/readers/RDR-X-001/end-maintenance")))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(as(post("/api/v1/iot/readers/RDR-X-001/deactivate")))
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  @Test
  void register_invalidData_isRejectedWithTheBrokenRules() throws Exception {
    mvc.perform(as(post("/api/v1/iot/readers")).content(readerBody("rdr-1", "sp-a301", 30, 60, "1.0.0")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("READER_ID_INVALID"));
    mvc.perform(as(post("/api/v1/iot/readers")).content(readerBody("RDR-X-002", "sp-a301", 30, 20, "1.0")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("READER_CONFIGURATION_INVALID"))
        .andExpect(jsonPath("$.errors", hasItem("HEARTBEAT_LOWER_THAN_COMMUNICATION")))
        .andExpect(jsonPath("$.errors", hasItem("FIRMWARE_FORMAT")));
    mvc.perform(as(post("/api/v1/iot/readers")).content(readerBody("RDR-X-003", "nope", 30, 60, "1.0.0")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    mvc.perform(as(post("/api/v1/iot/readers")).content("{\"id\":\"RDR-X-004\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void configuration_invalidChangeKeepsThePreviousOne() throws Exception {
    mvc.perform(
            as(put("/api/v1/iot/readers/RDR-B-201/configuration"))
                .content("{\"readerMode\":\"ATTENDANCE\",\"communicationInterval\":0,\"heartbeatInterval\":60,\"firmwareVersion\":\"2.4.1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasItem("COMMUNICATION_INTERVAL_INVALID")));
    mvc.perform(as(get("/api/v1/iot/readers/RDR-B-201")))
        .andExpect(jsonPath("$.reader.configuration.communicationInterval").value(30));

    mvc.perform(
            as(put("/api/v1/iot/readers/RDR-B-201/configuration"))
                .content("{\"readerMode\":\"ATTENDANCE\",\"communicationInterval\":30,\"heartbeatInterval\":90,\"firmwareVersion\":\"2.5.0\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configuration.firmwareVersion").value("2.5.0"))
        .andExpect(jsonPath("$.configuration.heartbeatInterval").value(90));
  }

  @Test
  void incidents_canBeResolvedOnce() throws Exception {
    String id =
        getJson("/api/v1/iot/incidents?readerId=RDR-D-007&open=true").get(0).path("id").asString();

    mvc.perform(as(post("/api/v1/iot/incidents/" + id + "/resolve")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resolvedAt").exists());
    mvc.perform(as(post("/api/v1/iot/incidents/" + id + "/resolve")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INCIDENT_ALREADY_RESOLVED"));
    mvc.perform(as(post("/api/v1/iot/incidents/nope/resolve")))
        .andExpect(status().isNotFound());
  }

  @Test
  void iotRoutes_needTheAdminRole() throws Exception {
    mvc.perform(get("/api/v1/iot/readers")).andExpect(status().isUnauthorized());
    String teacher =
        mapper
            .readTree(
                mvc.perform(
                        post("/api/v1/auth/sign-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"docente@upc.edu.pe\",\"password\":\"Docente123*\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("token")
            .asString();
    mvc.perform(get("/api/v1/iot/readers").header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher))
        .andExpect(status().isForbidden());
  }

  // ---------- Helpers ----------

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
    return request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON);
  }

  private JsonNode getJson(String url) throws Exception {
    return mapper.readTree(
        mvc.perform(as(get(url))).andReturn().getResponse().getContentAsString());
  }

  private String readerStatus(String readerId) throws Exception {
    return getJson("/api/v1/iot/readers/" + readerId).path("reader").path("status").asString();
  }

  private static String readerBody(String id, String spaceId, int comm, int heartbeat, String firmware) {
    return "{\"id\":\"%s\",\"name\":\"Test reader\",\"deviceType\":\"DOOR_READER\",\"spaceId\":\"%s\","
            .formatted(id, spaceId)
        + "\"configuration\":{\"readerMode\":\"ACCESS_CONTROL\",\"communicationInterval\":%d,\"heartbeatInterval\":%d,\"firmwareVersion\":\"%s\"}}"
            .formatted(comm, heartbeat, firmware);
  }

  private static String tapPayload(String eventId, String readerId, String uid) {
    return ("{\"v\":1,\"type\":\"CardTapped\",\"eventId\":\"%s\",\"readerId\":\"%s\",\"cardUid\":\"%s\","
            + "\"tappedAt\":\"%s\",\"buffered\":false}")
        .formatted(eventId, readerId, uid, Instant.now());
  }

  private static String telemetryPayload(String readerId, int signal, Instant capturedAt) {
    return ("{\"v\":1,\"type\":\"TelemetryCaptured\",\"readerId\":\"%s\",\"capturedAt\":\"%s\","
            + "\"signalStrength\":%d,\"uptime\":100,\"firmwareVersion\":\"2.4.1\",\"bufferedCount\":0}")
        .formatted(readerId, capturedAt, signal);
  }

  /** Publishes as a reader would: to {@code tarjepafi.devices} with the routing key of the topic. */
  private void publish(String readerId, String kind, String json) {
    template.send(
        "tarjepafi.devices",
        "tarjepafi.readers." + readerId + "." + kind,
        MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build());
  }

  /** A queue that receives what the backend sends to the reader's display topic. */
  private Queue bindDisplayQueue(String readerId) {
    Queue queue = new Queue("test.display." + UUID.randomUUID(), false, true, true);
    admin.declareQueue(queue);
    Binding binding =
        BindingBuilder.bind(queue)
            .to(new TopicExchange("tarjepafi.devices"))
            .with("tarjepafi.readers." + readerId + ".display");
    admin.declareBinding(binding);
    return queue;
  }

  private JsonNode awaitResolved(String eventId) throws Exception {
    JsonNode[] found = new JsonNode[1];
    eventually(
        () -> {
          var result = mvc.perform(as(get("/api/v1/iot/tap-events/" + eventId))).andReturn().getResponse();
          if (result.getStatus() != 200) {
            return false;
          }
          JsonNode node = mapper.readTree(result.getContentAsString());
          found[0] = node;
          return !node.path("outcome").asString().equals("PENDING");
        });
    return found[0];
  }

  /** Polls until the condition holds, for at most 15 seconds. */
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
