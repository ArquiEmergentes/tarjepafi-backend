package com.pafisolutions.tarjepafi.shared.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Declares the bearer JWT scheme so Swagger UI shows the "Authorize" button: sign in at
 * {@code POST /api/v1/auth/sign-in}, paste the token (without the "Bearer " prefix) and every request carries it.
 */
@Configuration
class OpenApiConfiguration {

    static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI tarjepafiOpenApi() {
        return new OpenAPI()
                .info(new Info().title("TarjePAFI Smart Campus API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
