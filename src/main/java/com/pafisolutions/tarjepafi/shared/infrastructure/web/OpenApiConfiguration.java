package com.pafisolutions.tarjepafi.shared.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Declares the bearer JWT scheme so Swagger UI shows the "Authorize" button, describes how to use the
 * API in a few lines and publishes the shape of the errors, which every route can answer.
 */
@Configuration
class OpenApiConfiguration {

  static final String BEARER = "bearerAuth";

  private static final String DESCRIPTION =
      """
      Backend of TarjePAFI Smart Campus. **To try it here:** call `POST /api/v1/auth/sign-in` with a demo account, copy the \
      `token` of the answer, press **Authorize** and paste it (without `Bearer `).

      - Dates and times are campus local time without offset, for example `2026-10-07T09:50:00`.
      - Lists a live screen watches answer `{ items, nextCursor }`, newest first; send `nextCursor` back as `after` to \
      get only what is new.
      - Errors are problem documents (`Problem`) with a stable `code`: 400 `VALIDATION_FAILED` / `INVALID_*`, 401 \
      `INVALID_CREDENTIALS`, 403 (wrong role), 404 `*_NOT_FOUND`, 409 conflicts, 422 broken rules, 429 \
      `TOO_MANY_ATTEMPTS` (with `Retry-After`).
      - The written contract, with every error code per section, is `docs/API.md`; ready requests are in `docs/http`.
      """;

  @Bean
  OpenAPI tarjepafiOpenApi() {
    return new OpenAPI()
        .info(new Info().title("TarjePAFI Smart Campus API").version("v1").description(DESCRIPTION))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  /** Every route can answer an error: say so once, with the shape of {@code Problem}, instead of on each route. */
  @Bean
  OpenApiCustomizer errorResponses() {
    return openApi -> {
      // springdoc rebuilds the schemas of the document from the controllers, so this one is added here.
      openApi.getComponents().addSchemas("Problem", problem());
      ApiResponse error =
          new ApiResponse()
              .description("Any error: a problem document whose `code` says what happened")
              .content(
                  new Content()
                      .addMediaType(
                          "application/json",
                          new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem"))));
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(operation -> operation.getResponses().addApiResponse("default", error)));
    };
  }

  /** RFC 9457 problem document with the extra {@code code} of this API. */
  private static Schema<?> problem() {
    Map<String, Object> example = new LinkedHashMap<>();
    example.put("title", "Conflict");
    example.put("status", 409);
    example.put("detail", "The space is already reserved in that slot");
    example.put("instance", "/api/v1/spaces/sp-cub-l1/reservations");
    example.put("code", "RESERVATION_OVERLAP");
    Schema<?> schema =
        new ObjectSchema()
            .description("Every error answer. `code` is stable and meant to be mapped to your own texts.")
            .addProperty("title", new StringSchema().example("Conflict"))
            .addProperty("status", new IntegerSchema().example(409))
            .addProperty("detail", new StringSchema().example("The space is already reserved in that slot"))
            .addProperty("instance", new StringSchema().example("/api/v1/spaces/sp-cub-l1/reservations"))
            .addProperty("code", new StringSchema().example("RESERVATION_OVERLAP"));
    schema.setExample(example);
    return schema;
  }
}
