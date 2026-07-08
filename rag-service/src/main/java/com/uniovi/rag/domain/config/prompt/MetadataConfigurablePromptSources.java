package com.uniovi.rag.domain.config.prompt;

/** Canonical metadata-tool prompt skeletons for the configurable prompt catalog. */
public final class MetadataConfigurablePromptSources {

    public static final String FILTER_AND_LIST =
            """
            You are a metadata matching system. Analyze if document metadata semantically matches a user query.

            User query (may be in any language):
            "%s"

            Document metadata (values may be in any language):
            %s

            Task: Determine if this document's metadata semantically matches the intent of the query.

            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider all metadata fields and their semantic meaning
            - Match regardless of exact wording or language
            - If query mentions dates, people, topics, etc., check if metadata contains relevant information

            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """;

    public static final String BOOLEAN_QUERY =
            """
            You are a meeting metadata matching system. Analyze if meeting metadata semantically matches a user query.

            User query (may be in any language):
            "%s"

            Meeting metadata:
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            Agenda: %s

            Task: Determine if this meeting metadata semantically matches the intent of the query.

            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider all fields and their semantic meaning
            - Match regardless of exact wording or language
            - If query mentions dates, people, topics, etc., check if metadata contains relevant information

            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """;

    public static final String GET_FIELD =
            """
            You are an information extraction system for meeting minutes.
            Return only the requested field value with no extra text.

            Field requested: %s
            Meeting metadata:
            %s

            Value:
            """;

    private MetadataConfigurablePromptSources() {}
}
