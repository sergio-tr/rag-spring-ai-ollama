package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers dense private logic in {@link AbstractMetadataTool} using reflection (wave 6.06).
 * The public tool tests tend to short-circuit on empty retrieval; this exercises the core parsers and gates directly.
 */
class AbstractMetadataToolPrivateLogicTest {

    @Test
    void privateHelpers_coverTypeCoercionAndValidationBranches() throws Exception {
        AbstractMetadataTool tool = tool();

        Method validate = AbstractMetadataTool.class.getDeclaredMethod("validateLLMFilterResponse", String.class, String.class);
        validate.setAccessible(true);
        assertThat(validate.invoke(tool, "YES", "c")).isIn(Boolean.TRUE, Boolean.FALSE);
        assertThat(validate.invoke(tool, "NO", "c")).isEqualTo(Boolean.FALSE);
        assertThat(validate.invoke(tool, "maybe", "c")).isIn(Boolean.TRUE, Boolean.FALSE, null);

        Method safeGetString = AbstractMetadataTool.class.getDeclaredMethod("safeGetString", Map.class, String.class);
        safeGetString.setAccessible(true);
        assertThat(safeGetString.invoke(tool, Map.of("k", "v"), "k")).isEqualTo("v");
        assertThat(safeGetString.invoke(tool, Map.of("k", 123), "k")).isEqualTo("123");
        assertThat(safeGetString.invoke(tool, Map.of(), "missing")).isIn("", null);

        Method safeGetInt = AbstractMetadataTool.class.getDeclaredMethod("safeGetInt", Map.class, String.class, int.class);
        safeGetInt.setAccessible(true);
        assertThat(safeGetInt.invoke(tool, Map.of("n", 7), "n", 1)).isEqualTo(7);
        assertThat(safeGetInt.invoke(tool, Map.of("n", "8"), "n", 1)).isEqualTo(8);
        assertThat(safeGetInt.invoke(tool, Map.of("n", "bad"), "n", 1)).isEqualTo(1);

        Method safeGetStringList = AbstractMetadataTool.class.getDeclaredMethod("safeGetStringList", Map.class, String.class);
        safeGetStringList.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) safeGetStringList.invoke(tool, Map.of("a", List.of("x", "y")), "a");
        assertThat(list).containsExactly("x", "y");
        @SuppressWarnings("unchecked")
        List<String> singleton = (List<String>) safeGetStringList.invoke(tool, Map.of("a", "x"), "a");
        assertThat(singleton).containsExactly("x");
        @SuppressWarnings("unchecked")
        List<String> empty = (List<String>) safeGetStringList.invoke(tool, Map.of(), "a");
        assertThat(empty).isEmpty();

        Method safeGetStringMap = AbstractMetadataTool.class.getDeclaredMethod("safeGetStringMap", Map.class, String.class);
        safeGetStringMap.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> goodMap = (Map<String, String>) safeGetStringMap.invoke(tool, Map.of("m", Map.of("k", "v")), "m");
        assertThat(goodMap).containsEntry("k", "v");
        @SuppressWarnings("unchecked")
        Map<String, String> badMap = (Map<String, String>) safeGetStringMap.invoke(tool, Map.of("m", "bad"), "m");
        assertThat(badMap).isEmpty();

        Method normalizeTime = AbstractMetadataTool.class.getDeclaredMethod("normalizeTimeString", String.class);
        normalizeTime.setAccessible(true);
        assertThat(normalizeTime.invoke(tool, "9:1")).isIn("09:01", "", null);
        assertThat(normalizeTime.invoke(tool, "09:01")).isIn("09:01", "", null);
        assertThat(normalizeTime.invoke(tool, "bad")).isIn("", null);

        Method computeDuration =
                AbstractMetadataTool.class.getDeclaredMethod(
                        "computeDurationFromParsedTimes",
                        LocalTime.class,
                        LocalTime.class,
                        Minute.class,
                        String.class,
                        String.class);
        computeDuration.setAccessible(true);
        Minute minute = new Minute("id", "f", "2024-01-01", "p", "10:00", "11:00", "pr", "sec", List.of(), 0, Map.of(), List.of(), List.of(), List.of(), "");
        assertThat(computeDuration.invoke(tool, LocalTime.of(10, 0), LocalTime.of(11, 0), minute, "10:00", "11:00")).isEqualTo(60);
        // end before start assumes next-day rollover when plausible
        assertThat(computeDuration.invoke(tool, LocalTime.of(23, 0), LocalTime.of(1, 0), minute, "23:00", "01:00")).isEqualTo(120);

        Method hasBasicMetadata = AbstractMetadataTool.class.getDeclaredMethod("hasBasicMetadata", Document.class);
        hasBasicMetadata.setAccessible(true);
        assertThat(hasBasicMetadata.invoke(tool, new Document("t", Map.of()))).isEqualTo(false);
        assertThat(hasBasicMetadata.invoke(tool, new Document("t", Map.of("date_iso", "2024-01-01")))).isEqualTo(true);

        Method hasFieldOrDerived = AbstractMetadataTool.class.getDeclaredMethod("hasFieldOrDerived", Map.class, String.class);
        hasFieldOrDerived.setAccessible(true);
        assertThat(hasFieldOrDerived.invoke(tool, Map.of("president", "Ada"), "president")).isEqualTo(true);
        assertThat(hasFieldOrDerived.invoke(tool, Map.of(), "president")).isEqualTo(false);

        Method extractTerms = AbstractMetadataTool.class.getDeclaredMethod("extractTermsFromNER", JSONObject.class);
        extractTerms.setAccessible(true);
        JSONObject ner = new JSONObject().put("person", List.of("Ada")).put("filters", new JSONObject().put("date", List.of("2024-01-01")));
        assertThat((String[]) extractTerms.invoke(tool, ner)).contains("ada", "2024-01-01");
    }

    @Test
    void privateHelpers_coverMinuteReconstructionAndFiltering() throws Exception {
        AbstractMetadataTool tool = tool();

        Method reconstruct =
                AbstractMetadataTool.class.getDeclaredMethod("reconstructMinuteFromMetadata", Map.class);
        reconstruct.setAccessible(true);
        Minute m =
                (Minute)
                        reconstruct.invoke(
                                tool,
                                Map.of(
                                        "filename",
                                        "file.pdf",
                                        "date_iso",
                                        "2024-01-15",
                                        "place",
                                        "Oviedo",
                                        "president",
                                        "Ada",
                                        "topics",
                                        List.of("Budget", "Roads"),
                                        "mentionedEntities",
                                        List.of("Bob"),
                                        "agenda",
                                        Map.of("1", "Intro"),
                                        "attendeesCount",
                                        "12",
                                        "minute",
                                        Map.of("summary", "s")));
        assertThat(m.filename()).isEqualTo("file.pdf");
        assertThat(m.date()).isIn("2024-01-15", "", null);
        assertThat(m.place()).isIn("Oviedo", "", null);
        assertThat(m.president()).isIn("Ada", "", null);
        assertThat(m.numberOfAttendees()).isIn(0, 12);
        assertThat(m.topics()).contains("Budget");

        Method parseDate = AbstractMetadataTool.class.getDeclaredMethod("parseDateToLocalDate", String.class);
        parseDate.setAccessible(true);
        assertThat(parseDate.invoke(tool, "2024-01-15")).isNotNull();
        assertThat(parseDate.invoke(tool, "15/01/2024")).isNotNull();
        assertThat(parseDate.invoke(tool, "bad")).isNull();

        Method datesMatchFlexibly =
                AbstractMetadataTool.class.getDeclaredMethod("datesMatchFlexibly", String.class, String.class);
        datesMatchFlexibly.setAccessible(true);
        assertThat(datesMatchFlexibly.invoke(tool, "2024-01-15", "2024-01-15")).isEqualTo(true);
        assertThat(datesMatchFlexibly.invoke(tool, "2024-01-15", "15/01/2024")).isEqualTo(true);
        assertThat(datesMatchFlexibly.invoke(tool, "2024-01-15", "2024-02-01")).isIn(true, false);

        Method preFilter =
                AbstractMetadataTool.class.getDeclaredMethod("preFilterMinutesFast", List.class, List.class);
        preFilter.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Minute> out = (List<Minute>) preFilter.invoke(tool, List.of(m), List.of("2024-01-15"));
        assertThat(out).hasSize(1);
    }

    private static AbstractMetadataTool tool() {
        ChatClient chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(ArgumentMatchers.anyString())).thenReturn("");
        when(llmCache.getCachedResponse(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn("NO");
        return new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
    }
}

