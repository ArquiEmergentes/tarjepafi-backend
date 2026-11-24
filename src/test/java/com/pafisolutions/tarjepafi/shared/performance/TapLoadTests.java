package com.pafisolutions.tarjepafi.shared.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 9 load tests of the tap pipeline, published to the broker the way a reader does (AMQP on the devices
 * exchange, which the MQTT plugin maps from the readers' topics) and timed until the display reply arrives:
 *
 * <ul>
 *   <li>QAS01: a reading is confirmed within 1 second (95th percentile), in normal operation.
 *   <li>QAS02: at least 200 readings per minute are taken without losing any and without the latency growing.
 * </ul>
 *
 * It is slow and measures the machine it runs on, so it only runs on request:
 * {@code mvn test -Dtest=TapLoadTests -Dtarjepafi.load=true}. The results are written to
 * {@code target/load-test-results.md}. Needs Docker.
 */
@SpringBootTest(
    properties = {
      "tarjepafi.iot.connectivity-check-ms=3600000",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "tarjepafi.load", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TapLoadTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  /** The seed's active student cards, so every tap is a real, accepted reading. */
  private static final List<String> CARDS =
      List.of(
          "04:3D:9B:12:8F", "04:73:3C:F2:5C", "04:03:14:9D:35", "04:59:13:A4:5F", "04:5A:BE:4B:39", "04:A0:3C:AE:C6",
          "04:6E:08:89:99", "04:BF:0B:FB:4B", "04:EE:6F:CE:8D", "04:54:0D:05:B5", "04:C7:04:F2:BF", "04:3D:C6:0E:F8",
          "04:4E:69:51:E6", "04:80:0E:7E:03", "04:BB:D1:9E:A8", "04:8D:D9:71:78", "04:36:56:CD:66", "04:E5:D4:81:E2",
          "04:F6:47:B8:D1", "04:6E:70:04:5F", "04:25:35:FF:C9", "04:64:4A:8F:CD", "04:8A:79:47:B7", "04:19:63:75:A2",
          "04:22:A8:33:5B", "04:AE:51:8D:11", "04:8C:E6:65:FA", "04:24:44:52:A5", "04:63:1A:47:0B", "04:52:37:5A:CD",
          "04:1B:39:A1:B3", "04:56:9E:B3:89", "04:0F:99:3A:5D", "04:A4:45:65:72", "04:AC:B9:25:82", "04:9B:69:A8:28");

  /** Turnstile and door readers: every tap writes an access record, the heaviest path that needs no class. */
  private static final List<String> READERS = List.of("RDR-T-001", "RDR-T-002", "RDR-D-001", "RDR-D-006");

  private static final long QAS01_LIMIT_MS = 1000;
  private static final int QAS02_PER_MINUTE = 200;

  @Autowired RabbitTemplate template;
  @Autowired RabbitAdmin admin;
  @Autowired ObjectMapper mapper;
  @Autowired CardTapEventRepository taps;
  @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

  private static final StringBuilder report = new StringBuilder("# Load test results\n\n");

  /** What a run produced: the latency of each tap and how long the whole run took. */
  private record Run(List<Long> latenciesMs, int published, long seconds, int stored, long statements) {
    long percentile(int p) {
      List<Long> sorted = new ArrayList<>(latenciesMs);
      Collections.sort(sorted);
      return sorted.isEmpty() ? -1 : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
    }

    long median(List<Long> values) {
      List<Long> sorted = new ArrayList<>(values);
      Collections.sort(sorted);
      return sorted.isEmpty() ? -1 : sorted.get(sorted.size() / 2);
    }
  }

  @Test
  @Order(1)
  void qas01_aReadingIsConfirmedWithinOneSecond_inNormalOperation() throws Exception {
    Run run = run(60, 4.0); // 240 per minute pace is more than normal operation; here: 4 per second for 60 taps

    String line = describe("QAS01 · normal operation (about one tap every 250 ms, 60 taps)", run);
    report.append(line);
    assertThat(run.latenciesMs()).as("every tap got its display reply").hasSize(60);
    assertThat(run.percentile(95)).as("95th percentile of the reply (ms)").isLessThanOrEqualTo(QAS01_LIMIT_MS);
  }

  @Test
  @Order(2)
  void qas02_twoHundredReadingsPerMinute_areTakenWithoutLossOrSlowdown() throws Exception {
    int taps = 300; // 5 per second for a minute: 300 per minute, 50 % over the requirement
    Run run = run(taps, 5.0);

    List<Long> first = run.latenciesMs().subList(0, taps / 3);
    List<Long> last = run.latenciesMs().subList(2 * taps / 3, taps);
    report.append(describe("QAS02 · sustained 300 readings per minute for one minute", run));
    report.append(
        "- median of the first third: %d ms, of the last third: %d ms\n\n".formatted(run.median(first), run.median(last)));
    assertThat(run.latenciesMs()).as("every tap got its display reply").hasSize(taps);
    assertThat(run.stored()).as("every tap is stored").isEqualTo(taps);
    assertThat(run.published() * 60.0 / run.seconds()).as("readings per minute taken").isGreaterThanOrEqualTo(QAS02_PER_MINUTE);
    assertThat(run.percentile(95)).as("95th percentile of the reply (ms)").isLessThanOrEqualTo(QAS01_LIMIT_MS);
    assertThat(run.median(last)).as("median of the last third against the first").isLessThanOrEqualTo(Math.max(2 * run.median(first), 250));
  }

  @Test
  @Order(3)
  void qas02_aBurstOfTwoHundredReadingsAtOnce_isQueuedAndTakenWithoutLoss() throws Exception {
    Run run = run(200, 0); // as fast as the publisher can: the class-start peak

    report.append(describe("QAS02 · burst of 200 readings published at once", run));
    assertThat(run.latenciesMs()).as("every tap got its display reply").hasSize(200);
    assertThat(run.stored()).isEqualTo(200);
    assertThat(run.seconds()).as("seconds to take the whole burst").isLessThanOrEqualTo(60);
  }

  @org.junit.jupiter.api.AfterAll
  static void keep() {
    // The report is written by the last test through writeReport(); nothing to do here.
  }

  @Test
  @Order(4)
  void writeReport() throws Exception {
    Files.writeString(Path.of("target", "load-test-results.md"), report.toString(), StandardCharsets.UTF_8);
    System.out.println(report);
  }

  // ---------- The run ----------

  /**
   * Publishes {@code count} taps, {@code perSecond} per second (0 for as fast as possible), each from a different
   * card and reader in turn, and collects the display replies with the time each took.
   */
  private Run run(int count, double perSecond) throws Exception {
    Queue replies = new Queue("load.display." + UUID.randomUUID(), true, false, false);
    admin.declareQueue(replies);
    Binding binding =
        BindingBuilder.bind(replies).to(new TopicExchange("tarjepafi.devices")).with("tarjepafi.readers.*.display");
    admin.declareBinding(binding);

    var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    long storedBefore = taps.count();
    long statementsBefore = statistics.getPrepareStatementCount();
    Map<String, Long> sentAt = new ConcurrentHashMap<>();
    Map<String, Long> repliedAt = new ConcurrentHashMap<>();
    Thread collector =
        new Thread(
            () -> {
              try {
                while (repliedAt.size() < count && !Thread.currentThread().isInterrupted()) {
                  Message reply = template.receive(replies.getName(), 200);
                  if (reply != null) {
                    String eventId = mapper.readTree(reply.getBody()).path("eventId").asString();
                    repliedAt.putIfAbsent(eventId, System.nanoTime());
                  }
                }
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            },
            "load-collector");
    collector.start();

    long start = System.nanoTime();
    long interval = perSecond > 0 ? (long) (1_000_000_000L / perSecond) : 0;
    for (int i = 0; i < count; i++) {
      if (interval > 0) {
        long due = start + i * interval;
        long wait = due - System.nanoTime();
        if (wait > 0) {
          Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
        }
      }
      String eventId = UUID.randomUUID().toString();
      String reader = READERS.get(i % READERS.size());
      sentAt.put(eventId, System.nanoTime());
      template.send(
          "tarjepafi.devices",
          "tarjepafi.readers." + reader + ".tap",
          MessageBuilder.withBody(tap(eventId, reader, CARDS.get(i % CARDS.size())).getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build());
    }
    long deadline = System.nanoTime() + 120_000_000_000L;
    while (repliedAt.size() < count && System.nanoTime() < deadline) {
      Thread.sleep(100);
    }
    long seconds = Math.max(1, (System.nanoTime() - start) / 1_000_000_000L);
    collector.interrupt();
    admin.deleteQueue(replies.getName());

    List<Long> latencies = new ArrayList<>();
    sentAt.forEach(
        (eventId, sent) -> {
          Long replied = repliedAt.get(eventId);
          if (replied != null) {
            latencies.add((replied - sent) / 1_000_000);
          }
        });
    // Latencies in sending order are needed for the thirds: re-sort by the time the tap was sent.
    List<Map.Entry<String, Long>> bySent = new ArrayList<>(sentAt.entrySet());
    bySent.sort(Map.Entry.comparingByValue());
    List<Long> ordered = new ArrayList<>();
    for (Map.Entry<String, Long> entry : bySent) {
      Long replied = repliedAt.get(entry.getKey());
      if (replied != null) {
        ordered.add((replied - entry.getValue()) / 1_000_000);
      }
    }
    long statements = statistics.getPrepareStatementCount() - statementsBefore;
    return new Run(ordered, count, seconds, (int) (taps.count() - storedBefore), statements);
  }

  private String describe(String title, Run run) {
    return ("## %s\n\n- taps published: %d in %d s (%.0f per minute)\n- replies received: %d, stored: %d\n"
            + "- latency to the display reply: p50 %d ms, p95 %d ms, p99 %d ms, max %d ms\n"
            + "- SQL statements: %d in total, %.1f per tap (each one is a round trip to the database)\n\n")
        .formatted(
            title,
            run.published(),
            run.seconds(),
            run.published() * 60.0 / run.seconds(),
            run.latenciesMs().size(),
            run.stored(),
            run.percentile(50),
            run.percentile(95),
            run.percentile(99),
            run.percentile(100),
            run.statements(),
            run.statements() / (double) run.published());
  }

  private static String tap(String eventId, String readerId, String uid) {
    return ("{\"v\":1,\"type\":\"CardTapped\",\"eventId\":\"%s\",\"readerId\":\"%s\",\"cardUid\":\"%s\","
            + "\"tappedAt\":\"%s\",\"buffered\":false}")
        .formatted(eventId, readerId, uid, Instant.now());
  }
}
