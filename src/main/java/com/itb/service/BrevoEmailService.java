package com.itb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Entrega e-mails transacionais via HTTPS (porta 443), sem SMTP.
 * Nao registra chave API, token de recuperacao ou conteudo da mensagem nos logs.
 */
@Service
public class BrevoEmailService {
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public BrevoEmailService(@Value("${app.password-reset.brevo-api-key:}") String apiKey,
                             ObjectMapper mapper) {
        this(apiKey, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    // Permite testar respostas da API sem efetuar requisicoes reais.
    BrevoEmailService(String apiKey, ObjectMapper mapper, HttpClient client) {
        this.apiKey = apiKey;
        this.mapper = mapper;
        this.client = client;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean send(String from, String to, String subject, String body) {
        if (!isConfigured()) return false;

        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "sender", Map.of("email", from),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", body));

            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response =
                    client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 201;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException failure) {
            // Nao expor corpo, headers ou chaves nos logs.
            return false;
        }
    }
}
