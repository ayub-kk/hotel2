package ru.mifi.booking.hotelservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import ru.mifi.booking.common.dto.ErrorDto;

import java.io.IOException;
import java.time.Instant;

/**
 * Единый JSON-ответ на 403 Forbidden.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        String requestId = SecurityRequestIdExtractor.getOrCreate(request, response);

        ErrorDto dto = new ErrorDto(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied",
                request.getRequestURI(),
                requestId
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), dto);
    }
}
