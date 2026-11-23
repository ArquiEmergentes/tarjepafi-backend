package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort.AccessDecision;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Credentials API against the demo data set: US10, US11, US13, US14, US15, the catalogues and the role
 * rules. Each test rolls back its changes. Skipped when Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CredentialsApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;
  @Autowired CardHolderDirectoryPort directory;

  MockMvc mvc;
  String admin;
  String teacher;

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    admin = signIn("admin@upc.edu.pe", "Admin123*");
    teacher = signIn("docente@upc.edu.pe", "Docente123*");
  }

  // ---------- Roles ----------

  @Test
  void credentials_withoutToken_is401() throws Exception {
    mvc.perform(get("/api/v1/credentials")).andExpect(status().isUnauthorized());
  }

  @Test
  void credentials_asTeacher_is403ButSettingsAndCataloguesAreOpen() throws Exception {
    mvc.perform(as(teacher, get("/api/v1/credentials"))).andExpect(status().isForbidden());
    mvc.perform(as(teacher, get("/api/v1/card-holders"))).andExpect(status().isForbidden());
    mvc.perform(as(teacher, get("/api/v1/settings"))).andExpect(status().isOk());
    mvc.perform(as(teacher, get("/api/v1/spaces"))).andExpect(status().isOk());
    mvc.perform(as(teacher, get("/api/v1/courses"))).andExpect(status().isOk());
  }

  // ---------- Reads ----------

  @Test
  void list_all_returnsTheFiftyFiveDemoCardsWithTheirHolders() throws Exception {
    mvc.perform(as(admin, get("/api/v1/credentials")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(55)))
        .andExpect(jsonPath("$[?(@.id=='cred-001')].holder.fullName").isNotEmpty());
  }

  @Test
  void list_filters_byStatusAndText() throws Exception {
    mvc.perform(as(admin, get("/api/v1/credentials").param("status", "BLOCKED")))
        .andExpect(jsonPath("$", hasSize(2)));
    mvc.perform(as(admin, get("/api/v1/credentials").param("q", "u202312966")))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("cred-001"))
        .andExpect(jsonPath("$[0].availableActions[0]").value("BLOCK"));
  }

  @Test
  void get_unknownCard_is404WithCode() throws Exception {
    mvc.perform(as(admin, get("/api/v1/credentials/nope")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CREDENTIAL_NOT_FOUND"));
  }

  @Test
  void cardHolders_filters_byCodeRoleAndText() throws Exception {
    mvc.perform(as(admin, get("/api/v1/card-holders").param("code", "u202312966")))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("ch-u202312966"))
        .andExpect(jsonPath("$[0].role").value("STUDENT"));
    mvc.perform(as(admin, get("/api/v1/card-holders").param("code", "U000")))
        .andExpect(jsonPath("$", hasSize(0)));
    mvc.perform(as(admin, get("/api/v1/card-holders").param("role", "TEACHER")))
        .andExpect(jsonPath("$", hasSize(4)));
    mvc.perform(as(admin, get("/api/v1/card-holders/nope")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HOLDER_NOT_FOUND"));
  }

  @Test
  void settings_returnsTermDeadlineAndTolerance() throws Exception {
    mvc.perform(as(teacher, get("/api/v1/settings")))
        .andExpect(jsonPath("$.term").value("2026-2"))
        .andExpect(jsonPath("$.enrollmentDeadline").value("2026-10-03T23:59:59"))
        .andExpect(jsonPath("$.defaultToleranceMinutes").value(10));
  }

  @Test
  void catalogues_spacesCoursesSchedulesEnrollments() throws Exception {
    mvc.perform(as(teacher, get("/api/v1/spaces"))).andExpect(jsonPath("$", hasSize(15)));
    mvc.perform(as(teacher, get("/api/v1/spaces").param("type", "CLASSROOM")))
        .andExpect(jsonPath("$", hasSize(6)));
    mvc.perform(as(teacher, get("/api/v1/spaces/sp-a301")))
        .andExpect(jsonPath("$.code").value("A-301"))
        .andExpect(jsonPath("$.allowedRoles[0]").value("STUDENT"))
        .andExpect(jsonPath("$.allowedRoles[1]").value("TEACHER"));
    mvc.perform(as(teacher, get("/api/v1/spaces/nope")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    mvc.perform(as(teacher, get("/api/v1/courses"))).andExpect(jsonPath("$", hasSize(6)));
    mvc.perform(as(teacher, get("/api/v1/class-schedules").param("teacherId", "ch-d0001")))
        .andExpect(jsonPath("$.length()", greaterThan(0)))
        .andExpect(jsonPath("$[0].startTime").exists());
    mvc.perform(as(teacher, get("/api/v1/enrollments").param("scheduleId", "sch-01")))
        .andExpect(jsonPath("$", hasSize(20)));
  }

  // ---------- US10: block, report lost, deactivate ----------

  @Test
  void block_withoutNote_is400NoteRequired() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-001/block")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
  }

  @Test
  void block_activeCard_blocksItAndWritesTheHistory() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-001/block")).content("{\"note\":\"Found damaged\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.availableActions[0]").value("DEACTIVATE"));

    mvc.perform(as(admin, get("/api/v1/credentials/cred-001/history")))
        .andExpect(jsonPath("$[0].fromStatus").value("ACTIVE"))
        .andExpect(jsonPath("$[0].toStatus").value("BLOCKED"))
        .andExpect(jsonPath("$[0].reason").value("MANUAL"))
        .andExpect(jsonPath("$[0].changedBy").value("usr-001"))
        .andExpect(jsonPath("$[0].note").value("Found damaged"));
  }

  @Test
  void block_alreadyBlockedCard_is409ActionNotAllowed() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-015/block")).content("{\"note\":\"x\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));
  }

  @Test
  void reportLost_activeCard_usesReasonLost() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-001/report-lost")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOST"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-001/history")))
        .andExpect(jsonPath("$[0].reason").value("LOST"));
  }

  @Test
  void deactivate_lostCard_deactivatesIt() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-009/deactivate")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DEACTIVATED"));
  }

  @Test
  void deactivate_deactivatedCard_is409() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-039/deactivate")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));
  }

  // ---------- US13: reactivate ----------

  @Test
  void reactivate_blockedCardOfCompletedStudent_returnsToActive() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-015/reactivate")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-015/history")))
        .andExpect(jsonPath("$[0].reason").value("MANUAL"));
  }

  @Test
  void reactivate_deactivatedCardOfCompletedStudent_usesReasonReenrollment() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-039/reactivate")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-039/history")))
        .andExpect(jsonPath("$[0].reason").value("REENROLLMENT"));
  }

  @Test
  void reactivate_studentWithPendingEnrollment_is422() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-024/reactivate")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_COMPLETED"));
  }

  @Test
  void reactivate_holderAlreadyHasAnActiveCard_is409() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-009/reactivate")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOLDER_HAS_ACTIVE_CARD"));
  }

  @Test
  void reactivate_activeCard_is409() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-001/reactivate")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));
  }

  // ---------- US11: link a holder ----------

  @Test
  void linkHolder_stockCard_linksItAndKeepsTheStatus() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-054/link-holder")).content("{\"holderCode\":\"u202311918\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardHolderId").value("ch-u202311918"))
        .andExpect(jsonPath("$.holder.code").value("U202311918"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-054/history")))
        .andExpect(jsonPath("$[0].fromStatus").value("ACTIVE"))
        .andExpect(jsonPath("$[0].toStatus").value("ACTIVE"))
        .andExpect(jsonPath("$[0].reason").value("MANUAL"));
  }

  @Test
  void linkHolder_sameHolderAgain_is409AlreadyLinked() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-001/link-holder")).content("{\"holderCode\":\"U202312966\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
  }

  @Test
  void linkHolder_unknownCode_is404() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-054/link-holder")).content("{\"holderCode\":\"U000\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HOLDER_NOT_FOUND"));
  }

  @Test
  void linkHolder_activeCardToHolderWithAnActiveCard_is409() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-054/link-holder")).content("{\"holderCode\":\"U202312966\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOLDER_HAS_ACTIVE_CARD"));
  }

  @Test
  void linkHolder_blankCode_is400Validation() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/cred-054/link-holder")).content("{\"holderCode\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // ---------- US14: permission exceptions ----------

  @Test
  void permissions_setReplaceAndRemove_workPerSpace() throws Exception {
    mvc.perform(as(admin, put("/api/v1/credentials/cred-001/permissions/sp-a301")).content("{\"effect\":\"DENY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effect").value("DENY"));
    mvc.perform(as(admin, put("/api/v1/credentials/cred-001/permissions/sp-a301")).content("{\"effect\":\"ALLOW\"}"))
        .andExpect(jsonPath("$.effect").value("ALLOW"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-001/permissions")))
        .andExpect(jsonPath("$", hasSize(2)));
    mvc.perform(as(admin, delete("/api/v1/credentials/cred-001/permissions/sp-a301")))
        .andExpect(status().isNoContent());
    mvc.perform(as(admin, delete("/api/v1/credentials/cred-001/permissions/sp-a301")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));
  }

  @Test
  void permissions_unknownSpaceOrBadEffect_isRejected() throws Exception {
    mvc.perform(as(admin, put("/api/v1/credentials/cred-001/permissions/nope")).content("{\"effect\":\"ALLOW\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
    mvc.perform(as(admin, put("/api/v1/credentials/cred-001/permissions/sp-a301")).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // ---------- US15: enrollment verification ----------

  @Test
  void enrollmentVerification_preview_listsWithoutChangingAnything() throws Exception {
    mvc.perform(as(admin, get("/api/v1/credentials/enrollment-verification/preview")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deadlinePassed").value(true))
        .andExpect(jsonPath("$.deactivated", hasSize(5)));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-006")))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void enrollmentVerification_run_deactivatesPendingStudentsAsSystem() throws Exception {
    mvc.perform(as(admin, post("/api/v1/credentials/enrollment-verification")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivated", hasSize(5)))
        .andExpect(jsonPath("$.failedCount").value(0))
        .andExpect(jsonPath("$.deactivated[0].status").value("DEACTIVATED"));
    mvc.perform(as(admin, get("/api/v1/credentials/cred-006/history")))
        .andExpect(jsonPath("$[0].changedBy").value("SYSTEM"))
        .andExpect(jsonPath("$[0].reason").value("ENROLLMENT_NOT_COMPLETED"));
    mvc.perform(as(admin, post("/api/v1/credentials/enrollment-verification")))
        .andExpect(jsonPath("$.deactivated", hasSize(0)));
  }

  // ---------- The port other contexts use (TS29) ----------

  @Test
  void directory_findByCardUid_ignoresCaseAndReturnsTheHolder() {
    var lookup = directory.findByCardUid("04:3d:9b:12:8f").orElseThrow();

    assertThat(lookup.credentialId()).isEqualTo("cred-001");
    assertThat(lookup.active()).isTrue();
    assertThat(lookup.holder().code()).isEqualTo("U202312966");
    assertThat(directory.findByCardUid("00:00:00:00:00")).isEmpty();
  }

  @Test
  void directory_resolveAccess_cardExceptionFirstThenRoles() {
    assertThat(directory.resolveAccess("cred-001", "sp-lab-e", List.of("TEACHER", "STAFF")))
        .isEqualTo(new AccessDecision(true, "EXCEPTION"));
    assertThat(directory.resolveAccess("cred-047", "sp-srv", List.of("STAFF")))
        .isEqualTo(new AccessDecision(false, "EXCEPTION"));
    assertThat(directory.resolveAccess("cred-001", "sp-a301", List.of("STUDENT", "TEACHER")))
        .isEqualTo(new AccessDecision(true, "ROLE"));
    assertThat(directory.resolveAccess("cred-001", "sp-srv", List.of("STAFF")))
        .isEqualTo(new AccessDecision(false, "ROLE"));
  }

  // ---------- Helpers ----------

  private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
    return request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON);
  }

  private String signIn(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/sign-in")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = mapper.readTree(body);
    return json.path("token").asString();
  }
}
