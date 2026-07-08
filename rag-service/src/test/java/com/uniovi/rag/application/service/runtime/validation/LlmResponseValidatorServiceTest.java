package com.uniovi.rag.application.service.runtime.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseValidatorServiceTest {

    private LlmResponseValidatorService validator;

    @BeforeEach
    void setUp() {
        validator = new LlmResponseValidatorService();
    }

    @Test
    void isAcceptableFinalUserAnswer_rejectsMetadataStub() {
        assertFalse(validator.isAcceptableFinalUserAnswer("Found 6 relevant meeting minutes."));
        assertFalse(validator.isAcceptableFinalUserAnswer("More information"));
    }

    @Test
    void isAcceptableFinalUserAnswer_rejectsPrefixOnlyBased() {
        assertFalse(validator.isAcceptableFinalUserAnswer("Based"));
        assertFalse(validator.isAcceptableFinalUserAnswer("Based\n\nFuentes consultadas: ACTA 1.pdf."));
    }

    @Test
    void isAcceptableFinalUserAnswer_acceptsSubstantiveAnswer() {
        assertTrue(
                validator.isAcceptableFinalUserAnswer(
                        "Found 2 relevant meeting minutes: ACTA_1.pdf and ACTA_2.pdf mention the topic."));
    }

    @Test
    void isValidResponse_null_returnsFalse() {
        assertFalse(validator.isValidResponse(null, "ctx"));
    }

    @Test
    void isValidResponse_empty_returnsFalse() {
        assertFalse(validator.isValidResponse("", "ctx"));
        assertFalse(validator.isValidResponse("   ", "ctx"));
    }

    @Test
    void isValidResponse_tooShort_returnsFalse() {
        assertFalse(validator.isValidResponse("x", "ctx"));
    }

    @Test
    void isValidResponse_valid_returnsTrue() {
        assertTrue(validator.isValidResponse("Valid response here.", "ctx"));
    }

    @Test
    void isValidResponse_errorLike_returnsFalse() {
        assertFalse(validator.isValidResponse("Error: something failed", "ctx"));
        assertFalse(validator.isValidResponse("exception: at com.xxx", "ctx"));
        assertFalse(validator.isValidResponse("An error occurred", "ctx"));
        assertFalse(validator.isValidResponse("A processing error stopped the pipeline.", "ctx"));
        assertFalse(validator.isValidResponse("Failed to retrieve documents from the store.", "ctx"));
        assertFalse(validator.isValidResponse(
                "java.lang.IllegalStateException thrown at com.example.Foo.bar(Foo.java:12)", "ctx"));
    }

    @Test
    void cleanResponse_null_returnsEmpty() {
        assertEquals("", validator.cleanResponse(null));
    }

    @Test
    void cleanResponse_removesCodeBlocks() {
        String input = "```json\n{\"a\":1}\n```";
        String out = validator.cleanResponse(input);
        assertFalse(out.contains("```"));
    }

    @Test
    void cleanResponse_removesQuotes() {
        assertEquals("hello", validator.cleanResponse("\"hello\""));
        assertEquals("hello", validator.cleanResponse("'hello'"));
    }

    @Test
    void validateAndClean_valid_returnsCleaned() {
        String result = validator.validateAndClean("  Good answer  ", "ctx");
        assertNotNull(result);
        assertEquals("Good answer", result);
    }

    @Test
    void validateAndClean_invalid_returnsNull() {
        assertNull(validator.validateAndClean(null, "ctx"));
        assertNull(validator.validateAndClean("x", "ctx"));
    }

    @Test
    void isValidResponse_tooLong_returnsFalse() {
        String longText = "a".repeat(10001);
        assertFalse(validator.isValidResponse(longText, "ctx"));
    }

    @Test
    void isValidResponse_spanishNoErrorPhrase_notTreatedAsError() {
        assertTrue(validator.isValidResponse("No hay error en el documento revisado.", "ctx"));
    }

    @Test
    void cleanResponse_stripsLineComments() {
        String in = "answer\n// comment\nmore";
        String out = validator.cleanResponse(in);
        assertFalse(out.contains("//"));
    }

    @Test
    void cleanResponse_stripsHashComments() {
        String in = "visible\n# hash comment\nend";
        String out = validator.cleanResponse(in);
        assertFalse(out.contains("#"));
        assertTrue(out.contains("visible"));
    }

    @Test
    void validateAndClean_whenCleaningEmpties_returnsNull() {
        String onlyCode = "```\n```";
        assertNull(validator.validateAndClean(onlyCode, "ctx"));
    }
}
