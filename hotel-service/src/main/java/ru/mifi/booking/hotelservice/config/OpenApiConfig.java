package ru.mifi.booking.hotelservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI конфигурация для Hotel Service.
 *
 * <p>
 * Важно: в этом сервисе есть internal endpoints (confirm-availability / release),
 * которые booking-service вызывает напрямую (НЕ через gateway).
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI-спецификация + схема Bearer JWT.
     *
     * @return OpenAPI-конфигурация
     */
    @Bean
    public OpenAPI hotelServiceOpenApi() {
        // Схема безопасности будет доступна в Swagger UI (кнопка Authorize).
        return new OpenAPI()
                .info(new Info()
                        .title("Hotel Service API")
                        .version("1.0")
                        .description("CRUD отелей/номеров, рекомендации по загрузке и internal endpoints для саги."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
