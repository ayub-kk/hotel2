package ru.mifi.booking.hotelservice.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mifi.booking.common.http.RequestHeaders;

/**
 * Фильтр для работы с X-Request-Id в hotel-service.
 *
 * <p>
 * Я делаю сервис устойчивым к ситуации, когда кто-то обратился напрямую минуя Gateway.
 * Поэтому:
 * - если X-Request-Id не пришёл, я генерирую его сам,
 * - возвращаю его в ответе,
 * - кладу в MDC для логов.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(RequestHeaders.X_REQUEST_ID);

        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(RequestHeaders.X_REQUEST_ID, requestId); // <-- важная строка
        response.setHeader(RequestHeaders.X_REQUEST_ID, requestId);

        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
