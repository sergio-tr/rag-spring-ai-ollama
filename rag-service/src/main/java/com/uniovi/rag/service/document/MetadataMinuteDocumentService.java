package com.uniovi.rag.service.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.infrastructure.logging.LogSanitization;
import com.uniovi.rag.util.RegexSafety;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures.supplyAsync;

@Service
public class MetadataMinuteDocumentService extends AbstractMetadataDocumentService<Minute> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int UNICODE_TEXT_REGEX_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.CANON_EQ;

    /** Header/explanation lines stripped from LLM bullet lists (Unicode-aware case). */
    private static final Pattern CLEAN_LLM_HEADER_LINE_PATTERN = Pattern.compile(
            "^(?:aquí|here|below|estos|these|lista|list|a continuación|following).*$", UNICODE_TEXT_REGEX_FLAGS);

    /** Minute metadata labels to exclude from entity extraction (Unicode-aware case). */
    private static final Pattern ENTITY_METADATA_STOPWORD_PATTERN = Pattern.compile(
            "(Fecha|Lugar|Hora|Asistentes|Orden|Día|Reunión|Acta|Presidente|Secretario)",
            UNICODE_TEXT_REGEX_FLAGS);

    private static final String TIME_PATTERN_HH_MM = "HH:mm";

    private static final String TIME_PATTERN_HH_MM_SS = "HH:mm:ss";

    private static final DateTimeFormatter TIME_FMT_HH_MM = DateTimeFormatter.ofPattern(TIME_PATTERN_HH_MM);

    private static final DateTimeFormatter TIME_FMT_HH_MM_SS = DateTimeFormatter.ofPattern(TIME_PATTERN_HH_MM_SS);

    /** Role/descriptive text in parentheses after attendee names (same flags as Unicode normalization elsewhere). */
    private static final Pattern PARENTHETICAL_SUFFIX_AFTER_WHITESPACE =
            Pattern.compile("\\s*\\([^)]*\\)\\s*", UNICODE_TEXT_REGEX_FLAGS);

    private static final Pattern TRAILING_H_OR_COLON_SPACING =
            Pattern.compile("(?i)\\s*h\\s*$");

    private static final Pattern SPACE_AFTER_COLON = Pattern.compile(":\\s+");

    private static final Pattern NON_DIGIT_CHARS = Pattern.compile("\\D");

    private static final String PROMPT_HINT_DECISIONS = "decisions";

    private static final String PROMPT_HINT_TOPICS = "topics";

    /** Normalized ISO date key in chunk metadata (see {@link #addDerivedFields}). */
    private static final String METADATA_KEY_DATE_ISO = "date_iso";

    private static final String LOG_FAILED_EXTRACT_CRIT_FIELDS =
            "Failed to extract critical fields (datePresent: {}, placePresent: {}, attendeeCount: {}, decisionCount: {},"
                    + " topicCount: {})";

    private static final String LOG_EXTRACTED_MINUTE_FIELDS =
            "Extracted minute fields (datePresent: {}, placePresent: {}, startTimePresent: {}, endTimePresent: {}, "
                    + "presidentPresent: {}, secretaryPresent: {}, attendeeCount: {}, decisionCount: {}, "
                    + "mentionedEntityCount: {}, topicCount: {}, summaryLen: {})";

    /** Fallback: bullet/numbered block after Orden|Agenda|Puntos (complexity isolated for static analysis). */
    /** Bounded repetition limits backtracking depth on very long agenda-like blocks. */
    private static final Pattern AGENDA_LIKE_BULLET_BLOCK_PATTERN = Pattern.compile(
            "(?i)(?:Orden|Agenda|Puntos):?\\s*((?:[•·▪▫◦‣⁃*\\-]|\\d+[.)])\\s*[^\\n]{1,4000}(?:\\n(?!Ruegos|Preguntas|Clausura|Asistentes|No habiendo)[^\\n]{0,4000}){0,2000})",
            Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS | Pattern.CANON_EQ);

    /** Flags shared by multi-line {@code Orden del día} body matchers (DOTALL + Unicode + canonical equivalence). */
    private static final int AGENDA_BODY_REGEX_FLAGS =
            Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS | Pattern.CANON_EQ;

    private static final Pattern AGENDA_BODY_AFTER_ORDEN_PATTERN_1 = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*)(?=\\n\\s*(?:[•·▪▫◦‣⁃*\\-]\\s*)?(?:Ruegos y preguntas|No habiendo más asuntos|Clausura|$))",
            AGENDA_BODY_REGEX_FLAGS);

    private static final Pattern AGENDA_BODY_AFTER_ORDEN_PATTERN_2 = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*)(?=\\n\\s*(?:[•·▪▫◦‣⁃*\\-]\\s*)?(?:Ruegos|Preguntas|Clausura|No habiendo|Asistentes|$))",
            AGENDA_BODY_REGEX_FLAGS);

    private static final Pattern AGENDA_BODY_AFTER_ORDEN_PATTERN_3 = Pattern.compile(
            "(?i)ORDEN DEL DÍA:?\\s*(.*)(?=\\n\\s*(?:[•·▪▫◦‣⁃*\\-]\\s*)?(?:Ruegos|Preguntas|Clausura|No habiendo|$))",
            AGENDA_BODY_REGEX_FLAGS);

    private static final Pattern AGENDA_BODY_AFTER_ORDEN_IN_ATTENDEES_TAIL = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*)(?=\\n\\s*(?:[•·▪▫◦‣⁃*\\-]\\s*)?(?:Ruegos|No habiendo)|$)",
            AGENDA_BODY_REGEX_FLAGS);

    /** Filters descriptive attendee lines (not person names) using Unicode-aware matching. */
    private static final Pattern ATTENDEE_DESCRIPTOR_NOISE_PATTERN = Pattern.compile(
            ".*(cuenta|asistencia|propietarios|lista|firmada|reunión|quórum|suficiente|validez|acuerdos|tomados|declara).*",
            UNICODE_TEXT_REGEX_FLAGS);

    /**
     * Numbered list lines ({@code 1. item}). Possessive quantifiers avoid polynomial backtracking (Sonar java:S5852).
     */
    private static final Pattern NUMBERED_LIST_ITEM_PATTERN = Pattern.compile(
            "\\d++\\.\\s*+([^\\n]++)(?=\\n\\d++\\.|\\n|$)", Pattern.MULTILINE);

    /** Marks start of the attendees section (line-based parsing avoids heavy alternation in one regex). */
    private static final Pattern ASISTENTES_HEADER = Pattern.compile("(?i)Asistentes:");

    /** Next-section headers that end the attendees block when scanning line by line. */
    private static final Pattern ASISTENTES_SECTION_BOUNDARY = Pattern.compile(
            "(Orden del día|Orden del dia|Ruegos|Clausura|No habiendo)\\b.*",
            UNICODE_TEXT_REGEX_FLAGS);

    /**
     * Capitalized words / multi-word names; possessive and bounded repetition avoids catastrophic backtracking.
     */
    private static final Pattern ENTITY_CAPITALIZED_WORDS_PATTERN = Pattern.compile(
            "\\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]++(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]++){0,15})\\b",
            Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final String SPANISH_MONTH_AGOSTO = "agosto";

    private static final String SPANISH_MONTH_SEPTIEMBRE = "septiembre";

    private static final String[] SPANISH_MONTH_NAMES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", SPANISH_MONTH_AGOSTO, SPANISH_MONTH_SEPTIEMBRE, "octubre", "noviembre", "diciembre"
    };

    private static String stripParentheticalSuffix(String value) {
        return PARENTHETICAL_SUFFIX_AFTER_WHITESPACE.matcher(value).replaceAll("").trim();
    }

    /** Spring proxy so {@code @Cacheable} extraction methods are not self-invoked. */
    private MetadataMinuteDocumentService extractionSelf;

    @Autowired
    public void setExtractionSelf(@Lazy MetadataMinuteDocumentService extractionSelf) {
        this.extractionSelf = extractionSelf;
    }

    private MetadataMinuteDocumentService extraction() {
        return extractionSelf != null ? extractionSelf : this;
    }

    public static final String PROMPT_DECISIONS = """
        Extract only the explicit decisions contained in the meeting minutes, based on expressions such as "it was agreed", "it was decided", "it was approved", etc.
        Return exclusively a raw list, one sentence per line, without headers or introductions.
        """;

    public static final String PROMPT_ENTITIES = """
        Extract all specific names of entities mentioned in the meeting minutes, such as companies, technicians, public organizations, or relevant associations.
        Return exclusively a list of names, one per line, without headers or explanatory phrases.
        """;

    public static final String PROMPT_TOPICS = """
        Extract the topics discussed during the meeting.
        Each topic should be a word or short phrase (for example: "lighting", "security", "maintenance").
        Return exclusively a list of topics, one per line, without headers.
        """;

    public static final String PROMPT_SUMMARY = """
        Provide a highly detailed summary of the entire meeting minutes content, following the indicated structure.
        Do not include introductions or headers — only the detailed summary.
        """;

    public static final String SYSTEM_PROMPT_LINE_DATA = """
        You are an information extraction system for meeting minutes.
        You must behave like a function that returns only the requested data, without any additional text.
        
        It is strictly forbidden to include introductions, headers, explanatory phrases, bullet points, numbering, or comments such as:
        - “Here you have…”
        - “These are…”
        - “Below you’ll find…”
        - Rewordings of the prompt
        
        Your response must be a clean list:
        - One item per line
        - Only the requested content
        - No decorations, marks, or explanations
        """;

    public static final String SYSTEM_PROMPT_SUMMARY = """
        You are an expert assistant in generating summaries of meeting minutes.
        
        Do not add introductions, phrases such as “This is the summary...”, “Below you’ll find...”, or any kind of headers.
        
        Return a single block of text with as much detail as possible, including:
        - Discussed topics
        - Decisions made
        - Identified issues
        - Relevant proposals
        - Mentioned entities or people (if applicable)
        
        The language must be clear, formal, and objective.
        """;


    public MetadataMinuteDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate,
                                         @Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
        super(vectorStore, chatClient, jdbcTemplate, chunkMaxChars);
    }

    /**
     * Creates a list of vector-store documents from a Minute (for repository add without duplicate).
     * Builds content from summary, agenda, decisions and topics, then chunks and applies metadata.
     */
    public List<Document> createDocumentsFromMinute(Minute minute) {
        if (minute == null || minute.id() == null || minute.id().isBlank()) {
            throw new IllegalArgumentException("Minute and minute.id() must be non-null and non-blank");
        }
        String content = buildContentFromMinute(minute);
        Map<String, Object> metadata = extractMetadata(minute);
        validateMetadata(metadata, minute.filename() != null ? minute.filename() : "minute-" + minute.id());
        List<String> chunks = splitContentIntoChunks(content, chunkMaxChars);
        String metadataPrefix = buildChunkMetadataPrefix(metadata);
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("total_chunks", chunks.size());
            String chunkText = chunks.get(i);
            String contentForEmbedding = metadataPrefix.isEmpty() ? chunkText : (metadataPrefix + chunkText);
            documents.add(new Document(contentForEmbedding, chunkMetadata));
        }
        return documents;
    }

    private String buildContentFromMinute(Minute minute) {
        StringBuilder sb = new StringBuilder();
        if (minute.summary() != null && !minute.summary().isBlank()) {
            sb.append(minute.summary()).append("\n\n");
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            minute.agenda().forEach((k, v) -> sb.append("• ").append(k).append(": ").append(v != null ? v : "").append("\n"));
            sb.append("\n");
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            minute.decisions().forEach(d -> sb.append("• ").append(d).append("\n"));
            sb.append("\n");
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            sb.append(String.join(", ", minute.topics())).append("\n");
        }
        if (minute.mentionedEntities() != null && !minute.mentionedEntities().isEmpty()) {
            sb.append("Entidades: ").append(String.join(", ", minute.mentionedEntities())).append("\n");
        }
        String content = sb.toString().trim();
        return content.isEmpty() ? "Acta " + minute.id() : content;
    }

    @Override
    protected Minute extractModel(String content, String filename) {
        if (content == null || content.trim().isEmpty()) {
            log().error("Document content is null or empty");
            throw new IllegalArgumentException("Document content is null or empty. The PDF may be corrupted, protected, or contain only images.");
        }
        
        if (content.trim().length() < 20) {
            log().warn("Content very short (length: {}). Processing anyway but extraction may be incomplete.",
                    content.length());
        }
        
        String date = extractDate(content);
        String place = extractPlace(content);
        String startTime = extractStartTime(content);
        String endTime = extractEndTime(content);
        String president = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
        // Extract secretary name only, stopping at the next bullet or newline
        String secretary = extractSingle(content, "(?i)•\\s*([^•\\n]+?)\\s*\\(Secretari[ao]\\)");
        List<String> attendees = extractAttendees(content);
        Map<String, String> agenda = extractAgendaMap(content);
        
        // Log agenda extraction result
        if (agenda == null || agenda.isEmpty()) {
            log().warn("Agenda is empty. Will try fallback from topics.");
        } else {
            log().info("Extracted agenda with {} items", agenda.size());
        }

        MetadataMinuteDocumentService ext = extraction();
        CompletableFuture<List<String>> decisionsFuture =
            supplyAsync(() -> ext.extractWithPrompt(content, PROMPT_DECISIONS))
                .exceptionally(e -> {
                    log().error("Error extracting decisions from document", e);
                    return new ArrayList<>();
                });
        CompletableFuture<List<String>> entitiesFuture =
            supplyAsync(() -> ext.extractWithPrompt(content, PROMPT_ENTITIES))
                .exceptionally(e -> {
                    log().error("Error extracting entities from document", e);
                    return new ArrayList<>();
                });
        CompletableFuture<List<String>> topicsFuture =
            supplyAsync(() -> ext.extractWithPrompt(content, PROMPT_TOPICS))
                .exceptionally(e -> {
                    log().error("Error extracting topics from document", e);
                    return new ArrayList<>();
                });
        CompletableFuture<String> summaryFuture =
            supplyAsync(() -> ext.extractSummaryWithPrompt(content, PROMPT_SUMMARY))
                .exceptionally(e -> {
                    log().error("Error extracting summary from document", e);
                    return generateFallbackSummary(content);
                });

        // Wait for all extractions to complete
        List<String> decisions = decisionsFuture.join();
        List<String> mentionedEntities = entitiesFuture.join();
        List<String> topics = topicsFuture.join();
        String summary = summaryFuture.join();

        // Enrich topics with agenda keys (keeps original topics + agenda keys deduplicated)
        if (agenda != null && !agenda.isEmpty()) {
            List<String> agendaKeys = agenda.keySet().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            Set<String> merged = new LinkedHashSet<>();
            merged.addAll(topics != null ? topics : List.of());
            merged.addAll(agendaKeys);
            topics = new ArrayList<>(merged);
        } else if ((agenda == null || agenda.isEmpty()) && topics != null && !topics.isEmpty()) {
            // Fallback: If agenda is empty but topics exist, create agenda from topics
            log().info("Agenda is empty; creating agenda from {} topics as fallback.", topics != null ? topics.size() : 0);
            agenda = createAgendaFromTopics(topics);
            if (agenda != null) {
                log().info("Created agenda with {} items from topics.", agenda.size());
            }
        }
        
        if (date == null && place == null && attendees.isEmpty() && decisions.isEmpty() && (topics == null || topics.isEmpty())) {
            log().warn(
                    LOG_FAILED_EXTRACT_CRIT_FIELDS,
                    date != null,
                    place != null,
                    attendees.size(),
                    decisions.size(),
                    topics != null ? topics.size() : 0);
        }

        // Log counts and presence flags only — avoid logging upload names or extracted document text.
        log().info(
                LOG_EXTRACTED_MINUTE_FIELDS,
                date != null,
                place != null,
                startTime != null,
                endTime != null,
                president != null,
                secretary != null,
                attendees.size(),
                decisions.size(),
                mentionedEntities != null ? mentionedEntities.size() : 0,
                topics != null ? topics.size() : 0,
                summary != null ? summary.length() : 0);

        return sanitizeMinute(new Minute(
                UUID.randomUUID().toString(),
                filename,
                date,
                place,
                startTime,
                endTime,
                president,
                secretary,
                attendees,
                attendees.size(),
                agenda,
                decisions,
                mentionedEntities,
                topics,
                summary
        ));
    }


    /**
     * Extracts information using LLM with error handling and fallback to regex.
     * Returns empty list if both LLM and regex extraction fail.
     * 
     * Cached extraction to avoid re-extracting same content.
     * Cache key is based on content hash and prompt hash.
     */
    @Cacheable(
        value = "metadataExtraction", 
        key = "#content.hashCode() + '_' + #prompt.hashCode()"
    )
    private List<String> extractWithPrompt(String content, String prompt) {
        if (content == null || content.trim().isEmpty()) {
            log().warn("Empty content provided to extractWithPrompt");
            return new ArrayList<>();
        }
        
        if (prompt == null || prompt.trim().isEmpty()) {
            log().warn("Empty prompt provided to extractWithPrompt");
            return new ArrayList<>();
        }
        
        // Truncate content to prevent context length errors (max 8000 chars to leave room for prompt)
        String truncatedContent = truncateForPrompt(content, 8000);
        
        try {
            String rawResponse = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT_LINE_DATA)
                    .user(prompt + "\nTexto del acta:\n" + truncatedContent)
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                log().warn("Empty response from LLM for prompt extraction, trying regex fallback");
                return extractWithRegexFallback(content, prompt);
            }

            List<String> extracted = cleanLLMResponse(rawResponse);
            
            if (extracted.size() < 2 && content.length() > 500) {
                log().warn("LLM extraction returned very few items ({}), trying regex fallback", extracted.size());
                List<String> regexExtracted = extractWithRegexFallback(content, prompt);
                if (regexExtracted.size() > extracted.size()) {
                    log().info("Regex fallback found more items ({} vs {}), using regex results", regexExtracted.size(), extracted.size());
                    return regexExtracted;
                }
            }
            
            log().info("Extracted {} items using LLM prompt", extracted.size());
            return extracted;
        } catch (Exception e) {
            // Check if error is related to context length
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("context length") || errorMsg.contains("input length exceeds") || 
                errorMsg.contains("token") && errorMsg.contains("limit")) {
                log().error("Context length error in extractWithPrompt, trying with more aggressive truncation", e);
                // Try with even more aggressive truncation
                String moreTruncated = truncateForPrompt(content, 4000);
                try {
                    String rawResponse = chatClient
                            .prompt()
                            .system(SYSTEM_PROMPT_LINE_DATA)
                            .user(prompt + "\nTexto del acta:\n" + moreTruncated)
                            .call()
                            .content();
                    if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                        List<String> extracted = cleanLLMResponse(rawResponse);
                        log().info("Extracted {} items using LLM prompt with aggressive truncation", extracted.size());
                        return extracted;
                    }
                } catch (Exception e2) {
                    log().error("Error even with aggressive truncation, trying regex fallback", e2);
                }
            } else {
                log().error("Error extracting information with LLM prompt, trying regex fallback", e);
            }
            return extractWithRegexFallback(content, prompt);
        }
    }
    
    /**
     * Cleans the LLM response by removing headers, explanations, and incorrect formatting.
     */
    private List<String> cleanLLMResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(rawResponse.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                // Remove lines that might be headers or explanations
                .filter(line -> !CLEAN_LLM_HEADER_LINE_PATTERN.matcher(line).matches())
                // Remove separators (lines with only dashes, equals, asterisks)
                .filter(line -> !line.matches("^[-=*_]{3,}"))
                // Filter very short lines (probably not useful content)
                .filter(line -> line.length() > 3)
                // Remove numbering at the beginning (1., 2., etc.) if it exists
                .map(line -> line.replaceAll("^\\d+[.)]\\s*", "").trim())
                .filter(line -> !line.isEmpty())
                .toList();
    }
    
    /**
     * Fallback extraction using regex patterns when LLM extraction fails.
     * Attempts to extract decisions, entities, or topics using pattern matching.
     */
    private List<String> extractWithRegexFallback(String content, String prompt) {
        List<String> extracted = new ArrayList<>();
        
        // Determine extraction type based on prompt
        if (prompt.contains(PROMPT_HINT_DECISIONS) || prompt.contains("decided") || prompt.contains("agreed") || prompt.contains("approved")) {
            extracted = extractDecisionsWithRegex(content);
        } else if (prompt.contains("entities") || prompt.contains("companies") || prompt.contains("organizations")) {
            extracted = extractEntitiesWithRegex(content);
        } else if (prompt.contains(PROMPT_HINT_TOPICS) || prompt.contains("discussed")) {
            extracted = extractTopicsWithRegex(content);
        }
        
        if (!extracted.isEmpty()) {
            log().info("Regex fallback extracted {} items", extracted.size());
        } else {
            log().info("Regex fallback also failed to extract items");
        }
        
        return extracted;
    }
    
    /**
     * Extracts decisions using regex patterns.
     */
    private List<String> extractDecisionsWithRegex(String content) {
        List<String> decisions = new ArrayList<>();
        if (content == null) {
            return decisions;
        }
        String bounded = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);

        // Pattern for common decision phrases in Spanish
        Pattern decisionPattern = Pattern.compile(
            "(?i)(?:se\\s+acordó|se\\s+decidió|se\\s+aprobó|se\\s+resolvió|se\\s+decide|se\\s+acuerda|se\\s+aprueba)[:.]?\\s*(.+?)(?:\\.|$|\\n)",
            Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        
        Matcher matcher = decisionPattern.matcher(bounded);
        while (matcher.find()) {
            String decision = matcher.group(1).trim();
            if (!decision.isEmpty() && decision.length() > 10) {
                decisions.add(decision);
            }
        }
        
        return decisions;
    }
    
    /**
     * Extracts entities (companies, organizations) using regex patterns.
     */
    private List<String> extractEntitiesWithRegex(String content) {
        List<String> entities = new ArrayList<>();
        if (content == null) {
            return entities;
        }
        String bounded = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);

        Matcher matcher = ENTITY_CAPITALIZED_WORDS_PATTERN.matcher(bounded);
        Set<String> uniqueEntities = new LinkedHashSet<>();
        
        while (matcher.find()) {
            String entity = matcher.group(1).trim();
            // Filter out common words and short entities
            if (entity.length() > 3 && 
                !ENTITY_METADATA_STOPWORD_PATTERN.matcher(entity).matches()) {
                uniqueEntities.add(entity);
            }
        }
        
        entities.addAll(uniqueEntities);
        return entities.stream().limit(20).collect(Collectors.toList()); // Limit to 20 entities
    }
    
    /**
     * Extracts topics using regex patterns.
     */
    private List<String> extractTopicsWithRegex(String content) {
        List<String> topics = new ArrayList<>();
        if (content == null) {
            return topics;
        }
        String bounded = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);

        // Extract from agenda section
        String agendaSection = extractBlock(bounded, "(?i)Orden del día:", "(?i)No habiendo más asuntos");
        if (!agendaSection.isEmpty()) {
            // Extract bullet points from agenda
            Pattern topicPattern = Pattern.compile("•\\s*([^•\\n]+)");
            Matcher matcher = topicPattern.matcher(agendaSection);
            while (matcher.find()) {
                String topic = matcher.group(1).trim();
                if (!topic.isEmpty() && topic.length() < 100) {
                    topics.add(topic);
                }
            }
        }
        
        // Also look for common topic keywords
        String[] commonTopics = {"limpieza", "mantenimiento", "seguridad", "iluminación", "calefacción", 
                                 "ascensor", "presupuesto", "cuotas", "reparaciones", "climatización"};
        String lowerBounded = bounded.toLowerCase();
        for (String topic : commonTopics) {
            if (lowerBounded.contains(topic)) {
                topics.add(topic);
            }
        }
        
        return topics.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Extracts summary using LLM with error handling and fallback.
     * Returns a default summary if extraction fails.
     * 
     * Cache key is based on content hash.
     */
    @Cacheable(
        value = "summaryExtraction", 
        key = "#content.hashCode() + '_' + #prompt.hashCode()"
    )
    private String extractSummaryWithPrompt(String content, String prompt) {
        if (content == null || content.trim().isEmpty()) {
            log().warn("Empty content provided to extractSummaryWithPrompt, using fallback");
            return generateFallbackSummary(content != null ? content : "");
        }
        
        if (prompt == null || prompt.trim().isEmpty()) {
            log().warn("Empty prompt provided to extractSummaryWithPrompt, using fallback");
            return generateFallbackSummary(content);
        }
        
        // Truncate content to prevent context length errors (max 8000 chars to leave room for prompt)
        String truncatedContent = truncateForPrompt(content, 8000);
        
        try {
            String summary = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT_SUMMARY)
                    .user(prompt + "\nTexto del acta:\n" + truncatedContent)
                    .call()
                    .content();
            
            if (summary == null || summary.trim().isEmpty()) {
                log().warn("Empty summary from LLM, using fallback");
                return generateFallbackSummary(content);
            }
            
            String trimmed = summary.trim();
            log().info("Extracted summary with {} characters", trimmed.length());
            return trimmed;
        } catch (Exception e) {
            // Check if error is related to context length
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("context length") || errorMsg.contains("input length exceeds") || 
                errorMsg.contains("token") && errorMsg.contains("limit")) {
                log().error("Context length error in extractSummaryWithPrompt, trying with more aggressive truncation", e);
                // Try with even more aggressive truncation
                String moreTruncated = truncateForPrompt(content, 4000);
                try {
                    String summary = chatClient
                            .prompt()
                            .system(SYSTEM_PROMPT_SUMMARY)
                            .user(prompt + "\nTexto del acta:\n" + moreTruncated)
                            .call()
                            .content();
                    if (summary != null && !summary.trim().isEmpty()) {
                        String trimmed = summary.trim();
                        log().info("Extracted summary with {} characters using aggressive truncation", trimmed.length());
                        return trimmed;
                    }
                } catch (Exception e2) {
                    log().error("Error even with aggressive truncation, using fallback", e2);
                }
            } else {
                log().error("Error extracting summary with LLM, using fallback", e);
            }
            return generateFallbackSummary(content);
        }
    }
    
    /**
     * Generates a basic fallback summary from document content.
     * Extracts first few sentences as a simple summary.
     */
    private String generateFallbackSummary(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "Summary extraction unavailable.";
        }
        
        // Extract first paragraph or first 500 characters as fallback
        String bounded = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        String[] sentences = bounded.split("[.!?]");
        StringBuilder fallback = new StringBuilder();
        int charCount = 0;
        int maxChars = 500;
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (!sentence.isEmpty() && charCount + sentence.length() < maxChars) {
                fallback.append(sentence).append(". ");
                charCount += sentence.length();
            } else {
                break;
            }
        }
        
        String result = fallback.toString().trim();
        return result.isEmpty() ? "Summary extraction unavailable." : result;
    }

    @Override
    public Map<String, Object> extractMetadata(Minute minute) {
        Map<String, Object> metadata = new HashMap<>();
        
        // This allows grouping chunks by document and avoiding duplicate processing
        metadata.put("document_id", minute.id());
        
        // Store individual fields for direct access with type validation and normalization
        metadata.put("id", normalizeString(minute.id()));
        metadata.put("filename", normalizeString(minute.filename()));
        metadata.put("date", normalizeString(minute.date()));
        metadata.put("place", normalizeString(minute.place()));
        metadata.put("startTime", normalizeString(minute.startTime()));
        metadata.put("endTime", normalizeString(minute.endTime()));
        metadata.put("president", normalizeString(minute.president()));
        metadata.put("secretary", normalizeString(minute.secretary()));
        metadata.put("attendees", normalizeStringList(minute.attendees()));
        metadata.put("numberOfAttendees", minute.numberOfAttendees());
        metadata.put("topics", normalizeStringList(minute.topics()));
        metadata.put("decisions", normalizeStringList(minute.decisions()));
        metadata.put("mentionedEntities", normalizeStringList(minute.mentionedEntities()));
        metadata.put("summary", normalizeString(minute.summary()));
        Map<String, String> normalizedAgenda = normalizeStringMap(minute.agenda());
        metadata.put("agenda", normalizedAgenda); // Store agenda map for consistency with NER
        // agenda_raw: optional flattened agenda for quick text access
        if (!normalizedAgenda.isEmpty()) {
            String agendaRaw = normalizedAgenda.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n"));
            metadata.put("agenda_raw", agendaRaw);
        }

        // Derived and reinforced fields
        addDerivedFields(metadata);
        
        // Validate that date_iso was generated successfully
        if (metadata.containsKey("date") && !metadata.containsKey(METADATA_KEY_DATE_ISO)) {
            log().warn("Derived {} was not set while date is present. This may cause date filtering issues.",
                    METADATA_KEY_DATE_ISO);
        } else if (metadata.containsKey(METADATA_KEY_DATE_ISO)) {
            log().debug("{} successfully generated", METADATA_KEY_DATE_ISO);
        }
        
        warnIfMissingOptionalContent(metadata);
        validateSignalPresence(metadata);
        
        try {
            String minuteJson = objectMapper.writeValueAsString(minute);
            metadata.put("minute", minuteJson);
            log().info("Minute object stored in metadata as JSON for document: {}", LogSanitization.singleLineForLog(minute.id()));
        } catch (Exception e) {
            log().warn("Failed to serialize Minute object to JSON for document: {}", LogSanitization.singleLineForLog(minute.id()), e);
        }
        
        String docId = LogSanitization.singleLineForLog(minute.id());
        log().info("Metadata extracted for document: {} with {} fields (document_id: {})", docId, metadata.size(), docId);
        return metadata;
    }
    
    /**
     * Normalizes a string value for storage in metadata.
     */
    private String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    /**
     * Normalizes a list of strings for storage in metadata.
     */
    private List<String> normalizeStringList(List<String> value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return value.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Normalizes a map of strings for storage in metadata.
     */
    private Map<String, String> normalizeStringMap(Map<String, String> value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        value.forEach((k, v) -> {
            if (k != null && !k.trim().isEmpty() && v != null && !v.trim().isEmpty()) {
                normalized.put(k.trim(), v.trim());
            }
        });
        return normalized;
    }

    /**
     * Cleans and normalizes the Minute object before storing metadata.
     */
    private Minute sanitizeMinute(Minute minute) {
        if (minute == null) {
            return null;
        }

        String date = trimOrNull(minute.date());
        String place = trimOrNull(minute.place());
        String startTime = trimOrNull(minute.startTime());
        String endTime = trimOrNull(minute.endTime());
        String president = trimOrNull(minute.president());
        String secretary = trimOrNull(minute.secretary());
        String summary = trimOrNull(minute.summary());
        String filename = trimOrNull(minute.filename());

        List<String> attendees = cleanStringList(minute.attendees());
        List<String> decisions = cleanStringList(minute.decisions());
        List<String> mentionedEntities = cleanStringList(minute.mentionedEntities());
        List<String> topics = cleanStringList(minute.topics());
        Map<String, String> agenda = cleanStringMap(minute.agenda());

        int attendeesCount = minute.numberOfAttendees() > 0 ? minute.numberOfAttendees() : attendees.size();

        return new Minute(
                minute.id(),
                filename,
                date,
                place,
                startTime,
                endTime,
                president,
                secretary,
                attendees,
                attendeesCount,
                agenda,
                decisions,
                mentionedEntities,
                topics,
                summary
        );
    }

    /**
     * Adds derived fields to metadata (date_iso, year, month, durationMinutes, attendeesCount)
     * and normalizes counts.
     */
    private void addDerivedFields(Map<String, Object> metadata) {
        // date_iso, year, month - always set when date exists (fallback to year-only if full parse fails)
        Object dateObj = metadata.get("date");
        if (dateObj instanceof String dateStr) {
            LocalDate parsed = parseDateToLocalDate(dateStr);
            if (parsed == null) {
                parsed = parseDateYearFallback(dateStr);
                if (parsed != null) {
                    log().warn("Could not fully parse date '{}'; set date_iso from year only: {}. Consider normalizing date format.", dateStr, parsed.format(DateTimeFormatter.ISO_LOCAL_DATE));
                } else {
                    log().warn("Could not parse date '{}' to generate date_iso field. Date will not be searchable by ISO format.", dateStr);
                }
            }
            if (parsed != null) {
                metadata.put(METADATA_KEY_DATE_ISO, parsed.format(DateTimeFormatter.ISO_LOCAL_DATE));
                metadata.put("year", parsed.getYear());
                metadata.put("month", parsed.getMonthValue());
            }
        }

        // attendeesCount
        int attendeesCount = 0;
        Object numberOfAttendees = metadata.get("numberOfAttendees");
        if (numberOfAttendees instanceof Number number) {
            attendeesCount = number.intValue();
        }
        Object attendeesObj = metadata.get("attendees");
        if (attendeesObj instanceof List<?> list && !list.isEmpty()) {
            attendeesCount = Math.max(attendeesCount, list.size());
        }
        metadata.put("attendeesCount", attendeesCount);

        // durationMinutes
        String start = metadata.get("startTime") instanceof String ? (String) metadata.get("startTime") : null;
        String end = metadata.get("endTime") instanceof String ? (String) metadata.get("endTime") : null;
        Integer duration = calculateDurationMinutes(start, end);
        if (duration != null) {
            metadata.put("durationMinutes", duration);
        }
    }

    private void warnIfMissingOptionalContent(Map<String, Object> metadata) {
        List<?> agenda = metadata.get("agenda") instanceof Map ? new ArrayList<>(((Map<?, ?>) metadata.get("agenda")).keySet()) : List.of();
        List<?> decisions = metadata.get("decisions") instanceof List ? (List<?>) metadata.get("decisions") : List.of();
        List<?> topics = metadata.get("topics") instanceof List ? (List<?>) metadata.get("topics") : List.of();

        if (agenda.isEmpty()) {
            log().warn("Agenda is empty for extracted metadata");
        }
        if (decisions.isEmpty()) {
            log().warn("Decisions are empty for extracted metadata");
        }
        if (topics.isEmpty()) {
            log().warn("Topics are empty for extracted metadata");
        }
    }

    /**
     * Validates that the document has at least one key signal to avoid empty shells.
     */
    private void validateSignalPresence(Map<String, Object> metadata) {
        boolean hasDate = isNotBlank(metadata.get("date"));
        boolean hasPlace = isNotBlank(metadata.get("place"));
        boolean hasSummary = isNotBlank(metadata.get("summary"));
        boolean hasTopics = metadata.get("topics") instanceof List<?> l && !l.isEmpty();
        boolean hasDecisions = metadata.get("decisions") instanceof List<?> l2 && !l2.isEmpty();

        if (!hasDate && !hasPlace && !hasSummary && !hasTopics && !hasDecisions) {
            throw new IllegalArgumentException(
                    "Document has no meaningful signal (date/place/summary/topics/decisions)");
        }
    }

    private boolean isNotBlank(Object value) {
        if (value == null) return false;
        if (value instanceof String s) return !s.trim().isEmpty();
        return true;
    }

    private Integer calculateDurationMinutes(String start, String end) {
        if (start == null || end == null || start.trim().isEmpty() || end.trim().isEmpty()) {
            return null;
        }
        
        // Normalize time strings (remove extra spaces, ensure HH:mm format)
        String startNormalized = normalizeTimeFormat(start.trim());
        String endNormalized = normalizeTimeFormat(end.trim());
        
        if (startNormalized == null || endNormalized == null) {
            log().warn("Could not normalize time format: start='{}', end='{}'", start, end);
            return null;
        }
        
        try {
            // Try HH:mm format first
            LocalTime s = LocalTime.parse(startNormalized, TIME_FMT_HH_MM);
            LocalTime e = LocalTime.parse(endNormalized, TIME_FMT_HH_MM);
            
            // Validate: end time should be after start time
            if (e.isBefore(s) || e.equals(s)) {
                log().warn("Invalid duration: endTime ({}) is not after startTime ({})", endNormalized, startNormalized);
                // Check if end time is on next day (e.g., meeting ends at 01:00 next day)
                if (e.isBefore(s)) {
                    // Assume next day: add 24 hours
                    int diff = (24 * 60) - (s.getHour() * 60 + s.getMinute()) + (e.getHour() * 60 + e.getMinute());
                    log().debug("Calculated duration assuming next day: {} minutes", diff);
                    return diff > 0 && diff <= 24 * 60 ? diff : null;
                }
                return null;
            }
            
            int diff = (e.getHour() * 60 + e.getMinute()) - (s.getHour() * 60 + s.getMinute());
            
            // Validate: duration should be reasonable (between 1 minute and 24 hours)
            if (diff < 1) {
                log().warn("Invalid duration: {} minutes (too short)", diff);
                return null;
            }
            if (diff > 24 * 60) {
                log().warn("Invalid duration: {} minutes (too long, >24h)", diff);
                return null;
            }
            
            return diff;
        } catch (DateTimeParseException ex) {
            // Try alternative formats
            try {
                // Try HH:mm:ss format
                LocalTime s = LocalTime.parse(startNormalized, TIME_FMT_HH_MM_SS);
                LocalTime e = LocalTime.parse(endNormalized, TIME_FMT_HH_MM_SS);
                int diff = (e.getHour() * 60 + e.getMinute()) - (s.getHour() * 60 + s.getMinute());
                return diff > 0 && diff <= 24 * 60 ? diff : null;
            } catch (DateTimeParseException ex2) {
                log().warn("Could not parse times: start='{}', end='{}'. Error: {}", 
                          startNormalized, endNormalized, ex.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Normalizes time format to HH:mm.
     * Handles various input formats and converts them to HH:mm.
     */
    private String normalizeTimeFormat(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        
        String normalized = timeStr.trim();
        
        // Remove common prefixes/suffixes
        normalized = normalized.replaceAll("(?i)^(hora|time|h):\\s*", "");
        normalized = normalized.replaceAll("\\s*$", "");
        
        // Try to parse and reformat to HH:mm
        try {
            // Try HH:mm format first
            LocalTime time = LocalTime.parse(normalized, TIME_FMT_HH_MM);
            return time.format(TIME_FMT_HH_MM);
        } catch (DateTimeParseException ignored) {
            // Try HH:mm:ss format
            try {
                LocalTime time = LocalTime.parse(normalized, TIME_FMT_HH_MM_SS);
                return time.format(TIME_FMT_HH_MM);
            } catch (DateTimeParseException ignored2) {
                // Try H:mm format (single digit hour)
                try {
                    LocalTime time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
                    return time.format(TIME_FMT_HH_MM);
                } catch (DateTimeParseException ignored3) {
                    log().debug("Could not normalize time format: {}", timeStr);
                    return null;
                }
            }
        }
    }

    /**
     * Parses a date string to LocalDate using multiple formatters.
     * Enhanced to match the formatters used in AbstractMetadataTool.parseDateFlexible for consistency.
     */
    private LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // Normalize to lowercase to handle case variations (e.g., "Agosto" vs "agosto")
        String v = dateStr.trim().toLowerCase();

        // Try ISO format first (most common after normalization)
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO-8601; try locale-specific formatters below.
        }

        // Try Spanish formats with quotes
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                // Spanish formats without quotes
                DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")),
                // Abbreviated month names
                DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("d de MMM de yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd de MMM de yyyy", Locale.forLanguageTag("es")),
                // Without "de" between day and month
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("es")),
                // Numeric formats
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyy.MM.dd")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        
        return null;
    }

    /**
     * Fallback: extract at least year from date string so date_iso can be set (yyyy-01-01).
     * Supports "d de mes de yyyy" via regex and plain "yyyy".
     */
    private LocalDate parseDateYearFallback(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String v = dateStr.trim();
        Pattern spanishPattern = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher m = spanishPattern.matcher(v);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            int month = SPANISH_MONTH_MAP.getOrDefault(m.group(2).toLowerCase(), -1);
            int year = Integer.parseInt(m.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                try {
                    return LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth()));
                } catch (Exception ignored) {
                    // Invalid day/month composition for parsed regex groups; try plain year fallback below.
                }
            }
        }
        Matcher yearMatcher = Pattern.compile("(\\d{4})").matcher(v);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            if (year >= 1900 && year <= 2100) {
                return LocalDate.of(year, 1, 1);
            }
        }
        return null;
    }

    private static final Map<String, Integer> SPANISH_MONTH_MAP = Map.ofEntries(
        Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
        Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry(SPANISH_MONTH_AGOSTO, 8),
        Map.entry(SPANISH_MONTH_SEPTIEMBRE, 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
        Map.entry("noviembre", 11), Map.entry("diciembre", 12)
    );

    /**
     * Multiple date format extraction
     */
    private String extractDate(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Pattern 1: "25 de agosto de 2026" (current format, with flexible uppercase/lowercase)
        String date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Pattern 2: "25/08/2026" or "25-08-2026"
        date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Pattern 3: "2026-08-25" (ISO format)
        date = extractSingle(content, "(?i)Fecha:\\s*(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Pattern 4: Without "Fecha:" label, search in header
        date = extractSingle(content, "(?i)^(?:ACTA|ACTA DE REUNIÓN|REUNIÓN).*?(\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Pattern 5: Date with day of the week (e.g. "Lunes, 25 de agosto de 2026")
        date = extractSingle(content, "(?i)Fecha:\\s*(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),?\\s*(\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Pattern 6: Abbreviated months (e.g. "25 ago 2026")
        date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2}\\s+(?:ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }

        // Pattern 7: Date with day of the week and short numeric format (e.g. "lunes, 25/02/2025" or "25/02/25")
        date = extractSingle(content, "(?i)Fecha:\\s*(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})");
        if (date != null) {
            return normalizeDate(date);
        }

        // Pattern 8: Date with abbreviation without label (e.g. "25 feb 2025" in header)
        date = extractSingle(content, "(?i)^(?:ACTA|ACTA DE REUNIÓN|REUNIÓN).*?(\\d{1,2}\\s+(?:ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)\\s+\\d{2,4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        log().warn("Could not extract date from document");
        return null;
    }
    
    /**
     * Normalizes the date to standard format "DD de mes de YYYY" (lowercase month).
     * This ensures consistency and allows proper parsing regardless of original capitalization.
     */
    private String normalizeDate(String date) {
        if (date == null) return null;
        
        try {
            // If already in format "day of month of year", normalize to lowercase
            if (date.matches("(?iu)\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}")) {
                return normalizeToLowercaseDate(date);
            }
            
            // If in numeric format, convert
            if (date.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) {
                return convertNumericDate(date);
            }
            
            if (date.matches("\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}")) {
                return convertISODate(date);
            }
            
            // Return lowercase version if possible to ensure consistency
            return date.toLowerCase();
        } catch (Exception e) {
            log().warn("Error normalizing date: {}", date, e);
            return date.toLowerCase();
        }
    }
    
    /**
     * Normalizes date to lowercase format to ensure consistent parsing.
     */
    private String normalizeToLowercaseDate(String date) {
        // Return lowercase version to ensure consistent parsing
        return date.toLowerCase();
    }
    
    private String convertNumericDate(String date) {
        try {
            String[] parts = date.split("[/-]");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                
                if (month >= 1 && month <= 12) {
                    return String.format("%d de %s de %d", day, SPANISH_MONTH_NAMES[month - 1], year);
                }
            }
        } catch (Exception e) {
            log().warn("Error converting numeric date: {}", date, e);
        }
        return date;
    }
    
    private String convertISODate(String date) {
        try {
            String[] parts = date.split("[/-]");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                
                if (month >= 1 && month <= 12) {
                    return String.format("%d de %s de %d", day, SPANISH_MONTH_NAMES[month - 1], year);
                }
            }
        } catch (Exception e) {
            log().warn("Error converting ISO date: {}", date, e);
        }
        return date;
    }
    
    /**
     * Improved place extraction with length validation.
     */
    private String extractPlace(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Pattern 1: "Lugar: [lugar]" stopping before "Hora" or time patterns
        // Stop at: Time, Start time, End time, or time pattern (HH:MM)
        String place = extractSingle(content, "(?i)Lugar:\\s*([^\\n]{0,150}?)(?=\\s*(?:Hora|\\d{1,2}:\\s*\\d{2}|Asistentes:|Orden del día))");
        if (place != null && !place.trim().isEmpty()) {
            place = place.trim().replaceAll("\\.$", "").trim();
            if (place.length() > 5 && place.length() < 200) {
                return place;
            }
        }
        
        // Pattern 2: Search in header context, stopping before hours
        String header = extractBlock(content, "(?i)^(?:ACTA|REUNIÓN)", "(?i)Asistentes:");
        if (header != null) {
            place = extractSingle(header, "(?i)Lugar[^:]*:\\s*([^\\n]{0,150}?)(?=\\s*(?:Hora|\\d{1,2}:\\s*\\d{2}|Asistentes:|Orden del día))");
            if (place != null && place.length() > 5 && place.length() < 200) {
                return place.trim().replaceAll("\\.$", "").trim();
            }
        }
        
        // Pattern 3: Place label variants, stopping before hours
        String[] placeLabels = {"Lugar de celebración", "Sitio", "Ubicación", "Localización", "Lugar"};
        for (String label : placeLabels) {
            place = extractSingle(content, String.format("(?i)%s[^:]*:\\s*([^\\n]{0,150}?)(?=\\s*(?:Hora|\\d{1,2}:\\s*\\d{2}|Asistentes:|Orden del día))", label));
            if (place != null && place.length() > 5 && place.length() < 200) {
                return place.trim().replaceAll("\\.$", "").trim();
            }
        }
        
        log().warn("Could not extract place from document");
        return null;
    }
    
    /**
     * Improved start time extraction with multiple formats.
     */
    private String extractStartTime(String content) {
        return extractTime(content, true);
    }
    
    /**
     * Improved end time extraction with multiple formats.
     */
    private String extractEndTime(String content) {
        return extractTime(content, false);
    }

    /**
     * Extracts end time from conclusion phrases (e.g. "ended at 20:45", "concluding at 20:45").
     * Used to prefer the actual closing time when it appears in the body rather than a header range.
     */
    private String extractEndTimeFromConclusionPhrases(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        // "ended at 20:45", "concluded at 20:45" (Spanish/English)
        String[] patterns = {
            "(?i)(?:finalizó|terminó|concluyó|finaliza|termina)\\s+(?:la reunión\\s+)?(?:a las?|a)\\s*(\\d{1,2}:\\s*\\d{2})(?:\\s*[hH])?",
            "(?i)(?:concluding|concluded|terminated)\\s+(?:at|a las?)\\s*(\\d{1,2}:\\s*\\d{2})(?:\\s*[hH])?",
            "(?i)reunión\\s+(?:finalizó|terminó|concluyó)\\s+(?:a las?|a)\\s*(\\d{1,2}:\\s*\\d{2})"
        };
        for (String regex : patterns) {
            String t = extractSingle(content, regex);
            if (t == null) {
                continue;
            }
            String normalized = normalizeTime(t);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
    
    private String extractTimeFromLabelGrid(String content, String[] labels, String[] timePatterns) {
        for (String label : labels) {
            for (String timePattern : timePatterns) {
                String regex = String.format("(?i)Hora de %s:\\s*%s", label, timePattern);
                String time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
                regex = String.format("(?i)Hora %s:\\s*%s", label, timePattern);
                time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
                String labelCapitalized = label.substring(0, 1).toUpperCase() + label.substring(1);
                regex = String.format("(?i)%s:\\s*%s", labelCapitalized, timePattern);
                time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
            }
        }
        return null;
    }

    private String extractTimeFromHeaderLabels(String content, String[] labels, String[] timePatterns) {
        String header = extractBlock(content, "(?i)^(?:ACTA|REUNIÓN)", "(?i)Asistentes:");
        if (header == null) {
            return null;
        }
        for (String label : labels) {
            for (String timePattern : timePatterns) {
                String regex = String.format("(?i)(?:Hora de |Hora )?%s[^:]*:\\s*%s", label, timePattern);
                String time = extractSingle(header, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
            }
        }
        return null;
    }

    private String extractTimeNearKeywordLine(String content, boolean isStartTime) {
        String keyword = isStartTime ? "inicio|comienzo|comienza" : "fin|final|termina|clausura";
        String time =
                extractSingle(content, String.format("(?i)(?:%s)[^:]*:\\s*(\\d{1,2}:\\s*\\d{2})", keyword));
        return time != null ? normalizeTime(time) : null;
    }

    private String extractTimeFromDashRanges(String content, boolean isStartTime) {
        if (isStartTime) {
            String time =
                    extractSingle(content, "(?i)(\\d{1,2}:\\s*\\d{2})\\s*[-a]\\s*\\d{1,2}:\\s*\\d{2}");
            return time != null ? normalizeTime(time) : null;
        }
        String time = extractSingle(content, "(?i)\\d{1,2}:\\s*\\d{2}\\s*[-a]\\s*(\\d{1,2}:\\s*\\d{2})");
        if (time != null) {
            return normalizeTime(time);
        }
        String fromConclusion = extractEndTimeFromConclusionPhrases(content);
        if (fromConclusion != null) {
            return fromConclusion;
        }
        Pattern rangePattern =
                Pattern.compile("(\\d{1,2}:\\s*\\d{2})\\s*[-a]\\s*(\\d{1,2}:\\s*\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(content);
        String lastEnd = null;
        while (rangeMatcher.find()) {
            lastEnd = rangeMatcher.group(2);
        }
        if (lastEnd == null) {
            return null;
        }
        return normalizeTime(lastEnd);
    }

    private String extractTimeFromFirstLinesHeuristic(String content, boolean isStartTime) {
        String firstLines = content.length() > 500 ? content.substring(0, 500) : content;
        Pattern localTimePattern = Pattern.compile("(\\d{1,2}:\\s*\\d{2})");
        Matcher matcher = localTimePattern.matcher(firstLines);
        List<String> foundTimes = new ArrayList<>();
        while (matcher.find() && foundTimes.size() < 3) {
            String foundTime = matcher.group(1);
            if (normalizeTime(foundTime) != null) {
                foundTimes.add(foundTime);
            }
        }
        if (foundTimes.isEmpty()) {
            return null;
        }
        if (isStartTime) {
            return normalizeTime(foundTimes.get(0));
        }
        if (foundTimes.size() >= 2) {
            return normalizeTime(foundTimes.get(foundTimes.size() - 1));
        }
        return normalizeTime(foundTimes.get(0));
    }

    /**
     * Extracts the time with multiple supported formats.
     *
     * @param content Content of the document
     * @param isStartTime true for start time, false for end time
     */
    private String extractTime(String content, boolean isStartTime) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String[] startLabels = {"inicio", "comienzo", "comienza"};
        String[] endLabels = {"fin", "finalización", "finaliza", "termina", "clausura"};
        String[] labels = isStartTime ? startLabels : endLabels;

        String[] timePatterns = {
            "(\\d{1,2}:\\s*\\d{2})",
            "(\\d{1,2}:\\s*\\d{2})\\s*[hH]",
            "(\\d{1,2}\\.\\d{2})",
            "(\\d{1,2})\\s*[hH]",
            "(\\d{1,2})\\s*[hH]\\s*(\\d{2})?"
        };

        String t = extractTimeFromLabelGrid(content, labels, timePatterns);
        if (t != null) {
            return t;
        }
        t = extractTimeFromHeaderLabels(content, labels, timePatterns);
        if (t != null) {
            return t;
        }
        t = extractTimeNearKeywordLine(content, isStartTime);
        if (t != null) {
            return t;
        }
        t = extractTimeFromDashRanges(content, isStartTime);
        if (t != null) {
            return t;
        }
        t = extractTimeFromFirstLinesHeuristic(content, isStartTime);
        if (t != null) {
            return t;
        }

        log().info(
                "Could not extract the time of {} from the document (this is optional and does not affect functionality)",
                isStartTime ? "inicio" : "fin");
        return null;
    }
    
    /**
     * Normalizes the time to standard HH:MM format.
     * Handles multiple formats and edge cases.
     */
    private String normalizeTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return null;
        }
        
        // Clean and normalize
        time = SPACE_AFTER_COLON.matcher(TRAILING_H_OR_COLON_SPACING.matcher(time.trim()).replaceAll(""))
                .replaceAll(":")
                .replace(".", ":")
                .trim();

        // Format "19 h 30" -> "19:30"
        if (time.matches("^\\d{1,2}\\s*[hH]\\s*\\d{2}$")) {
            time = time.replaceAll("\\s*[hH]\\s*", ":").replaceAll("\\s+", "");
        }

        // Format "19 h" -> "19:00"
        if (time.matches("^\\d{1,2}$")) {
            try {
                int hour = Integer.parseInt(time);
                if (hour >= 0 && hour <= 23) {
                    return String.format("%02d:00", hour);
                }
            } catch (NumberFormatException ignored) {
                // Fall back to next checks
            }
        }
        
        // Try to extract time from HH:MM or H:MM format (with optional space after colon)
        if (time.matches("\\d{1,2}:\\s*\\d{2}")) {
            try {
                String[] parts = time.split(":");
                if (parts.length != 2) {
                    return null;
                }
                
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                
                // Validate range
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    return String.format("%02d:%02d", hour, minute);
                } else {
                    log().info("Time out of valid range: {}:{}", hour, minute);
                    return null;
                }
            } catch (NumberFormatException e) {
                log().info("Error parsing time '{}': {}", time, e.getMessage());
                return null;
            }
        }
        
        // If it does not match the expected pattern, try to extract numbers
        String numbersOnly = NON_DIGIT_CHARS.matcher(time).replaceAll("");
        if (numbersOnly.length() >= 3 && numbersOnly.length() <= 4) {
            // Format HHMM or HMM
            try {
                int hour;
                int minute;
                if (numbersOnly.length() == 4) {
                    hour = Integer.parseInt(numbersOnly.substring(0, 2));
                    minute = Integer.parseInt(numbersOnly.substring(2, 4));
                } else {
                    hour = Integer.parseInt(numbersOnly.substring(0, 1));
                    minute = Integer.parseInt(numbersOnly.substring(1, 3));
                }
                
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    return String.format("%02d:%02d", hour, minute);
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        log().info("Could not normalize time: '{}'", time);
        return null;
    }
    
    private static boolean isAttendeeDescriptorNoise(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return RegexSafety.matcher(ATTENDEE_DESCRIPTOR_NOISE_PATTERN, text, 8192).matches();
    }

    /**
     * Improved attendees extraction with multiple fallback methods.
     */
    private List<String> extractAttendees(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> attendees = new ArrayList<>();
        
        // Method 1: Between "Attendees:" and "Agenda:" (actual)
        String block = extractBlock(content, "(?i)Asistentes:", "(?i)Orden del día:");
        if (block != null && !block.trim().isEmpty()) {
            // Remove everything before the first list delimiter (bullet, dash, asterisk, or number)
            String cleanedBlock = removeTextBeforeFirstDelimiter(block);
            if (!cleanedBlock.trim().isEmpty()) {
                attendees.addAll(extractBullets(cleanedBlock));
            }
            
            // If no bullets found, try comma-separated list after ":"
            if (attendees.isEmpty()) {
                attendees.addAll(extractCommaSeparatedNames(block));
            }
        }
        
        // Method 2: If not found, search until finding another section
        if (attendees.isEmpty()) {
            block = extractBlock(content, "(?i)Asistentes:", "(?i)(?:Orden del día|Ruegos|Clausura|No habiendo)");
            if (block != null && !block.trim().isEmpty()) {
                String cleanedBlock = removeTextBeforeFirstDelimiter(block);
                if (!cleanedBlock.trim().isEmpty()) {
                    attendees.addAll(extractBullets(cleanedBlock));
                }
                
                // If still empty, try comma-separated list
                if (attendees.isEmpty()) {
                    attendees.addAll(extractCommaSeparatedNames(block));
                }
            }
        }
        
        // Method 3: Lines after "Asistentes:" until the next section (simple scan; avoids one complex regex)
        if (attendees.isEmpty()) {
            Matcher header = ASISTENTES_HEADER.matcher(content);
            if (header.find()) {
                String afterHeader = content.substring(header.end());
                String asistentesSlice = sliceUntilAsistentesSectionBoundary(afterHeader);
                if (!asistentesSlice.trim().isEmpty()) {
                    attendees.addAll(extractBullets(asistentesSlice));
                }
            }
        }
        
        // Clean and normalize names
        attendees = attendees.stream()
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .filter(name -> name.length() > 2)
            // Remove roles between parentheses (extracted separately)
            .map(MetadataMinuteDocumentService::stripParentheticalSuffix)
            .filter(name -> !name.isEmpty())
            // Filter out descriptive text that might have been captured
            .filter(name -> !ATTENDEE_DESCRIPTOR_NOISE_PATTERN.matcher(name).matches())
            .distinct()
            .collect(Collectors.toList());
        
        // Remove first element if it looks like descriptive text (simple solution)
        if (!attendees.isEmpty()) {
            String first = attendees.get(0).toLowerCase();
            if (first.contains("cuenta") || first.contains("asistencia") || first.contains("propietarios") ||
                first.contains("lista") || first.contains("firmada") || first.contains("reunión") ||
                first.contains("quórum") || first.contains("suficiente") || first.contains("validez") ||
                first.contains("acuerdos") || first.contains("tomados") || first.contains("declara") ||
                first.length() > 100) { // Also remove if too long (likely descriptive text)
                attendees.remove(0);
            }
        }
        
        log().info("Extracted {} attendees", attendees.size());
        return attendees;
    }
    
    /**
     * Removes all text before the first list delimiter (bullet, dash, asterisk, or number).
     * This discards introductory/descriptive text before the actual list.
     * Supports multiple unordered list symbols: •, -, *, etc.
     */
    private String removeTextBeforeFirstDelimiter(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        // List of unordered list delimiters to search for
        String[] delimiters = {"•", "-", "*"};
        
        int firstIndex = Integer.MAX_VALUE;
        String foundDelimiter = null;
        
        // Find the first occurrence of any delimiter
        for (String delimiter : delimiters) {
            int index = text.indexOf(delimiter);
            if (index >= 0 && index < firstIndex) {
                firstIndex = index;
                foundDelimiter = delimiter;
            }
        }
        
        // If found, return text from that delimiter onwards
        if (firstIndex < Integer.MAX_VALUE && foundDelimiter != null) {
            return text.substring(firstIndex);
        }
        
        // If no delimiter found, try pattern-based search for delimiters at start of line
        Pattern[] patterns = {
            Pattern.compile("(?:^|\\n)\\s*•\\s+[A-ZÁÉÍÓÚÑ]", Pattern.MULTILINE),  // Bullet
            Pattern.compile("(?:^|\\n)\\s*-\\s+[A-ZÁÉÍÓÚÑ]", Pattern.MULTILINE),   // Dash
            Pattern.compile("(?:^|\\n)\\s*\\*\\s+[A-ZÁÉÍÓÚÑ]", Pattern.MULTILINE), // Asterisk
            Pattern.compile("(?:^|\\n)\\s*\\d+\\.\\s+[A-ZÁÉÍÓÚÑ]", Pattern.MULTILINE) // Numbered
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return text.substring(matcher.start());
            }
        }
        
        // If no delimiter found, return original (might be comma-separated)
        return text;
    }
    
    /**
     * Extracts names from a comma-separated list after ":".
     * Example: "Attendees: John Doe, Jane Smith, ..."
     */
    private List<String> extractCommaSeparatedNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return names;
        }
        
        // Look for pattern: ":" followed by names separated by commas
        Pattern pattern = Pattern.compile(":\\s*([^\\n]+)(?=\\n|$)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String namesText = matcher.group(1).trim();
            // Split by comma
            String[] nameParts = namesText.split(",");
            for (String name : nameParts) {
                name = name.trim();
                // Filter out descriptive text
                if (!name.isEmpty() && name.length() > 2 && !isAttendeeDescriptorNoise(name)) {
                    names.add(name);
                }
            }
        }
        
        return names;
    }

    /** Text after {@code Asistentes:} until a known next-section heading (exclusive). */
    private static String sliceUntilAsistentesSectionBoundary(String afterHeader) {
        if (afterHeader == null || afterHeader.isEmpty()) {
            return "";
        }
        String[] lines = afterHeader.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && ASISTENTES_SECTION_BOUNDARY.matcher(t).matches()) {
                break;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * Extraction of bullets with multiple supported formats.
     * Handles both multi-line (one bullet per line) and single-line (multiple bullets separated by any list symbol) formats.
     * Supports: • (bullet), - (dash), * (asterisk), and numbered lists (1., 2., etc.)
     */
    private List<String> extractBullets(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }
        
        // Skip if the text doesn't start with a delimiter (might be descriptive text)
        String trimmed = text.trim();
        if (!trimmed.startsWith("•") && !trimmed.matches("^\\s*[-*]") && !trimmed.matches("^\\s*\\d+\\.")) {
            // Text doesn't start with delimiter, find first delimiter
            int firstBullet = trimmed.indexOf("•");
            int firstDash = trimmed.indexOf("-");
            int firstAsterisk = trimmed.indexOf("*");
            int firstNumber = trimmed.indexOf("1.");
            
            int firstDelimiter = Integer.MAX_VALUE;
            if (firstBullet >= 0) firstDelimiter = Math.min(firstDelimiter, firstBullet);
            if (firstDash >= 0) firstDelimiter = Math.min(firstDelimiter, firstDash);
            if (firstAsterisk >= 0) firstDelimiter = Math.min(firstDelimiter, firstAsterisk);
            if (firstNumber >= 0) firstDelimiter = Math.min(firstDelimiter, firstNumber);
            
            if (firstDelimiter < Integer.MAX_VALUE) {
                trimmed = trimmed.substring(firstDelimiter);
            } else {
                // No delimiter found, return empty
                return items;
            }
        }
        
        // Detect the most common list symbol in the cleaned text
        String detectedSymbol = detectListSymbol(trimmed);
        
        // Method 1: Split by detected symbol if multiple items in same line
        if (detectedSymbol != null && !detectedSymbol.equals("1.")) {
            // Escape special regex characters
            String escapedSymbol = Pattern.quote(detectedSymbol);
            String[] parts = trimmed.split(escapedSymbol);
            if (parts.length > 1) {
                // Multiple items separated by the same symbol
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i].trim();
                    // Skip the first part if it's empty or doesn't look like a name (might be leftover text)
                    if (i == 0 && (part.isEmpty() || part.length() < 3 || isAttendeeDescriptorNoise(part))) {
                        continue;
                    }
                    // Remove leading/trailing list symbols and whitespace
                    part = part.replaceAll("^[•\\-*\\d\\.\\s]+", "").replaceAll("[•\\-*\\d\\.\\s]+$", "").trim();
                    if (!part.isEmpty() && part.length() > 2) {
                        // Remove roles in parentheses (extracted separately)
                        part = stripParentheticalSuffix(part);
                        // Filter out descriptive text
                        if (!part.isEmpty() && !isAttendeeDescriptorNoise(part)) {
                            items.add(part);
                        }
                    }
                }
            }
        }
        
        // Method 2: Pattern-based extraction (one item per line or separated by newlines)
        if (items.isEmpty()) {
            // Pattern for bullet points (•)
            Pattern pattern1 = Pattern.compile("•\\s*([^•\\n]+)(?=\\s*•|\\n|$)", Pattern.MULTILINE);
            Matcher matcher1 = RegexSafety.matcher(pattern1, text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
            while (matcher1.find()) {
                String item = matcher1.group(1).trim();
                item = stripParentheticalSuffix(item);
                if (!item.isEmpty() && item.length() > 2) {
                    items.add(item);
                }
            }
            
            // Pattern for dashes (-)
            if (items.isEmpty()) {
                Pattern pattern2 = Pattern.compile("(?:^|\\n)\\s*-\\s*([^\\n\\-]+)(?=\\s*-|\\n|$)", Pattern.MULTILINE);
                Matcher matcher2 = RegexSafety.matcher(pattern2, text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
                while (matcher2.find()) {
                    String item = matcher2.group(1).trim();
                    item = stripParentheticalSuffix(item);
                    if (!item.isEmpty() && item.length() > 2) {
                        items.add(item);
                    }
                }
            }
            
            // Pattern for asterisks (*)
            if (items.isEmpty()) {
                Pattern pattern3 = Pattern.compile("(?:^|\\n)\\s*\\*\\s*([^\\n\\*]+)(?=\\s*\\*|\\n|$)", Pattern.MULTILINE);
                Matcher matcher3 = RegexSafety.matcher(pattern3, text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
                while (matcher3.find()) {
                    String item = matcher3.group(1).trim();
                    item = stripParentheticalSuffix(item);
                    if (!item.isEmpty() && item.length() > 2) {
                        items.add(item);
                    }
                }
            }
            
            // Pattern for numbered lists (1., 2., etc.)
            if (items.isEmpty()) {
                Matcher matcher4 =
                        RegexSafety.matcher(NUMBERED_LIST_ITEM_PATTERN, text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
                while (matcher4.find()) {
                    String item = matcher4.group(1).trim();
                    item = stripParentheticalSuffix(item);
                    if (!item.isEmpty() && item.length() > 2) {
                        items.add(item);
                    }
                }
            }
        }
        
        // Clean and deduplicate
        return items.stream()
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .filter(item -> item.length() > 2)
            .distinct()
            .toList();
    }
    
    /**
     * Detects the most common list symbol in the text.
     * Returns the symbol that appears most frequently as a list marker.
     */
    private String detectListSymbol(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        // Count occurrences of each list symbol
        int bulletCount = countOccurrences(text, "•");
        int dashCount = countOccurrences(text, "-");
        int asteriskCount = countOccurrences(text, "*");
        int numberCount = countNumberedListItems(text);
        
        // Return the most common symbol
        int maxCount = Math.max(Math.max(bulletCount, dashCount), Math.max(asteriskCount, numberCount));
        
        if (maxCount < 2) {
            // Not enough items to be a list
            return null;
        }
        
        if (bulletCount == maxCount) return "•";
        if (dashCount == maxCount) return "-";
        if (asteriskCount == maxCount) return "*";
        // When maxCount >= 2, at least one counter equals maxCount; if not bullet/dash/asterisk, it is numbered.
        return "1.";
    }
    
    /**
     * Counts occurrences of a symbol that appear to be list markers (followed by text).
     */
    private int countOccurrences(String text, String symbol) {
        if (text == null || symbol == null) {
            return 0;
        }
        
        // Count symbol followed by whitespace and text (not just punctuation)
        String escapedSymbol = Pattern.quote(symbol);
        Pattern pattern = Pattern.compile(escapedSymbol + "\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ]", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
    
    /**
     * Counts numbered list items (1., 2., etc.).
     */
    private int countNumberedListItems(String text) {
        if (text == null) {
            return 0;
        }
        
        Pattern pattern = Pattern.compile("\\d+\\.\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ]", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void flushAgendaEntry(Map<String, String> agenda, String currentKey, StringBuilder currentValue) {
        if (currentKey != null && currentValue.length() > 0) {
            agenda.put(currentKey, currentValue.toString().trim());
            currentValue.setLength(0);
        }
    }

    private static boolean isAgendaNewItemLine(String line, String currentKey) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.strip();
        char first = trimmed.charAt(0);
        if (isBullet(first)) {
            return hasNonBlankAfterPrefix(trimmed, 1);
        }
        if (Character.isDigit(first)) {
            int i = 0;
            while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
                i++;
            }
            if (i < trimmed.length() && (trimmed.charAt(i) == '.' || trimmed.charAt(i) == ')')) {
                return hasNonBlankAfterPrefix(trimmed, i + 1);
            }
        }
        // Uppercase line as a new item only when there is no current key yet.
        if (currentKey == null) {
            return isUppercaseLetter(first) && trimmed.length() > 1;
        }
        return false;
    }

    private static boolean isAgendaContinuationLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.strip();
        String lower = t.toLowerCase();
        return !(lower.startsWith("ruegos") || lower.startsWith("no habiendo") || lower.startsWith("clausura"));
    }

    private static boolean isBullet(char c) {
        return c == '•'
                || c == '·'
                || c == '▪'
                || c == '▫'
                || c == '◦'
                || c == '‣'
                || c == '⁃'
                || c == '*'
                || c == '-';
    }

    private static boolean hasNonBlankAfterPrefix(String s, int idx) {
        if (idx < 0 || idx >= s.length()) {
            return false;
        }
        int i = idx;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i < s.length();
    }

    private static boolean isUppercaseLetter(char c) {
        return Character.isLetter(c) && Character.isUpperCase(c);
    }

    private void parseAgendaLinesIntoMap(String[] lines, Map<String, String> agenda) {
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isAgendaNewItemLine(line, currentKey)) {
                flushAgendaEntry(agenda, currentKey, currentValue);
                currentKey = extractAgendaItemKey(line);
            } else if (currentKey != null && isAgendaContinuationLine(line)) {
                currentValue.append(line).append(" ");
            }
        }
        if (currentKey != null) {
            agenda.put(currentKey, currentValue.toString().trim());
        }
    }

    /**
     * Improved agenda extraction with support for sub-items and multiple formats.
     */
    private Map<String, String> extractAgendaMap(String content) {
        Map<String, String> agenda = new LinkedHashMap<>();
        if (content == null || content.trim().isEmpty()) {
            log().debug("Content is null or empty, cannot extract agenda");
            return agenda;
        }
        
        // Extract complete agenda block
        String agendaBlock = extractAgendaBlock(content);
        if (agendaBlock == null || agendaBlock.trim().isEmpty()) {
            // Not critical - the agenda is optional and may be in other formats
            log().debug("No agenda block found in content (this is optional and does not affect functionality)");
            return agenda;
        }
        
        log().debug("Found agenda block (length: {} chars), parsing items", agendaBlock.length());
        
        parseAgendaLinesIntoMap(agendaBlock.split("\\n"), agenda);
        
        log().debug("Extracted {} agenda items from agenda block", agenda.size());
        if (agenda.isEmpty()) {
            log().warn("Agenda map is empty after parsing agenda block. Content may not have structured agenda format.");
        }
        
        return agenda;
    }
    
    /**
     * Creates an agenda map from topics list as fallback when agenda extraction fails.
     * Each topic becomes an agenda item with the topic as both key and value.
     * 
     * @param topics List of topics to convert to agenda
     * @return Map representing agenda (key: topic, value: topic)
     */
    private Map<String, String> createAgendaFromTopics(List<String> topics) {
        Map<String, String> agenda = new LinkedHashMap<>();
        if (topics == null || topics.isEmpty()) {
            return agenda;
        }
        
        for (String topic : topics) {
            if (topic != null && !topic.trim().isEmpty()) {
                String trimmedTopic = topic.trim();
                // Use topic as both key and value
                agenda.put(trimmedTopic, trimmedTopic);
            }
        }
        
        log().debug("Created agenda from {} topics", agenda.size());
        return agenda;
    }
    
    /**
     * Extract the complete agenda block with multiple fallbacks.
     */
    private String extractAgendaBlock(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        String bounded = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        // Ensure we have newlines (content may have been normalized with wrong regex in the past)
        String normalized = bounded.contains("\n") ? bounded : bounded.replaceAll("(?<=[.!])\\s+", "\n");

        String block = agendaBodyNonEmptyGroup1(AGENDA_BODY_AFTER_ORDEN_PATTERN_1, normalized);
        if (block != null) {
            return block;
        }
        block = agendaBodyNonEmptyGroup1(AGENDA_BODY_AFTER_ORDEN_PATTERN_2, normalized);
        if (block != null) {
            return block;
        }
        block = agendaBodyNonEmptyGroup1(AGENDA_BODY_AFTER_ORDEN_PATTERN_3, normalized);
        if (block != null) {
            return block;
        }

        String afterAttendees = extractBlock(normalized, "(?i)Asistentes:", "(?i)(?:Ruegos|Preguntas|Clausura|No habiendo|$)");
        if (afterAttendees != null && !afterAttendees.trim().isEmpty()) {
            block = agendaBodyNonEmptyGroup1(AGENDA_BODY_AFTER_ORDEN_IN_ATTENDEES_TAIL, afterAttendees);
            if (block != null && block.length() > 10) {
                return block;
            }
        }

        Matcher matcher = RegexSafety.matcher(AGENDA_LIKE_BULLET_BLOCK_PATTERN, normalized, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        if (matcher.find()) {
            String bulletBlock = matcher.group(1).trim();
            if (!bulletBlock.isEmpty() && bulletBlock.length() > 10) {
                return bulletBlock;
            }
        }

        return null;
    }

    /** First capturing group trimmed; {@code null} when absent or blank after trim. */
    private static String agendaBodyNonEmptyGroup1(Pattern pattern, CharSequence input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return null;
        }
        String body = matcher.group(1).trim();
        return body.isEmpty() ? null : body;
    }
    
    /**
     * Extracts the key of an agenda item, stripping bullets and numbering.
     */
    private String extractAgendaItemKey(String line) {
        // Remove bullets and numbering
        line = line.replaceAll("^[•·▪▫◦‣⁃*\\-]\\s*", "")
                   .replaceAll("^\\d+[.)]\\s*", "")
                   .trim();
        
        // Take only the first part (up to colon or period)
        String[] parts = line.split("[:.]", 2);
        return parts[0].trim();
    }

    

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> cleanStringList(List<String> input) {
        if (input == null) {
            return new ArrayList<>();
        }
        return input.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, String> cleanStringMap(Map<String, String> input) {
        if (input == null) {
            return new LinkedHashMap<>();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        input.forEach((k, v) -> {
            if (k != null && v != null) {
                String kt = k.trim();
                String vt = v.trim();
                if (!kt.isEmpty() && !vt.isEmpty()) {
                    cleaned.put(kt, vt);
                }
            }
        });
        return cleaned;
    }
    
    private String extractSingle(String text, String regex) {
        if (text == null) {
            return null;
        }
        String bounded = RegexSafety.truncateString(text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile(regex).matcher(bounded);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractBlock(String text, String startRegex, String endRegex) {
        if (text == null) {
            return "";
        }
        String bounded = RegexSafety.truncateString(text, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Pattern pattern = Pattern.compile(startRegex + "(.*)" + endRegex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(bounded);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
    
    /**
     * Truncates content before sending to LLM prompt to avoid context length errors.
     * Preserves header and footer to maintain relevant signal.
     */
    private String truncateForPrompt(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }

        int head = (int) (maxChars * 0.65); // Keep more header
        int tail = maxChars - head;
        String truncated = trimmed.substring(0, head) + "\n...\n" + trimmed.substring(trimmed.length() - tail);
        log().info("Prompt content truncated from {} to {} characters", trimmed.length(), truncated.length());
        return truncated;
    }
}
