package ru.mifi.booking.hotelservice.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.mifi.booking.common.dto.ErrorDto;
import ru.mifi.booking.common.exception.ApiException;
import ru.mifi.booking.common.http.RequestHeaders;

/**
 * Глобальный обработчик ошибок xxx-service.
 *
 * <p>
 * Я привожу все ошибки к единому формату {@link ErrorDto},
 * возвращая корректные HTTP-статусы и requestId.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    /**
     * Обработка ожидаемых бизнес-ошибок (404/409/401 и т.д.).
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorDto dto = buildDto(status, resolveErrorCode(ex), ex.getMessage(), request);
        return ResponseEntity.status(status).body(dto);
    }

    /**
     * Ошибки валидации DTO (@Valid).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidationException(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        if (message == null || message.isBlank()) {
            message = "Validation error";
        }

        ErrorDto dto = buildDto(status, "VALIDATION_ERROR", message, request);
        return ResponseEntity.status(status).body(dto);
    }

//    /**
//     * 401 Unauthorized — пользователь не аутентифицирован.
//     * (актуально после подключения Security)
//     */
//    @ExceptionHandler(AuthenticationException.class)
//    public ResponseEntity<ErrorDto> handleAuthentication(AuthenticationException ex,
//                                                         HttpServletRequest request) {
//
//        HttpStatus status = HttpStatus.UNAUTHORIZED;
//        ErrorDto dto = buildDto(status, "UNAUTHORIZED", "Authentication required", request);
//        return ResponseEntity.status(status).body(dto);
//    }
//
//    /**
//     * 403 Forbidden — доступ запрещён.
//     * (актуально после подключения Security)
//     */
//    @ExceptionHandler(AccessDeniedException.class)
//    public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException ex,
//                                                       HttpServletRequest request) {
//
//        HttpStatus status = HttpStatus.FORBIDDEN;
//        ErrorDto dto = buildDto(status, "ACCESS_DENIED", "Access denied", request);
//        return ResponseEntity.status(status).body(dto);
//    }

    /**
     * Фолбэк для всех непредвиденных ошибок.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleAnyException(Exception ex, HttpServletRequest request) {

        log.error("Unexpected error", ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorDto dto = buildDto(status, "INTERNAL_ERROR", ex.getMessage(), request);
        return ResponseEntity.status(status).body(dto);
    }

    private ErrorDto buildDto(HttpStatus status,
                              String error,
                              String message,
                              HttpServletRequest request) {

        String requestId = resolveRequestId(request);

        return new ErrorDto(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                requestId
        );
    }

    private String resolveErrorCode(Exception ex) {
        return ex.getClass()
                .getSimpleName()
                .replace("Exception", "")
                .toUpperCase();
    }

    /**
     * Получение requestId в приоритетном порядке:
     * 1) из заголовка X-Request-Id
     * 2) из request attribute
     * 3) из MDC
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(RequestHeaders.X_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        Object attr = request.getAttribute(RequestHeaders.X_REQUEST_ID);
        if (attr != null) {
            String attrValue = String.valueOf(attr);
            if (!attrValue.isBlank()) {
                return attrValue;
            }
        }

        String mdcValue = MDC.get(MDC_REQUEST_ID_KEY);
        if (mdcValue != null && !mdcValue.isBlank()) {
            return mdcValue;
        }

        return null;
    }
}

