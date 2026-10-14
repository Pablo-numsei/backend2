package com.itb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.controller.AuthController;
import com.itb.dto.LoginRequest;
import com.itb.dto.LoginResponse;
import com.itb.exception.GlobalExceptionHandler;
import com.itb.service.AuthService;
import com.itb.service.CustomUserDetailsService;
import com.itb.service.JwtService;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(RequestBoundaryHttpTest.Fixtures.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000", "jwt.order-expiration=60000"
})
class RequestBoundaryHttpTest {
    private static final String BODY = "{\"email\":\"fixture@example.test\",\"senha\":\"synthetic-á\"}";
    @Autowired private WebApplicationContext context;
    @Autowired private AuthService auth;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
            AuthController.class, GlobalExceptionHandler.class, CorsConfiguration.class})
    static class Fixtures {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean AuthService auth() { return mock(AuthService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(auth);
        when(auth.login(any())).thenReturn(new LoginResponse(1L, "Synthetic fixture",
                "fixture@example.test", 1L, "ADMINISTRADOR", "synthetic-result"));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void encodedAndCanonicalLoginPathsConsumeTheSameBudget() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            String route = attempt % 2 == 0 ? "/api/auth/login" : "/api/auth/%6cogin";
            mvc.perform(post(URI.create(route)).contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .with(request -> { request.setRemoteAddr("192.0.2.20"); return request; }))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(URI.create("/api/auth/%6cogin")).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(request -> { request.setRemoteAddr("192.0.2.20"); return request; }))
                .andExpect(status().isTooManyRequests());
        verify(auth, times(20)).login(any());
    }

    @Test
    void oversizedUnknownLengthBodyGets413BeforeMvcOrAuthenticationService() throws Exception {
        mvc.perform(unknownLength(" ".repeat(280000) + BODY, "192.0.2.21"))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(auth);
    }

    @Test
    void validUnknownLengthUtf8JsonStillAuthenticates() throws Exception {
        mvc.perform(unknownLength(BODY, "192.0.2.22")).andExpect(status().isOk());
        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(auth).login(request.capture());
        assertEquals("synthetic-á", request.getValue().senha());
    }

    @Test
    void unknownLengthJsonExactlyAtByteLimitIsAccepted() throws Exception {
        String body = " ".repeat(262144 - BODY.getBytes(StandardCharsets.UTF_8).length) + BODY;
        mvc.perform(unknownLength(body, "192.0.2.23")).andExpect(status().isOk());
        verify(auth).login(any());
    }

    private RequestBuilder unknownLength(String body, String address) {
        return servletContext -> {
            MockHttpServletRequest request = new UnknownLengthRequest(servletContext);
            request.setMethod("POST");
            request.setRequestURI("/api/auth/login");
            request.setServletPath("/api/auth/login");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setRemoteAddr(address);
            request.addHeader("Transfer-Encoding", "chunked");
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            return request;
        };
    }

    private static final class UnknownLengthRequest extends MockHttpServletRequest {
        UnknownLengthRequest(ServletContext context) { super(context); }
        @Override public int getContentLength() { return -1; }
        @Override public long getContentLengthLong() { return -1; }
    }
}
