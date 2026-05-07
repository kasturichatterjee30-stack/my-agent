package com.agent.service;

import com.agent.tool.ObsidianTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AgentService {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 4096;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;
    private final ObsidianTool obsidianTool;

    public AgentService(@Value("${anthropic.api.key}") String apiKey, ObsidianTool obsidianTool) {
        this.apiKey = apiKey;
        this.obsidianTool = obsidianTool;
    }

    @SuppressWarnings("unchecked")
    public String chat(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> tools = List.of(buildObsidianToolSchema());

        // Agentic loop: keep going until Claude stops calling tools
        while (true) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("max_tokens", MAX_TOKENS);
            body.put("tools", tools);
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(ANTHROPIC_URL, request, Map.class);

            String stopReason = (String) response.get("stop_reason");
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

            if ("end_turn".equals(stopReason)) {
                return content.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .findFirst()
                        .orElse("");
            }

            if ("tool_use".equals(stopReason)) {
                // Append assistant turn
                messages.add(Map.of("role", "assistant", "content", content));

                // Execute each tool call and collect results
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (Map<String, Object> block : content) {
                    if ("tool_use".equals(block.get("type"))) {
                        String toolUseId = (String) block.get("id");
                        Map<String, Object> input = (Map<String, Object>) block.get("input");
                        String operation = (String) input.get("operation");
                        String result = obsidianTool.execute(operation, input);
                        toolResults.add(Map.of(
                                "type", "tool_result",
                                "tool_use_id", toolUseId,
                                "content", result
                        ));
                    }
                }

                messages.add(Map.of("role", "user", "content", toolResults));
            } else {
                break;
            }
        }

        return "Unexpected agent loop exit.";
    }

    private Map<String, Object> buildObsidianToolSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", Map.of(
                "type", "string",
                "enum", List.of("list_notes", "read_note", "search_notes", "get_daily_note", "get_recent_notes"),
                "description", "The operation to perform"
        ));
        properties.put("folder", Map.of(
                "type", "string",
                "description", "Vault folder to list (for list_notes). E.g. '00 Inbox', '04 Projects'"
        ));
        properties.put("path", Map.of(
                "type", "string",
                "description", "Relative path to the note (for read_note). E.g. '01 Daily/2026-05-07.md'"
        ));
        properties.put("keyword", Map.of(
                "type", "string",
                "description", "Keyword to search for across all notes (for search_notes)"
        ));

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("operation"));

        return Map.of(
                "name", "obsidian",
                "description", "Read notes from the user's Obsidian vault (LifeOS). Use this to access their inbox, daily notes, weekly reviews, projects, areas, and knowledge base.",
                "input_schema", inputSchema
        );
    }
}
