package com.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 1024;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;

    public AgentService(@Value("${anthropic.api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @SuppressWarnings("unchecked")
    public String chat(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // POST to https://api.anthropic.com/v1/messages and parse the response
        Map<String, Object> response = restTemplate.postForObject(ANTHROPIC_URL, request, Map.class);

        // Response shape: { "content": [{ "type": "text", "text": "..." }], ... }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }
}
