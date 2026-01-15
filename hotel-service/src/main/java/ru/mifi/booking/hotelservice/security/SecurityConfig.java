package ru.mifi.booking.hotelservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Security-конфигурация hotel-service.
 *
 * <p>
 * Я настраиваю сервис как Resource Server:
 * - принимаю JWT от booking-service
 * - извлекаю роль из claim "role" (USER/ADMIN/SERVICE)
 * - ограничиваю доступ к endpoints по ролям
 * </p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final String secret;

    public SecurityConfig(@Value("${security.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // REST API: сессии не нужны
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF выключаем (у нас stateless API)
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Actuator оставляем доступным для health/info (при желании можно тоже закрыть)
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Swagger / OpenAPI (чтобы проверяющий мог открыть Swagger UI без токена)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // ===== Admin-only операции =====
                        .requestMatchers(HttpMethod.POST, "/api/hotels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/hotels/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/hotels/**").hasRole("ADMIN")

                        // RoomController#add
                        .requestMatchers(HttpMethod.POST, "/api/rooms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("ADMIN")

                        // ===== Статистика =====
                        .requestMatchers(HttpMethod.GET, "/api/rooms/stats").hasRole("ADMIN")

                        // ===== Публичные ручки (но только для аутентифицированных USER|ADMIN) =====
                        .requestMatchers(HttpMethod.GET, "/api/hotels/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**").hasAnyRole("USER", "ADMIN", "SERVICE")
                        // ===== Internal endpoints (под 2.4 заложим SERVICE) =====
                        .requestMatchers(HttpMethod.POST, "/api/rooms/*/confirm-availability").hasRole("SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/rooms/*/release").hasRole("SERVICE")

                        // Всё остальное — только с валидным JWT
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * Декодер JWT для HS256 (симметричный секрет).
     * Секрет должен совпадать с booking-service, который эти токены подписывает.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
    }

    /**
     * Конвертация claim "role" -> GrantedAuthority вида ROLE_<ROLE>.
     * Например role=ADMIN -> ROLE_ADMIN.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });

        return converter;
    }
}