package com.finpilot.insights.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.finpilot.insights.exception.AiServiceUnavailableException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal client for the OpenAI-compatible Chat Completions API.
 * Works with OpenAI, Groq and Ollama by changing only base URL, model and API key.
 */
@Component
public class OpenAiCompatibleChatClient {

    // Reasoning models (e.g. qwen3) prepend their chain of thought in <think> tags.
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private final RestClient restClient;
    private final String baseUrl;
    @Getter
    private final String model;
    private final String apiKey;

    @Autowired
    public OpenAiCompatibleChatClient(
            RestClient.Builder builder,
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.model}") String model,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.timeout:60s}") Duration timeout) {

        this(builder.requestFactory(requestFactory(timeout)).build(), baseUrl, model, apiKey);
    }

    OpenAiCompatibleChatClient(RestClient restClient, String baseUrl, String model, String apiKey) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    public String complete(String systemPrompt, String userPrompt) {

        ChatRequest request = new ChatRequest(
                model,
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                0.3
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (apiKey != null && !apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException("AI provider request failed", ex);
        }

        String content = response == null || response.choices() == null || response.choices().isEmpty()
                ? null
                : response.choices().get(0).message().content();

        if (content == null || content.isBlank()) {
            throw new AiServiceUnavailableException("AI provider returned an empty response");
        }

        return THINK_BLOCK.matcher(content).replaceAll("").trim();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(timeout);
        return factory;
    }

    public record ChatRequest(String model, List<Message> messages, double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }
}
