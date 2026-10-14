package com.itb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.nio.charset.StandardCharsets;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RequestRateLimitFilterTest {
    private MutableClock clock;
    private RequestRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        filter = new RequestRateLimitFilter(clock);
    }

    @Test
    void permitsConfiguredNumberOfAttemptsAndRejectsNext() {
        for (int attempt = 0; attempt < 20; attempt++) {
            assertTrue(filter.allow("192.0.2.1:/api/auth/login", 20));
        }
        assertFalse(filter.allow("192.0.2.1:/api/auth/login", 20));
    }

    @Test
    void windowResetsAfterOneMinuteWithoutSleeping() {
        assertTrue(filter.allow("test-key", 1));
        assertFalse(filter.allow("test-key", 1));
        clock.advance(Duration.ofMillis(59999));
        assertFalse(filter.allow("test-key", 1));

        clock.advance(Duration.ofMillis(1));
        assertTrue(filter.allow("test-key", 1));
    }

    @Test
    void addressesAndPathsUseSeparateBuckets() {
        assertTrue(filter.allow("192.0.2.1:/api/auth/login", 1));
        assertFalse(filter.allow("192.0.2.1:/api/auth/login", 1));
        assertTrue(filter.allow("192.0.2.2:/api/auth/login", 1));
        assertTrue(filter.allow("192.0.2.1:/api/push/subscriptions", 1));
    }

    @ParameterizedTest
    @MethodSource("limitedRoutes")
    void limitedPostRoutesReturn429AndRetryAfter(String path, int limit) throws Exception {
        AtomicInteger passed = new AtomicInteger();
        for (int attempt = 0; attempt < limit; attempt++) {
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request("POST", path), response, (req, res) -> passed.incrementAndGet());
            assertEquals(200, response.getStatus());
        }

        var rejected = new MockHttpServletResponse();
        filter.doFilterInternal(request("POST", path), rejected, (req, res) -> passed.incrementAndGet());

        assertEquals(limit, passed.get());
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertTrue(rejected.getContentAsString().contains("Muitas tentativas"));
    }

    @Test
    void oversizedBodyIsRejectedBeforeCallingNextFilter() throws Exception {
        var request = request("POST", "/api/pedidos");
        request.setContent(new byte[262145]);
        var response = new MockHttpServletResponse();
        AtomicInteger passed = new AtomicInteger();

        filter.doFilterInternal(request, response, (req, res) -> passed.incrementAndGet());

        assertEquals(413, response.getStatus());
        assertEquals(0, passed.get());
        assertTrue(response.getContentAsString().contains("Requisição excede"));
    }

    @Test
    void bodyExactlyAtLimitIsAccepted() throws Exception {
        var request = request("POST", "/api/pedidos");
        request.setContent(new byte[262144]);
        var response = new MockHttpServletResponse();
        AtomicInteger passed = new AtomicInteger();

        filter.doFilterInternal(request, response, (req, res) -> passed.incrementAndGet());

        assertEquals(200, response.getStatus());
        assertEquals(1, passed.get());
    }

    @Test
    void readRequestsDoNotConsumeWriteRateBudget() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        for (int attempt = 0; attempt < 25; attempt++) {
            filter.doFilterInternal(request("GET", "/api/auth/login"), new MockHttpServletResponse(),
                    (req, res) -> passed.incrementAndGet());
        }
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request("POST", "/api/auth/login"), response, (req, res) -> passed.incrementAndGet());

        assertEquals(200, response.getStatus());
        assertEquals(26, passed.get());
    }

    @Test
    void unknownLengthBodyCannotBypassActualByteLimit() throws Exception {
        var request = unknownLengthRequest(new byte[262145]);
        var response = new MockHttpServletResponse();
        AtomicInteger passed = new AtomicInteger();

        filter.doFilterInternal(request, response, (req, res) -> passed.incrementAndGet());

        assertEquals(413, response.getStatus());
        assertEquals(0, passed.get());
    }

    @Test
    void unknownLengthUtf8JsonIsPreservedForDownstreamParser() throws Exception {
        byte[] body = "{\"senha\":\"synthetic-á\"}".getBytes(StandardCharsets.UTF_8);
        var request = unknownLengthRequest(body);
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            assertArrayEquals(body, req.getInputStream().readAllBytes());
            assertEquals(body.length, req.getContentLengthLong());
        });

        assertEquals(200, response.getStatus());
    }

    @Test
    void boundedBodyReaderPreservesExplicitEncoding() throws Exception {
        byte[] body = "synthetic-á".getBytes(StandardCharsets.UTF_8);
        var request = unknownLengthRequest(body);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) ->
                assertEquals("synthetic-á", req.getReader().readLine()));
    }

    @Test
    void actualBodyReadStopsAtOneOverflowByte() throws Exception {
        AtomicInteger consumed = new AtomicInteger();
        ServletInputStream stream = new ServletInputStream() {
            @Override public boolean isFinished() { return consumed.get() >= 1_000_000; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            @Override public int read() { return consumed.getAndIncrement() < 1_000_000 ? 'x' : -1; }
            @Override public int read(byte[] bytes, int offset, int length) {
                // InputStream requires zero for an empty read, even at EOF. readNBytes
                // can issue that read while advancing its bounded internal buffers.
                if (length == 0) return 0;
                int count = Math.min(length, 1_000_000 - consumed.get());
                if (count == 0) return -1;
                java.util.Arrays.fill(bytes, offset, offset + count, (byte) 'x');
                consumed.addAndGet(count);
                return count;
            }
        };
        var request = new MockHttpServletRequest("POST", "/api/pedidos") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
            @Override public ServletInputStream getInputStream() { return stream; }
        };
        request.setServletPath("/api/pedidos");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> fail("Oversized body reached the application"));

        assertEquals(413, response.getStatus());
        assertEquals(262145, consumed.get());
    }

    @Test
    void containerDecodedServletPathSharesRateBudgetWithEncodedUri() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            filter.doFilterInternal(request("POST", "/api/auth/login"), new MockHttpServletResponse(), (req, res) -> {});
        }
        var encoded = request("POST", "/api/auth/login");
        encoded.setContextPath("/tablehub");
        encoded.setRequestURI("/tablehub/api/auth/%6cogin");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(encoded, response, (req, res) -> fail("Encoded URL bypassed the rate budget"));

        assertEquals(429, response.getStatus());
    }

    @Test
    void fallbackParsesRawPathSegmentsExactlyOnce() {
        var encoded = request("POST", "/api/auth/%6cogin");
        encoded.setServletPath("");
        assertEquals("/api/auth/login", RequestRateLimitFilter.canonicalPath(encoded));
        encoded.setRequestURI("/api/auth/%256cogin");
        assertEquals("/api/auth/%6cogin", RequestRateLimitFilter.canonicalPath(encoded));
    }

    @Test
    void servletPathIsNeverDecodedAgain() {
        var request = request("POST", "/api/auth/%6cogin");
        request.setRequestURI("/api/auth/%256cogin");
        assertEquals("/api/auth/%6cogin", RequestRateLimitFilter.canonicalPath(request));
    }

    @Test
    void unknownPushPathsCannotExhaustTheGlobalRateBudget() throws Exception {
        for (int attempt = 0; attempt < 10_005; attempt++) {
            filter.doFilterInternal(request("POST", "/api/push/unknown-" + attempt),
                    new MockHttpServletResponse(), (req, res) -> {});
        }
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request("POST", "/api/auth/login"), response, (req, res) -> {});
        assertEquals(200, response.getStatus(), "Unknown routes must not consume another endpoint's rate capacity");
    }

    private MockHttpServletRequest unknownLengthRequest(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/pedidos") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setServletPath("/api/pedidos");
        request.setRemoteAddr("192.0.2.1");
        request.setContent(body);
        return request;
    }

    private static Stream<Arguments> limitedRoutes() {
        return Stream.of(
                Arguments.of("/api/auth/login", 20),
                Arguments.of("/api/mesas/contexto", 20),
                Arguments.of("/api/push/subscriptions", 20),
                Arguments.of("/api/pedidos", 60),
                Arguments.of("/api/atendimentos", 60)
        );
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("192.0.2.1");
        return request;
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T12:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
