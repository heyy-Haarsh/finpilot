package com.finpilot.insights.client;

import com.finpilot.insights.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatClientTest {

    private static final String URL = "http://llm.test/v1/chat/completions";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiCompatibleChatClient client(String apiKey) {
        return new OpenAiCompatibleChatClient(builder.build(), "http://llm.test/v1", "test-model", apiKey);
    }

    private static String reply(String content) {
        return """
                {"id":"x","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}]}
                """.formatted(content);
    }

    @Test
    void sendsSystemAndUserMessagesWithBearerTokenAndReturnsContent() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("system rules"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("portfolio facts"))
                .andRespond(withSuccess(reply("Overview: looks fine."), MediaType.APPLICATION_JSON));

        String content = client("secret-key").complete("system rules", "portfolio facts");

        assertThat(content).isEqualTo("Overview: looks fine.");
        server.verify();
    }

    @Test
    void omitsAuthorizationHeaderWhenNoKeyConfigured() {
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(reply("ok"), MediaType.APPLICATION_JSON));

        assertThat(client("").complete("s", "u")).isEqualTo("ok");
    }

    @Test
    void stripsReasoningBlockFromThinkingModels() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(reply("<think>internal reasoning</think>\\nOverview: fine."), MediaType.APPLICATION_JSON));

        assertThat(client("").complete("s", "u")).isEqualTo("Overview: fine.");
    }

    @Test
    void providerErrorBecomesAiServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client("k").complete("s", "u"))
                .isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void emptyChoicesBecomeAiServiceUnavailable() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("k").complete("s", "u"))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI provider returned an empty response");
    }
}
