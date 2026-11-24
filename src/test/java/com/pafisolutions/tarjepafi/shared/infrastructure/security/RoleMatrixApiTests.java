package com.pafisolutions.tarjepafi.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 9 role review (QAS09): every route of the application, called with no token and with a token of each role,
 * is allowed or refused (401 or 403) exactly as the matrix below says. The matrix is the documented policy
 * (docs/ROLE_MATRIX.md); a route that is added without being considered falls in the default row, so the test also
 * fails when a new route is more open than "any signed-in user" by mistake. Skipped when Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class RoleMatrixApiTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  /** Who may call a route. */
  enum Access {
    PUBLIC,
    SIGNED_IN,
    ADMIN,
    TEACHER
  }

  /** A row of the matrix: method (null for any), an ant pattern, and who may call. First match wins. */
  record Rule(HttpMethod method, String pattern, Access access) {}

  private static final List<Rule> MATRIX =
      List.of(
          new Rule(HttpMethod.POST, "/api/v1/auth/sign-in", Access.PUBLIC),
          new Rule(HttpMethod.PUT, "/api/v1/simulation/clock", Access.ADMIN),
          new Rule(null, "/api/v1/credentials/**", Access.ADMIN),
          new Rule(null, "/api/v1/card-holders/**", Access.ADMIN),
          new Rule(null, "/api/v1/iot/**", Access.ADMIN),
          new Rule(null, "/api/v1/access-records/**", Access.ADMIN),
          new Rule(null, "/api/v1/work-shifts/**", Access.ADMIN),
          new Rule(null, "/api/v1/data-records/**", Access.ADMIN),
          new Rule(null, "/api/v1/dashboard/**", Access.ADMIN),
          new Rule(null, "/api/v1/reports/**", Access.ADMIN),
          new Rule(null, "/api/v1/reservations/**", Access.ADMIN),
          new Rule(HttpMethod.POST, "/api/v1/spaces", Access.ADMIN),
          new Rule(HttpMethod.PATCH, "/api/v1/spaces/**", Access.ADMIN),
          new Rule(null, "/api/v1/spaces/*/reservations", Access.ADMIN),
          new Rule(null, "/api/v1/spaces/*/occupancy", Access.ADMIN),
          new Rule(null, "/api/v1/class-sessions/**", Access.TEACHER),
          // Everything else under /api/v1 (me, the clock reading, spaces and catalogues reads): any signed-in user.
          new Rule(null, "/api/v1/**", Access.SIGNED_IN));

  @Autowired WebApplicationContext context;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
  @Autowired ObjectMapper mapper;

  MockMvc mvc;
  Map<String, String> tokens = new LinkedHashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    tokens.put("ADMIN", signIn("admin@upc.edu.pe", "Admin123*"));
    tokens.put("TEACHER", signIn("docente@upc.edu.pe", "Docente123*"));
    tokens.put("STAFF", signIn("trabajador@upc.edu.pe", "User123*"));
    tokens.put("STUDENT", signIn("u202312966@upc.edu.pe", "User123*"));
  }

  @Test
  void everyRoute_isAllowedOrRefusedAsTheMatrixSays() throws Exception {
    List<String> wrong = new ArrayList<>();
    int checked = 0;
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = entry.getKey();
      if (info.getPathPatternsCondition() == null) {
        continue;
      }
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (!pattern.startsWith("/api/v1")) {
          continue;
        }
        for (HttpMethod method : methodsOf(info)) {
          Access access = accessFor(method, pattern);
          String path = pattern.replaceAll("\\{[^/}]+}", "x");
          checked++;
          check(wrong, method, pattern, path, "anonymous", null, access == Access.PUBLIC);
          for (String role : tokens.keySet()) {
            boolean allowed =
                switch (access) {
                  case PUBLIC, SIGNED_IN -> true;
                  case ADMIN -> role.equals("ADMIN");
                  case TEACHER -> role.equals("TEACHER");
                };
            check(wrong, method, pattern, path, role, tokens.get(role), allowed);
          }
        }
      }
    }

    assertThat(checked).as("routes checked").isGreaterThan(60);
    assertThat(wrong).as("routes whose access differs from the matrix").isEmpty();
  }

  @Test
  void swaggerAndHealth_arePublic_andTheRestOfTheApplicationIsNot() throws Exception {
    assertThat(status(MockMvcRequestBuilders.get("/actuator/health"))).isEqualTo(200);
    assertThat(status(MockMvcRequestBuilders.get("/v3/api-docs"))).isEqualTo(200);
    assertThat(status(MockMvcRequestBuilders.get("/actuator/env"))).isIn(401, 403, 404);
    assertThat(status(MockMvcRequestBuilders.get("/some/other/path"))).isIn(401, 403);
  }

  // ---------- helpers ----------

  private void check(
      List<String> wrong, HttpMethod method, String pattern, String path, String who, String token, boolean allowed)
      throws Exception {
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path).contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    int status = status(request);
    boolean refused = status == 401 || status == 403;
    if (refused == allowed) {
      wrong.add(who + " " + method + " " + pattern + " -> " + status + (allowed ? " (should be allowed)" : " (should be refused)"));
    }
    if (!allowed && token != null && status != 403) {
      wrong.add(who + " " + method + " " + pattern + " -> " + status + " (a signed-in caller of another role must get 403)");
    }
    if (!allowed && token == null && status != 401) {
      wrong.add(who + " " + method + " " + pattern + " -> " + status + " (an anonymous caller must get 401)");
    }
  }

  private int status(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  private static Set<HttpMethod> methodsOf(RequestMappingInfo info) {
    Set<HttpMethod> methods = new java.util.LinkedHashSet<>();
    info.getMethodsCondition().getMethods().forEach(m -> methods.add(HttpMethod.valueOf(m.name())));
    if (methods.isEmpty()) {
      methods.add(HttpMethod.GET);
    }
    return methods;
  }

  private static Access accessFor(HttpMethod method, String path) {
    AntPathMatcher matcher = new AntPathMatcher();
    for (Rule rule : MATRIX) {
      if ((rule.method() == null || rule.method().equals(method)) && matcher.match(rule.pattern(), path)) {
        return rule.access();
      }
    }
    return Access.SIGNED_IN;
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
}
