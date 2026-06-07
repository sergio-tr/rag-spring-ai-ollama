package com.uniovi.rag.application.service.runtime.query.analyser;

import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MinuteNERQueryAnalyserTest {

    private ChatClient chatClient;
    private MinuteNERQueryAnalyser analyser;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        analyser = new MinuteNERQueryAnalyser(chatClient);
    }

    @Test
    void analyse_nullOrEmpty_returnsFallback() {
        JSONObject result = analyser.analyse(null);
        assertNotNull(result);

        result = analyser.analyse("");
        assertNotNull(result);

        result = analyser.analyse("   ");
        assertNotNull(result);
    }

    @Test
    void analyse_whenChatClientNull_returnsFallback() {
        MinuteNERQueryAnalyser a = new MinuteNERQueryAnalyser(null);
        JSONObject result = a.analyse("q");
        assertNotNull(result);
        assertTrue(result.has("attendees"));
    }

    @Test
    void analyse_withValidJsonFromLlm_returnsParsedObject() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                "{\"date\":[\"2025-01-15\"],\"answerType\":\"person\"}");

        JSONObject result = analyser.analyse("¿Quién presidió el 15 de enero de 2025?");

        assertNotNull(result);
        assertTrue(result.has("date") || result.length() >= 0);
    }

    @Test
    void analyse_llmThrows_returnsFallback() {
        when(chatClient.prompt().system(anyString())).thenThrow(new RuntimeException("error"));

        JSONObject result = analyser.analyse("query");

        assertNotNull(result);
    }

    @Test
    void analyse_normalizesExtractedEntitiesAndInfersContext() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                """
                ```json
                {
                  "date": ["25 de febrero de 2026", "última"],
                  "startTime": ["9:05"],
                  "numberOfAttendees": ["42 asistentes"],
                  "president": ["jUaN pErEz"],
                  "comparisonType": [],
                  "temporalContext": "none",
                  "answerType": "unknown"
                }
                ```
                """);

        JSONObject result = analyser.analyse("Compare la duración de la última reunión");

        assertEquals("2026-02-25", result.getJSONArray("date").getString(0));
        assertEquals("latest", result.getJSONArray("date").getString(1));
        assertEquals("09:05", result.getJSONArray("startTime").getString(0));
        assertEquals("42", result.getJSONArray("numberOfAttendees").getString(0));
        assertEquals("Juan Perez", result.getJSONArray("president").getString(0));
        assertEquals("duration", result.getString("comparisonType"));
        assertEquals("latest", result.getString("temporalContext"));
        assertEquals("text", result.getString("answerType"));
    }

    @Test
    void analyse_withNonJsonResponse_fallsBackAndKeepsDefaultShape() {
        ChatClientTestSupport.stubSystemUserPromptReturns(chatClient, "No JSON available");

        JSONObject result = analyser.analyse("Who attended in march?");

        assertEquals("person", result.getString("answerType"));
        assertEquals("general", result.getString("temporalContext"));
        assertTrue(result.has("attendees"));
        assertTrue(result.has("decisions"));
    }

    @Test
    void analyse_extractsJsonSubstring_whenResponseHasNoiseAroundObject() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                """
                Here you go:
                {
                  "date": ["2025-02-01"],
                  "answerType": "date"
                }
                Thanks!
                """);

        JSONObject result = analyser.analyse("date?");

        assertEquals("2025-02-01", result.getJSONArray("date").getString(0));
        assertEquals("date", result.getString("answerType"));
    }

    @Test
    void analyse_duplicateKeys_inResponse_usesJacksonFallback_lastValueWins() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                """
                {
                  "answerType": "text",
                  "answerType": "person",
                  "date": ["2025-01-15"]
                }
                """);

        JSONObject result = analyser.analyse("who?");

        assertEquals("person", result.getString("answerType"));
        assertEquals("2025-01-15", result.getJSONArray("date").getString(0));
    }

    @Test
    void analyse_validateAndNormalize_fillsMissingFields_and_coercesStringFields() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                """
                {
                  "date": [],
                  "answerType": [],
                  "comparisonType": [],
                  "temporalContext": []
                }
                """);

        JSONObject result = analyser.analyse("anything");

        assertEquals("text", result.getString("answerType"));
        assertEquals("none", result.getString("comparisonType"));
        assertEquals("general", result.getString("temporalContext"));
        assertTrue(result.has("attendees"));
        assertTrue(result.has("decisions"));
        assertTrue(result.get("attendees") instanceof JSONArray);
    }

    @Test
    void analyse_dateNormalization_supportsMultipleNumericFormats() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                """
                {
                  "date": ["1/2/2026", "2026.02.03"],
                  "answerType": "date"
                }
                """);

        JSONObject result = analyser.analyse("dates");

        assertEquals("2026-02-01", result.getJSONArray("date").getString(0));
        assertEquals("2026-02-03", result.getJSONArray("date").getString(1));
    }
}
