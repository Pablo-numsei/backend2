package com.itb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance limits, complemented by network-level abuse protection. */
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_REQUEST_BYTES = 262_144;
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public RequestRateLimitFilter() { this(Clock.systemUTC()); }
    RequestRateLimitFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            reject(response, 413, "Requisição excede o tamanho permitido.");
            return;
        }
        String path;
        try {
            path = canonicalPath(request);
        } catch (IllegalArgumentException malformedPath) {
            reject(response, 400, "Caminho da requisição inválido.");
            return;
        }
        // Unknown paths are denied by authorization and must not allocate arbitrary
        // rate keys that could exhaust the shared capacity for legitimate clients.
        boolean pushSubscription = path.equals("/api/push/subscriptions")
                || path.equals("/api/push/staff-subscriptions");
        boolean resetRequest = path.equals("/api/auth/password-reset/request");
        boolean resetConfirm = path.equals("/api/auth/password-reset/confirm");
        boolean customerSignup = path.equals("/api/clientes/cadastro");
        boolean customerLogin = path.equals("/api/clientes/login");
        int limit = resetRequest ? 5 : resetConfirm ? 10 : customerSignup || customerLogin ? 10 :
                path.equals("/api/auth/login") || path.equals("/api/mesas/contexto")
                || pushSubscription ? 20 : 60;
        boolean limited = "POST".equals(request.getMethod()) &&
                (path.equals("/api/auth/login") || resetRequest || resetConfirm || customerSignup || customerLogin || path.equals("/api/mesas/contexto") || path.equals("/api/pedidos") ||
                 path.equals("/api/atendimentos") || pushSubscription);
        if (limited && !allow(request.getRemoteAddr() + ":" + path, limit)) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "Muitas tentativas. Aguarde um minuto e tente novamente.");
            return;
        }
        if (request.getContentLengthLong() == 0) {
            chain.doFilter(request, response);
            return;
        }
        // Bound the actual bytes before MVC parses JSON, including chunked/unknown lengths.
        // The temporary buffer never exceeds the limit plus one overflow-detection byte.
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            reject(response, 413, "Requisição excede o tamanho permitido.");
            return;
        }
        chain.doFilter(new BoundedBodyRequest(request, body), response);
    }

    static String canonicalPath(HttpServletRequest request) {
        // The container has already decoded servletPath/pathInfo; SecurityFilterChain's
        // StrictHttpFirewall checks them. Decoding them again would change their meaning.
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath + (request.getPathInfo() == null ? "" : request.getPathInfo());
        }
        // MockMvc and some servlet mappings expose an empty servletPath. Match MVC's
        // segment parsing of the raw request URI, decoding each segment exactly once.
        StringBuilder path = new StringBuilder();
        for (PathContainer.Element element : RequestPath.parse(request.getRequestURI(),
                request.getContextPath()).pathWithinApplication().elements()) {
            path.append(element instanceof PathContainer.PathSegment segment
                    ? segment.valueToMatch() : element.value());
        }
        return path.toString();
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

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private ServletInputStream stream;
        private BufferedReader reader;

        BoundedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }

        @Override
        public ServletInputStream getInputStream() {
            if (reader != null) throw new IllegalStateException("O leitor da requisição já foi aberto.");
            if (stream == null) stream = new BufferedServletInputStream(body);
            return stream;
        }

        @Override
        public BufferedReader getReader() {
            if (reader != null) return reader;
            if (stream != null) throw new IllegalStateException("O fluxo da requisição já foi aberto.");
            Charset charset = getCharacterEncoding() == null ? StandardCharsets.ISO_8859_1
                    : Charset.forName(getCharacterEncoding());
            stream = new BufferedServletInputStream(body);
            reader = new BufferedReader(new InputStreamReader(stream, charset));
            return reader;
        }
    }

    private static final class BufferedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;
        private ReadListener listener;
        private boolean allReadNotified;

        BufferedServletInputStream(byte[] body) { input = new ByteArrayInputStream(body); }
        @Override public boolean isFinished() { return input.available() == 0; }
        @Override public boolean isReady() { return true; }

        @Override
        public int read() throws IOException {
            int value = input.read();
            notifyFinished();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = input.read(bytes, offset, length);
            notifyFinished();
            return count;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            if (listener == null) throw new IllegalArgumentException("O listener é obrigatório.");
            if (this.listener != null) throw new IllegalStateException("O listener já foi registrado.");
            this.listener = listener;
            try {
                if (!isFinished()) listener.onDataAvailable();
                notifyFinished();
            } catch (IOException ex) {
                listener.onError(ex);
            }
        }

        private void notifyFinished() throws IOException {
            if (listener != null && isFinished() && !allReadNotified) {
                allReadNotified = true;
                listener.onAllDataRead();
            }
        }
    }
}
