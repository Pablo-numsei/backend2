package com.itb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance limits, complemented by network-level abuse protection. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public RequestRateLimitFilter() { this(Clock.systemUTC()); }
    RequestRateLimitFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > 262_144) {
            reject(response, 413, "Requisição excede o tamanho permitido.");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        int limit = path.equals("/api/auth/login") ? 20 : path.startsWith("/api/push/") ? 20 : 60;
        boolean limited = "POST".equals(request.getMethod()) &&
                (path.equals("/api/auth/login") || path.equals("/api/pedidos") ||
                 path.equals("/api/atendimentos") || path.startsWith("/api/push/"));
        if (limited && !allow(request.getRemoteAddr() + ":" + path, limit)) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "Muitas tentativas. Aguarde um minuto e tente novamente.");
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String key, int limit) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || now - window.startedAt >= WINDOW_MS) {
            if (windows.size() >= MAX_KEYS) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MS);
                if (!windows.containsKey(key) && windows.size() >= MAX_KEYS) return false;
            }
            window = new Window(now);
            windows.put(key, window);
        }
        return ++window.requests <= limit;
    }
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
    private static final class Window {
        final long startedAt;
        int requests;
        Window(long startedAt) { this.startedAt = startedAt; }
    }
}
