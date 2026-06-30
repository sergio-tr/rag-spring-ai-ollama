package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uniovi.rag.application.port.llm.LlmChatMessage;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmChatRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleChatMapperTest {

    @Test
    void toApiRequest_mapsModelMessagesAndTemperature() {
        LlmChatRequest request =
                LlmChatRequest.of("gpt-oss:20b", "responde siempre en español", "Responde solo con OK", 0.1, 5_000, Map.of());

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertEquals("gpt-oss:20b", apiRequest.model());
        assertEquals(0.1, apiRequest.temperature());
        assertEquals(2, apiRequest.messages().size());
        assertEquals("system", apiRequest.messages().get(0).role());
        assertEquals("responde siempre en español", apiRequest.messages().get(0).content());
        assertEquals("user", apiRequest.messages().get(1).role());
        assertEquals("Responde solo con OK", apiRequest.messages().get(1).content());
    }

    @Test
    void toApiRequest_mapsSupportedAdditionalParameters() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "gpt-oss:20b",
                        "sys",
                        "user",
                        0.0,
                        null,
                        Map.of(
                                "top_p", 0.85,
                                "maxTokens", 256,
                                "stop", List.of("END"),
                                "presencePenalty", -0.1,
                                "frequencyPenalty", 0.2,
                                "seed", 42,
                                "responseFormat", Map.of("type", "json_object")));

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertEquals(0.85, apiRequest.topP());
        assertEquals(256, apiRequest.maxTokens());
        assertEquals(List.of("END"), apiRequest.stop());
        assertEquals(null, apiRequest.presencePenalty());
        assertEquals(null, apiRequest.frequencyPenalty());
        assertEquals(42, apiRequest.seed());
        assertInstanceOf(Map.class, apiRequest.responseFormat());
        assertEquals("json_object", ((Map<?, ?>) apiRequest.responseFormat()).get("type"));
    }

    @Test
    void toApiRequest_ignoresUnsupportedResponseFormatString() {
        LlmChatRequest request =
                LlmChatRequest.of("m", "s", "u", null, null, Map.of("responseFormat", "json_object"));

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertEquals(null, apiRequest.responseFormat());
    }

    @Test
    void toApiRequest_omitsOllamaOnlyParameters() {
        LlmChatRequest request =
                LlmChatRequest.of("m", "s", "u", null, null, Map.of("topK", 40, "numCtx", 8192, "repeatPenalty", 1.1));

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertEquals(null, apiRequest.topP());
        assertEquals(null, apiRequest.maxTokens());
        assertEquals(null, apiRequest.seed());
    }

    @Test
    void toApiRequest_omitsSystemWhenBlank() {
        LlmChatRequest request =
                new LlmChatRequest(
                        "gpt-oss:20b",
                        List.of(LlmChatMessage.user("hola")),
                        null,
                        null,
                        Map.of());

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertEquals(1, apiRequest.messages().size());
        assertEquals("user", apiRequest.messages().getFirst().role());
    }

    @Test
    void toPortResponse_parsesAssistantContentUsageAndFinishReason() {
        OpenAiChatCompletionResponse apiResponse =
                new OpenAiChatCompletionResponse(
                        List.of(
                                new OpenAiChatChoiceDto(
                                        "stop",
                                        new OpenAiChatChoiceMessageDto("assistant", "OK"))),
                        new OpenAiUsageDto(76, 38, 114),
                        "gpt-oss:20b");

        LlmChatResponse response = OpenAiCompatibleChatMapper.toPortResponse(apiResponse, "gpt-oss:20b");

        assertEquals("OK", response.content());
        assertEquals("gpt-oss:20b", response.model());
        assertEquals("stop", response.finishReason());
        assertEquals(76, response.usage().promptTokens());
        assertEquals(38, response.usage().completionTokens());
        assertEquals(114, response.usage().totalTokens());
    }

    @Test
    void toPortResponse_emptyChoicesThrowsInvalidResponse() {
        OpenAiChatCompletionResponse apiResponse =
                new OpenAiChatCompletionResponse(List.of(), null, "gpt-oss:20b");

        OpenAiCompatibleLlmException ex =
                assertThrows(
                        OpenAiCompatibleLlmException.class,
                        () -> OpenAiCompatibleChatMapper.toPortResponse(apiResponse, "gpt-oss:20b"));
        assertEquals(OpenAiCompatibleLlmFailureKind.INVALID_RESPONSE, ex.kind());
    }

    @Test
    void mapHttpError_unauthorized401() {
        OpenAiCompatibleLlmException ex =
                OpenAiCompatibleChatMapper.mapHttpError(401, "{\"error\":\"invalid key\"}", "http://x/v1/chat/completions");
        assertEquals(OpenAiCompatibleLlmFailureKind.UNAUTHORIZED, ex.kind());
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void mapHttpError_forbidden403() {
        OpenAiCompatibleLlmException ex =
                OpenAiCompatibleChatMapper.mapHttpError(403, "forbidden", "http://x/v1/chat/completions");
        assertEquals(OpenAiCompatibleLlmFailureKind.UNAUTHORIZED, ex.kind());
        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    void mapHttpError_modelRejectedOn400() {
        OpenAiCompatibleLlmException ex =
                OpenAiCompatibleChatMapper.mapHttpError(
                        400, "{\"error\":{\"message\":\"model not found\"}}", "http://x/v1/chat/completions");
        assertEquals(OpenAiCompatibleLlmFailureKind.INVALID_MODEL, ex.kind());
    }

    @Test
    void toApiRole_mapsAllRoles() {
        LlmChatRequest request =
                new LlmChatRequest(
                        "m",
                        List.of(
                                LlmChatMessage.system("s"),
                                LlmChatMessage.user("u"),
                                LlmChatMessage.assistant("a"),
                                new LlmChatMessage(LlmChatRole.TOOL, "t")),
                        null,
                        null,
                        Map.of());
        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);
        assertEquals(List.of("system", "user", "assistant", "tool"), apiRequest.messages().stream().map(OpenAiChatMessageDto::role).toList());
    }
}
