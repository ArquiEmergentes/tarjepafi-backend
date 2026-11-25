package com.pafisolutions.tarjepafi.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * D14: the OpenAPI document of the application carries an example for every field of every request and response
 * model, so Swagger UI shows a ready body for each route. A new field without an example fails this test. Skipped when
 * Docker is not running.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class OpenApiDocumentationTests {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired WebApplicationContext context;
  @Autowired ObjectMapper mapper;

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void everyFieldOfEveryModel_hasAnExample() throws Exception {
    JsonNode document =
        mapper.readTree(
            mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse().getContentAsString());

    List<String> missing = new ArrayList<>();
    int checked = 0;
    for (var schema : document.path("components").path("schemas").properties()) {
      for (var property : schema.getValue().path("properties").properties()) {
        JsonNode definition = property.getValue();
        boolean reference = definition.has("$ref");
        boolean isObject = "object".equals(definition.path("type").asString()) && !definition.has("example");
        boolean isArrayOfModels = "array".equals(definition.path("type").asString()) && definition.path("items").has("$ref");
        if (reference || isArrayOfModels || (isObject && definition.has("additionalProperties"))) {
          continue; // a model or a list of models: its own fields carry the examples
        }
        checked++;
        if (!definition.has("example")) {
          missing.add(schema.getKey() + "." + property.getKey());
        }
      }
    }

    assertThat(checked).as("fields checked").isGreaterThan(250);
    assertThat(missing).as("fields without an example").isEmpty();
  }

  @Test
  void examples_haveTheTypeOfTheirField() throws Exception {
    JsonNode schemas =
        mapper.readTree(
            mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse().getContentAsString())
            .path("components")
            .path("schemas");

    assertThat(schemas.path("SignInResource").path("properties").path("email").path("example").asString())
        .isEqualTo("docente@upc.edu.pe");
    assertThat(schemas.path("CredentialResource").path("properties").path("availableActions").path("example").isArray())
        .as("a list example is a JSON array")
        .isTrue();
    assertThat(schemas.path("CreateSpaceRequest").path("properties").path("allowedRoles").path("example").isArray()).isTrue();
    assertThat(schemas.path("ConfigurationRequest").path("properties").path("heartbeatInterval").path("example").isNumber())
        .as("a number example is a JSON number")
        .isTrue();
    assertThat(schemas.path("TapEventResource").path("properties").path("buffered").path("example").isBoolean()).isTrue();
  }

  @Test
  void theDocument_describesTheErrorsAndHowToSignIn() throws Exception {
    JsonNode document =
        mapper.readTree(
            mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs")).andReturn().getResponse().getContentAsString());

    assertThat(document.path("info").path("description").asString()).contains("TOO_MANY_ATTEMPTS").contains("Authorize");
    assertThat(document.path("components").path("schemas").has("Problem")).isTrue();
    assertThat(document.path("components").path("schemas").path("Problem").path("example").path("code").asString())
        .isNotBlank();
  }
}
