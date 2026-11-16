package com.pafisolutions.tarjepafi.iam.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Sign-in and token rules (TS02 to TS04) against the demo users, the same seven accounts the web
 * app's README documents. Skipped when Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired WebApplicationContext context;

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void signIn_activeAdmin_returnsTokenAndUserWithoutPassword() throws Exception {
    mvc.perform(signIn("admin@upc.edu.pe", "Admin123*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.user.id").value("usr-001"))
        .andExpect(jsonPath("$.user.role").value("ADMIN"))
        .andExpect(jsonPath("$.user.cardHolderId").doesNotExist())
        .andExpect(jsonPath("$.user.createdAt").value("2026-08-17T08:00:00"))
        .andExpect(jsonPath("$.user.password").doesNotExist())
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
  }

  @Test
  void signIn_emailWithUpperCaseAndSpaces_isAccepted() throws Exception {
    mvc.perform(signIn("  Docente@UPC.edu.pe ", "Docente123*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.cardHolderId").value("ch-d0001"));
  }

  @Test
  void signIn_nonInstitutionalEmail_returns400InvalidEmail() throws Exception {
    mvc.perform(signIn("admin@gmail.com", "Admin123*"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
  }

  @Test
  void signIn_wrongPassword_returns401InvalidCredentials() throws Exception {
    mvc.perform(signIn("admin@upc.edu.pe", "wrong"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void signIn_unknownEmail_returnsSameErrorAsWrongPassword() throws Exception {
    mvc.perform(signIn("nobody@upc.edu.pe", "Admin123*"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void signIn_inactiveOrSuspendedAccount_returns403AccountDisabled() throws Exception {
    mvc.perform(signIn("jsalazar@upc.edu.pe", "Docente123*"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    mvc.perform(signIn("soporte@upc.edu.pe", "Admin123*"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
  }

  @Test
  void signIn_disabledAccountWithWrongPassword_doesNotRevealTheAccountState() throws Exception {
    mvc.perform(signIn("soporte@upc.edu.pe", "wrong"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void signIn_blankPassword_returns400ValidationFailed() throws Exception {
    mvc.perform(signIn("admin@upc.edu.pe", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void me_withoutToken_returns401() throws Exception {
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void me_tamperedToken_returns401() throws Exception {
    String token = tokenOf("admin@upc.edu.pe", "Admin123*");
    mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void me_validToken_returnsTheCaller() throws Exception {
    String token = tokenOf("u202312966@upc.edu.pe", "User123*");
    mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("u202312966@upc.edu.pe"))
        .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void moveClock_teacherToken_returns403_adminToken_returns200() throws Exception {
    String body = "{\"now\":\"2026-10-07T10:05:00\"}";
    mvc.perform(
            put("/api/v1/simulation/clock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf("docente@upc.edu.pe", "Docente123*"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/simulation/clock")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf("admin@upc.edu.pe", "Admin123*"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.now").value("2026-10-07T10:05:00"));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signIn(
      String email, String password) {
    return post("/api/v1/auth/sign-in")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
  }

  private String tokenOf(String email, String password) throws Exception {
    String json =
        mvc.perform(signIn(email, password))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = com.jayway.jsonpath.JsonPath.read(json, "$.token");
    assertThat(token).isNotBlank();
    return token;
  }
}
