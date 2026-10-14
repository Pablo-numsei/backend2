package com.itb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

    private static Stream<Arguments> limitedRoutes() {
        return Stream.of(
                Arguments.of("/api/auth/login", 20),
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
