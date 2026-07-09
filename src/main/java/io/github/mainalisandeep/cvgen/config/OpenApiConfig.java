package io.github.mainalisandeep.cvgen.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger configuration for OpenAPI documentation.
 * Registers a bean that customizes the OpenAPI specification for the Cloud Colleague application.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CVgen API Documentation",
                description = "API documentation for CVgen application",
                version = "1.0.0"
        ),
        servers = {
                @Server(
                        url = "http://localhost:8080",
                        description = "CVgen local Server"
                )
        },
        security = {
                @SecurityRequirement(name = "Authorization")
        }
)
@SecurityScheme(
        name = "Authorization",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}