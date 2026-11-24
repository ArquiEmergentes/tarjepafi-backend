package com.pafisolutions.tarjepafi.datamanagement.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
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
 * Phase 7 Verify block against real PostgreSQL and RabbitMQ: a report of the demo week has the figures the old
 * web app computes from the same facts (captured once from its calculator on the seed: the same logic gives the
 * seeded reports back), and one fact is recorded once even when its event is delivered again. Skipped when
 * Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataManagementApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  private static final String START = "2026-10-07T09:50:00";

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;
  @Autowired RabbitTemplate rabbitTemplate;

  MockMvc mvc;
  String admin;
  String teacher;

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    admin = signIn("admin@upc.edu.pe", "Admin123*");
    teacher = signIn("docente@upc.edu.pe", "Docente123*");
    mvc.perform(as(put("/api/v1/simulation/clock"), admin).content("{\"now\":\"" + START + "\"}"))
        .andExpect(status().isOk());
  }

  // ---------- Verify: the report of the demo week ----------

  @Test
  @Order(1)
  void weeklyReport_ofTheDemoWeek_hasTheFiguresOfTheOldWebApp() throws Exception {
    JsonNode requested =
        postJson(
            "/api/v1/reports/weekly",
            "{\"periodStart\":\"2026-10-05\",\"periodEnd\":\"2026-10-11\"}",
            202);
    assertThat(requested.path("status").asString()).isEqualTo("GENERATING");
    assertThat(requested.path("summary").isNull()).isTrue();
    assertThat(requested.path("periodStart").asString()).isEqualTo("2026-10-05T00:00:00");
    assertThat(requested.path("periodEnd").asString()).isEqualTo("2026-10-11T23:59:59");

    JsonNode report = awaitGenerated(requested.path("id").asString());

    assertThat(report.path("totalRecords").asInt()).isEqualTo(497);
    JsonNode summary = report.path("summary");
    assertThat(summary.path("attendanceRate").asDouble()).isEqualTo(77.5);
    assertThat(summary.path("accessGranted").asInt()).isEqualTo(104);
    assertThat(summary.path("accessDenied").asInt()).isEqualTo(15);
    assertThat(summary.path("peakHour").asString()).isEqualTo("07:00");
    assertThat(summary.path("readersWithIncidents").asInt()).isEqualTo(7);
  }

  @Test
  @Order(2)
  void seededReport_hasTheSameFiguresAsItsCalculation_andItsDays() throws Exception {
    // rep-002 (5 and 6 October) came with the old web app's seed; the same calculation gives it back.
    JsonNode asked = postJson("/api/v1/reports/weekly", "{\"periodStart\":\"2026-10-05\",\"periodEnd\":\"2026-10-06\"}", 202);
    JsonNode report = awaitGenerated(asked.path("id").asString());
    JsonNode seeded = getJson("/api/v1/reports/rep-002", admin).path("report");

    assertThat(report.path("totalRecords").asInt()).isEqualTo(seeded.path("totalRecords").asInt());
    assertThat(report.path("summary")).isEqualTo(seeded.path("summary"));

    JsonNode days = getJson("/api/v1/reports/" + asked.path("id").asString(), admin).path("days");
    assertThat(days).hasSize(2);
    assertThat(days.get(0).path("date").asString()).isEqualTo("2026-10-05");
    assertThat(days.get(0).path("readings").asInt()).isEqualTo(89);
    assertThat(days.get(0).path("attendanceRate").asDouble()).isEqualTo(79.5);
    assertThat(days.get(0).path("accessGranted").asInt()).isEqualTo(33);
    assertThat(days.get(0).path("accessDenied").asInt()).isEqualTo(4);
    assertThat(days.get(1).path("date").asString()).isEqualTo("2026-10-06");
    assertThat(days.get(1).path("readings").asInt()).isEqualTo(95);
    assertThat(days.get(1).path("attendanceRate").asDouble()).isEqualTo(77.3);
  }

  @Test
  @Order(3)
  void reports_listAndDetail_andTheirErrors() throws Exception {
    JsonNode list = getJson("/api/v1/reports", admin);
    assertThat(list.findValuesAsString("id")).contains("rep-001", "rep-002", "rep-003");

    mvc.perform(as(get("/api/v1/reports/rep-999"), admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    mvc.perform(as(post("/api/v1/reports/yearly"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REPORT_TYPE"));
    mvc.perform(
            as(post("/api/v1/reports/weekly"), admin)
                .content("{\"periodStart\":\"2026-10-11\",\"periodEnd\":\"2026-10-05\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
  }

  @Test
  @Order(4)
  void report_withoutAPeriod_usesTheWeekOfTheCampusClock() throws Exception {
    JsonNode requested = postJson("/api/v1/reports/weekly", null, 202);

    // 2026-10-07 is a Wednesday: the week is Monday 5 to Sunday 11.
    assertThat(requested.path("periodStart").asString()).isEqualTo("2026-10-05T00:00:00");
    assertThat(requested.path("periodEnd").asString()).isEqualTo("2026-10-11T23:59:59");
    assertThat(requested.path("requestedBy").asString()).isNotBlank();
  }

  // ---------- Dashboard and records ----------

  @Test
  @Order(5)
  void dashboard_ofADemoDay_hasTheFiguresOfItsFacts() throws Exception {
    JsonNode dashboard = getJson("/api/v1/dashboard?date=2026-10-06", admin);

    assertThat(dashboard.path("date").asString()).isEqualTo("2026-10-06");
    JsonNode byStatus = dashboard.path("attendance").path("byStatus");
    assertThat(byStatus.path("PRESENT").asInt()).isEqualTo(11);
    assertThat(byStatus.path("LATE").asInt()).isEqualTo(6);
    assertThat(byStatus.path("ABSENT").asInt()).isEqualTo(5);
    assertThat(byStatus.path("PENDING").asInt()).isEqualTo(18);
    assertThat(dashboard.path("attendance").path("rate").asDouble()).isEqualTo(77.3);
    assertThat(dashboard.path("access").path("granted").asInt()).isEqualTo(38);
    assertThat(dashboard.path("access").path("denied").asInt()).isEqualTo(6);
    assertThat(dashboard.path("readings").path("today").asInt()).isEqualTo(95);
    assertThat(dashboard.path("readings").path("recent")).hasSize(8);
    assertThat(dashboard.path("readersWithIncidents").asInt()).isEqualTo(2);
  }

  @Test
  @Order(6)
  void dataRecords_filtersAndPolling_andTheirErrors() throws Exception {
    JsonNode first = getJson("/api/v1/data-records?type=ACCESS&limit=3", admin);
    assertThat(first.path("items")).hasSize(3);
    assertThat(first.path("items").get(0).path("dataType").asString()).isEqualTo("ACCESS");
    String cursor = first.path("nextCursor").asString();

    JsonNode nothingNew = getJson("/api/v1/data-records?type=ACCESS&after=" + cursor, admin);
    assertThat(nothingNew.path("items")).isEmpty();
    assertThat(nothingNew.path("nextCursor").asString()).isEqualTo(cursor);

    JsonNode ofAReader = getJson("/api/v1/data-records?source=IOT_MONITORING&subjectId=RDR-T-002&type=DEVICE_TELEMETRY", admin);
    assertThat(ofAReader.path("items")).isNotEmpty();

    mvc.perform(as(get("/api/v1/data-records?type=WEATHER"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DATA_TYPE"));
    mvc.perform(as(get("/api/v1/data-records?after=abc"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    mvc.perform(as(get("/api/v1/data-records?from=2026-10-06T00:00:00&to=2026-10-05T00:00:00"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }

  @Test
  @Order(7)
  void dataManagement_isForTheAdministratorOnly() throws Exception {
    for (String url : new String[] {"/api/v1/reports", "/api/v1/dashboard", "/api/v1/data-records"}) {
      mvc.perform(as(get(url), teacher)).andExpect(status().isForbidden());
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
    mvc.perform(as(post("/api/v1/reports/weekly"), teacher)).andExpect(status().isForbidden());
  }

  // ---------- Ingestion (TS24) ----------

  @Test
  @Order(8)
  void ingestion_aLiveTap_isRecordedOncePerFact() throws Exception {
    long readings = count("CARD_READING", "RDR-A-301");
    long attendances = count("ATTENDANCE", null);

    mvc.perform(as(post("/api/v1/iot/readers/RDR-A-301/taps"), admin).content("{\"cardUid\":\"04:3D:9B:12:8F\"}"))
        .andExpect(status().is2xxSuccessful());

    eventually(() -> count("CARD_READING", "RDR-A-301") == readings + 1);
    eventually(() -> count("ATTENDANCE", null) == attendances + 1);
    JsonNode reading = getJson("/api/v1/data-records?type=CARD_READING&subjectId=RDR-A-301&limit=1", admin).path("items").get(0);
    assertThat(reading.path("sourceContext").asString()).isEqualTo("IOT_MONITORING");
    assertThat(reading.path("payload").path("cardUid").asString()).isEqualTo("04:3D:9B:12:8F");
    assertThat(reading.path("payload").path("outcome").asString()).isEqualTo("ACCEPTED");
  }

  @Test
  @Order(9)
  void ingestion_theSameEventDeliveredAgain_addsNothing() throws Exception {
    long accesses = count("ACCESS", null);
    String event =
        envelope(
            "evt-replayed-1",
            "space.access-granted",
            "{\"accessRecordId\":\"acc-replay-1\",\"decision\":\"GRANTED\",\"denialReason\":\"NONE\","
                + "\"accessType\":\"CAMPUS_ENTRY\",\"spaceId\":\"sp-ent-01\",\"attemptedAt\":\"2026-10-07T09:51:00\"}");

    publish("space.access-granted", event);
    eventually(() -> count("ACCESS", null) == accesses + 1);
    publish("space.access-granted", event);
    publish("space.access-granted", event);
    // A different event is recorded after the replays, so by the time it shows the replays have been handled.
    publish(
        "space.access-denied",
        envelope(
            "evt-replayed-2",
            "space.access-denied",
            "{\"accessRecordId\":\"acc-replay-2\",\"decision\":\"DENIED\",\"denialReason\":\"UNAUTHORIZED_ROLE\","
                + "\"accessType\":\"ROOM\",\"spaceId\":\"sp-lab-e\",\"attemptedAt\":\"2026-10-07T09:52:00\"}"));
    eventually(() -> count("ACCESS", null) == accesses + 2);

    assertThat(count("ACCESS", null)).isEqualTo(accesses + 2);
  }

  @Test
  @Order(10)
  void ingestion_aMessageThatBreaksTheRules_endsInTheDeadLetterQueue() throws Exception {
    // A data record needs a subject: this event has none, so it fails three times and is parked.
    publish(
        "space.access-granted",
        envelope("evt-broken-1", "space.access-granted", "{\"decision\":\"GRANTED\",\"attemptedAt\":\"2026-10-07T09:53:00\"}"));

    Message dead = null;
    long deadline = System.nanoTime() + 20_000_000_000L;
    while (dead == null && System.nanoTime() < deadline) {
      dead = rabbitTemplate.receive("data.events.queue.dlq", 500);
    }

    assertThat(dead).isNotNull();
    assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("evt-broken-1");
  }

  // ---------- Helpers ----------

  private long count(String type, String subjectId) throws Exception {
    // Each page is at most 200 items, so walk the cursor-less list by the newest `limit`: filter by type and subject.
    String url = "/api/v1/data-records?type=" + type + "&limit=200" + (subjectId == null ? "" : "&subjectId=" + subjectId);
    JsonNode page = getJson(url, admin);
    long total = page.path("items").size();
    assertThat(total).as("a page of 200 is enough for this test").isLessThan(200);
    return total;
  }

  private JsonNode awaitGenerated(String id) throws Exception {
    JsonNode[] last = new JsonNode[1];
    eventually(
        () -> {
          last[0] = getJson("/api/v1/reports/" + id, admin).path("report");
          return "GENERATED".equals(last[0].path("status").asString());
        });
    return last[0];
  }

  private String envelope(String eventId, String type, String data) {
    return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"occurredAt\":\"2026-10-07T09:50:00\",\"data\":" + data + "}";
  }

  private void publish(String routingKey, String json) {
    Message message =
        MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitTemplate.send("tarjepafi.events", routingKey, message);
  }

  private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request = as(post(url), admin);
    if (body != null) {
      request.content(body);
    }
    return mapper.readTree(
        mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
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
