package ru.mifi.booking.hotelservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import ru.mifi.booking.common.http.RequestHeaders;

import java.util.UUID;

/**
 * Утилита для получения requestId при обработке security-ошибок (401/403).
 */
final class SecurityRequestIdExtractor {

    private static final String MDC_KEY = "requestId";

    private SecurityRequestIdExtractor() {
    }

    static String getOrCreate(HttpServletRequest request, HttpServletResponse response) {
        String requestId = request.getHeader(RequestHeaders.X_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            Object attr = request.getAttribute(RequestHeaders.X_REQUEST_ID);
            if (attr != null) {
                String v = String.valueOf(attr);
                if (!v.isBlank()) {
                    requestId = v;
                }
            }
        }

        if (requestId == null || requestId.isBlank()) {
            String mdc = MDC.get(MDC_KEY);
            if (mdc != null && !mdc.isBlank()) {
                requestId = mdc;
            }
        }

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(RequestHeaders.X_REQUEST_ID, requestId);
        response.setHeader(RequestHeaders.X_REQUEST_ID, requestId);
        MDC.put(MDC_KEY, requestId);

        return requestId;
    }
}
