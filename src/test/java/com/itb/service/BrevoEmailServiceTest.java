package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrevoEmailServiceTest {

    @Test
    void missingApiKeyDoesNotCallNetwork() {
        HttpClient client = mock(HttpClient.class);
        BrevoEmailService sender = new BrevoEmailService("", new ObjectMapper(), client);

        assertFalse(sender.isConfigured());
        assertFalse(sender.send("sender@example.test", "user@example.test", "subject", "body"));
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsToFixedHttpsEndpointWithApiKey() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        BrevoEmailService sender = new BrevoEmailService("test-only-key", new ObjectMapper(), client);

        assertTrue(sender.send("sender@example.test", "user@example.test", "subject", "body"));

        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.brevo.com/v3/smtp/email", request.getValue().uri().toString());
        assertEquals("POST", request.getValue().method());
        assertEquals("test-only-key", request.getValue().headers().firstValue("api-key").orElse(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNonSuccessfulApiResponse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        BrevoEmailService sender = new BrevoEmailService("test-only-key", new ObjectMapper(), client);

        assertFalse(sender.send("sender@example.test", "user@example.test", "subject", "body"));
    }
}
