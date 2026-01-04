package com.uniovi.rag.services.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.model.Minute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;

@Service
public class MetadataMinuteDocumentService extends AbstractMetadataDocumentService<Minute> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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


    public MetadataMinuteDocumentService(PgVectorStore vectorStore, ChatClient chatClient, JdbcTemplate jdbcTemplate) {
        super(vectorStore, chatClient, jdbcTemplate);
    }

    @Override
    protected Minute extractModel(String content, String filename) {
        if (content == null || content.trim().isEmpty()) {
            log().error("Content is null or empty for file: {}", filename);
            throw new IllegalArgumentException("Document content is null or empty. The PDF may be corrupted, protected, or contain only images.");
        }
        
        if (content.trim().length() < 20) {
            log().warn("Content very short for file: {} (length: {}). Processing anyway but extraction may be incomplete.", 
                      filename, content.length());
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
            log().warn("Agenda is empty for document: {}. Will try fallback from topics.", filename);
        } else {
            log().info("Extracted agenda with {} items for document: {}", agenda.size(), filename);
        }

        CompletableFuture<List<String>> decisionsFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_DECISIONS))
                .exceptionally(e -> {
                    log().error("Error extracting decisions for file: {}", filename, e);
                    return new ArrayList<>();
                });
        CompletableFuture<List<String>> entitiesFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_ENTITIES))
                .exceptionally(e -> {
                    log().error("Error extracting entities for file: {}", filename, e);
                    return new ArrayList<>();
                });
        CompletableFuture<List<String>> topicsFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_TOPICS))
                .exceptionally(e -> {
                    log().error("Error extracting topics for file: {}", filename, e);
                    return new ArrayList<>();
                });
        CompletableFuture<String> summaryFuture = 
            CompletableFuture.supplyAsync(() -> extractSummaryWithPrompt(content, PROMPT_SUMMARY))
                .exceptionally(e -> {
                    log().error("Error extracting summary for file: {}", filename, e);
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
                    .collect(Collectors.toList());
            Set<String> merged = new LinkedHashSet<>();
            merged.addAll(topics != null ? topics : List.of());
            merged.addAll(agendaKeys);
            topics = new ArrayList<>(merged);
        } else if ((agenda == null || agenda.isEmpty()) && topics != null && !topics.isEmpty()) {
            // Fallback: If agenda is empty but topics exist, create agenda from topics
            log().info("Agenda is empty for document: {}. Creating agenda from {} topics as fallback.", 
                      filename, topics != null ? topics.size() : 0);
            agenda = createAgendaFromTopics(topics);
            if (agenda != null) {
                log().info("Created agenda with {} items from topics for document: {}", agenda.size(), filename);
            }
        }
        
        if (date == null && place == null && attendees.isEmpty() && decisions.isEmpty() && (topics == null || topics.isEmpty())) {
            log().warn("Failed to extract critical fields from document: {} (date: {}, place: {}, attendees: {}, decisions: {}, topics: {})", 
                      filename, date != null, place != null, attendees.size(), decisions.size(), topics != null ? topics.size() : 0);
        }

        // Log the extracted fields
        log().info("Extracted fields for file: {} - Date: {}, Place: {}, Start Time: {}, End Time: {}, President: {}, Secretary: {}, Attendees: {}, Decisions: {}, Mentioned Entities: {}, Topics: {}, Summary: {}",
                      filename, date, place, startTime, endTime, president, secretary, attendees.size(), decisions.size(), 
                      mentionedEntities != null ? mentionedEntities.size() : 0, topics != null ? topics.size() : 0, summary);

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
                .filter(line -> !line.matches("(?i)^(?:aquí|here|below|estos|these|lista|list|a continuación|following).*"))
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
        if (prompt.contains("decisions") || prompt.contains("decided") || prompt.contains("agreed") || prompt.contains("approved")) {
            extracted = extractDecisionsWithRegex(content);
        } else if (prompt.contains("entities") || prompt.contains("companies") || prompt.contains("organizations")) {
            extracted = extractEntitiesWithRegex(content);
        } else if (prompt.contains("topics") || prompt.contains("discussed")) {
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
        
        // Pattern for common decision phrases in Spanish
        Pattern decisionPattern = Pattern.compile(
            "(?i)(?:se\\s+acordó|se\\s+decidió|se\\s+aprobó|se\\s+resolvió|se\\s+decide|se\\s+acuerda|se\\s+aprueba)[:.]?\\s*(.+?)(?:\\.|$|\\n)",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        
        Matcher matcher = decisionPattern.matcher(content);
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
        
        // Pattern for capitalized names (potential entities)
        Pattern entityPattern = Pattern.compile(
            "\\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)\\b",
            Pattern.MULTILINE
        );
        
        Matcher matcher = entityPattern.matcher(content);
        Set<String> uniqueEntities = new LinkedHashSet<>();
        
        while (matcher.find()) {
            String entity = matcher.group(1).trim();
            // Filter out common words and short entities
            if (entity.length() > 3 && 
                !entity.matches("(?i)(Fecha|Lugar|Hora|Asistentes|Orden|Día|Reunión|Acta|Presidente|Secretario)")) {
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
        
        // Extract from agenda section
        String agendaSection = extractBlock(content, "(?i)Orden del día:", "(?i)No habiendo más asuntos");
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
        for (String topic : commonTopics) {
            if (content.toLowerCase().contains(topic)) {
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
        String[] sentences = content.split("[.!?]");
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
        if (metadata.containsKey("date") && !metadata.containsKey("date_iso")) {
            String dateValue = metadata.get("date") != null ? metadata.get("date").toString() : "null";
            log().warn("date_iso was not generated for document with date: {}. This may cause date filtering issues.", dateValue);
        } else if (metadata.containsKey("date_iso")) {
            log().debug("date_iso successfully generated: {}", metadata.get("date_iso"));
        }
        
        warnIfMissingOptionalContent(metadata);
        validateSignalPresence(metadata, minute.filename());
        
        try {
            String minuteJson = objectMapper.writeValueAsString(minute);
            metadata.put("minute", minuteJson);
            log().info("Minute object stored in metadata as JSON for document: {}", minute.id());
        } catch (Exception e) {
            log().warn("Failed to serialize Minute object to JSON for document: {}. Error: {}", 
                      minute.id(), e.getMessage());
        }
        
        log().info("Metadata extracted for document: {} with {} fields (document_id: {})", 
                  minute.id(), metadata.size(), minute.id());
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
                .collect(java.util.stream.Collectors.toList());
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
        // date_iso, year, month
        Object dateObj = metadata.get("date");
        if (dateObj instanceof String) {
            String dateStr = (String) dateObj;
            // parseDateToLocalDate now handles case normalization internally
            LocalDate parsed = parseDateToLocalDate(dateStr);
            if (parsed != null) {
                metadata.put("date_iso", parsed.format(DateTimeFormatter.ISO_LOCAL_DATE));
                metadata.put("year", parsed.getYear());
                metadata.put("month", parsed.getMonthValue());
            } else {
                log().warn("Could not parse date '{}' to generate date_iso field. Date will not be searchable by ISO format.", dateStr);
            }
        }

        // attendeesCount
        int attendeesCount = 0;
        Object numberOfAttendees = metadata.get("numberOfAttendees");
        if (numberOfAttendees instanceof Number) {
            attendeesCount = ((Number) numberOfAttendees).intValue();
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
            log().warn("Agenda is empty for document: {}", metadata.get("filename"));
        }
        if (decisions.isEmpty()) {
            log().warn("Decisions are empty for document: {}", metadata.get("filename"));
        }
        if (topics.isEmpty()) {
            log().warn("Topics are empty for document: {}", metadata.get("filename"));
        }
    }

    /**
     * Validates that the document has at least one key signal to avoid empty shells.
     */
    private void validateSignalPresence(Map<String, Object> metadata, String filename) {
        boolean hasDate = isNotBlank(metadata.get("date"));
        boolean hasPlace = isNotBlank(metadata.get("place"));
        boolean hasSummary = isNotBlank(metadata.get("summary"));
        boolean hasTopics = metadata.get("topics") instanceof List<?> l && !l.isEmpty();
        boolean hasDecisions = metadata.get("decisions") instanceof List<?> l2 && !l2.isEmpty();

        if (!hasDate && !hasPlace && !hasSummary && !hasTopics && !hasDecisions) {
            throw new IllegalArgumentException("Document " + filename + " has no meaningful signal (date/place/summary/topics/decisions)");
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
            LocalTime s = LocalTime.parse(startNormalized, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime e = LocalTime.parse(endNormalized, DateTimeFormatter.ofPattern("HH:mm"));
            
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
                LocalTime s = LocalTime.parse(startNormalized, DateTimeFormatter.ofPattern("HH:mm:ss"));
                LocalTime e = LocalTime.parse(endNormalized, DateTimeFormatter.ofPattern("HH:mm:ss"));
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
            LocalTime time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
            return time.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            // Try HH:mm:ss format
            try {
                LocalTime time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm:ss"));
                return time.format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ignored2) {
                // Try H:mm format (single digit hour)
                try {
                    LocalTime time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
                    return time.format(DateTimeFormatter.ofPattern("HH:mm"));
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
        
        // Enhanced logging: show which formatters were tried
        log().warn("Could not parse date '{}' (normalized to '{}') with any of {} formatters. " +
                  "This may prevent date_iso generation and cause date filtering issues.", 
                  dateStr, v, formatters.size());
        return null;
    }

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
            if (date.matches("(?i)\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}")) {
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
            return date != null ? date.toLowerCase() : null;
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
                
                String[] monthNames = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                                       "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
                
                if (month >= 1 && month <= 12) {
                    return String.format("%d de %s de %d", day, monthNames[month - 1], year);
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
                
                String[] monthNames = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                                       "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
                
                if (month >= 1 && month <= 12) {
                    return String.format("%d de %s de %d", day, monthNames[month - 1], year);
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
        // Stop at: Hora, Hora de inicio, Hora de finalización, or time pattern (HH:MM)
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
     * Extracts the time with multiple supported formats.
     * @param content Content of the document
     * @param isStartTime true for start time, false for end time
     */
    private String extractTime(String content, boolean isStartTime) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Define possible labels according to the type of time
        String[] startLabels = {"inicio", "comienzo", "comienza"};
        String[] endLabels = {"fin", "finalización", "finaliza", "termina", "clausura"};
        String[] labels = isStartTime ? startLabels : endLabels;
        
        // Possible time patterns (including space after colon: "19: 00" and optional space before h: "19:00 h" or "19:00h")
        String[] timePatterns = {
            "(\\d{1,2}:\\s*\\d{2})",                    // 19:00 or 19: 00 (with space)
            "(\\d{1,2}:\\s*\\d{2})\\s*[hH]",            // 19:00 h, 19:00h, 19: 00 h, 19: 00h (space optional before h)
            "(\\d{1,2}\\.\\d{2})",                      // 19.00
            "(\\d{1,2})\\s*[hH]",                       // 19 h or 19h
            "(\\d{1,2})\\s*[hH]\\s*(\\d{2})?"           // 19 h 30 or 19h30 (partial)
        };
        
        // Try each combination of label and pattern
        for (String label : labels) {
            for (String timePattern : timePatterns) {
                // Pattern 1: "Hora de inicio: 19:00"
                String regex = String.format("(?i)Hora de %s:\\s*%s", label, timePattern);
                String time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
                
                // Pattern 2: "Hora inicio: 19:00" (without "de")
                regex = String.format("(?i)Hora %s:\\s*%s", label, timePattern);
                time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
                
                // Pattern 3: "Inicio: 19:00" (only label)
                String labelCapitalized = label.substring(0, 1).toUpperCase() + label.substring(1);
                regex = String.format("(?i)%s:\\s*%s", labelCapitalized, timePattern);
                time = extractSingle(content, regex);
                if (time != null) {
                    return normalizeTime(time.replace(".", ":"));
                }
            }
        }
        
        // Additional pattern: Search in header context (similar to place)
        String header = extractBlock(content, "(?i)^(?:ACTA|REUNIÓN)", "(?i)Asistentes:");
        if (header != null) {
            for (String label : labels) {
                for (String timePattern : timePatterns) {
                    String regex = String.format("(?i)(?:Hora de |Hora )?%s[^:]*:\\s*%s", label, timePattern);
                    String time = extractSingle(header, regex);
                    if (time != null) {
                        return normalizeTime(time.replace(".", ":"));
                    }
                }
            }
        }
        
        // Final pattern: Search any time in HH:MM format (with optional space) near keywords
        String keyword = isStartTime ? "inicio|comienzo|comienza" : "fin|final|termina|clausura";
        String time = extractSingle(content, 
            String.format("(?i)(?:%s)[^:]*:\\s*(\\d{1,2}:\\s*\\d{2})", keyword));
        if (time != null) {
            return normalizeTime(time);
        }
        
        // Additional pattern: Search format "19:00 - 20:30" or "19:00 a 20:30" (with optional space)
        if (isStartTime) {
            time = extractSingle(content, "(?i)(\\d{1,2}:\\s*\\d{2})\\s*[-a]\\s*\\d{1,2}:\\s*\\d{2}");
            if (time != null) {
                return normalizeTime(time);
            }
        } else {
            time = extractSingle(content, "(?i)\\d{1,2}:\\s*\\d{2}\\s*[-a]\\s*(\\d{1,2}:\\s*\\d{2})");
            if (time != null) {
                return normalizeTime(time);
            }
        }
        
        // Additional very flexible pattern: Search any time in HH:MM format (with optional space) in the first lines
        String firstLines = content.length() > 500 ? content.substring(0, 500) : content;
        Pattern timePattern = Pattern.compile("(\\d{1,2}:\\s*\\d{2})");
        Matcher matcher = timePattern.matcher(firstLines);
        List<String> foundTimes = new ArrayList<>();
        while (matcher.find() && foundTimes.size() < 3) {
            String foundTime = matcher.group(1);
            if (normalizeTime(foundTime) != null) {
                foundTimes.add(foundTime);
            }
        }
        
        // If we find times, use the first for start and the last for end
        if (!foundTimes.isEmpty()) {
            if (isStartTime && foundTimes.size() >= 1) {
                return normalizeTime(foundTimes.get(0));
            } else if (!isStartTime && foundTimes.size() >= 2) {
                return normalizeTime(foundTimes.get(foundTimes.size() - 1));
            } else if (!isStartTime && foundTimes.size() == 1) {
                // If there is only one time and it is end, it may be the end time
                return normalizeTime(foundTimes.get(0));
            }
        }
        
        // If not found, it is not critical - times are optional
        // Changed to debug to avoid noise in logs
        log().info("Could not extract the time of {} from the document (this is optional and does not affect functionality)", 
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
        time = time.trim()
                .replaceAll("\\s*h\\s*$", "")  // Remove "h" at the end
                .replaceAll("\\s*H\\s*$", "")  // Remover "H" al final
                .replaceAll("\\.", ":")        // Convert 19.00 to 19:00
                .replaceAll(":\\s+", ":")      // Remove space after colon: "19: 00" -> "19:00"
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
        String numbersOnly = time.replaceAll("[^0-9]", "");
        if (numbersOnly.length() >= 3 && numbersOnly.length() <= 4) {
            // Format HHMM or HMM
            try {
                int hour, minute;
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
    
    /**
     * Improved attendees extraction with multiple fallback methods.
     */
    private List<String> extractAttendees(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> attendees = new ArrayList<>();
        
        // Method 1: Between "Asistentes:" and "Orden del día:" (actual)
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
        
        // Method 3: Search lines that begin with bullet after "Asistentes:" (even if there's text before)
        if (attendees.isEmpty()) {
            Pattern pattern = Pattern.compile(
                "(?i)Asistentes:.*?\\n((?:•|[-*]|\\d+\\.)\\s*[^\\n]+(?:\\n(?!•|[-*]|\\d+\\.|Orden|Ruegos|Clausura)[^\\n]+)*)", 
                Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String attendeesBlock = matcher.group(1);
                attendees.addAll(extractBullets(attendeesBlock));
            }
        }
        
        // Clean and normalize names
        attendees = attendees.stream()
            .map(name -> name.trim())
            .filter(name -> !name.isEmpty())
            .filter(name -> name.length() > 2)
            // Remove roles between parentheses (extracted separately)
            .map(name -> name.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim())
            .filter(name -> !name.isEmpty())
            // Filter out descriptive text that might have been captured
            .filter(name -> !name.toLowerCase().matches(".*(cuenta|asistencia|propietarios|lista|firmada|reunión|quórum|suficiente|validez|acuerdos|tomados|declara).*"))
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
        
        log().info("Extracted {} attendees: {}", attendees.size(), attendees);
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
     * Example: "Asistentes: Juan Pérez, Marta González, Luis Ramírez"
     */
    private List<String> extractCommaSeparatedNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return names;
        }
        
        // Look for pattern: ":" followed by names separated by commas
        Pattern pattern = Pattern.compile(":\\s*([^\\n]+?)(?=\\n|$)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String namesText = matcher.group(1).trim();
            // Split by comma
            String[] nameParts = namesText.split(",");
            for (String name : nameParts) {
                name = name.trim();
                // Filter out descriptive text
                if (!name.isEmpty() && name.length() > 2 && 
                    !name.toLowerCase().matches(".*(cuenta|asistencia|propietarios|lista|firmada|reunión|quórum|suficiente|validez|acuerdos|tomados|declara).*")) {
                    names.add(name);
                }
            }
        }
        
        return names;
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
                    if (i == 0 && (part.isEmpty() || part.length() < 3 || 
                        part.toLowerCase().matches(".*(cuenta|asistencia|propietarios|lista|firmada|reunión|quórum|suficiente|validez|acuerdos|tomados|declara).*"))) {
                        continue;
                    }
                    // Remove leading/trailing list symbols and whitespace
                    part = part.replaceAll("^[•\\-*\\d\\.\\s]+", "").replaceAll("[•\\-*\\d\\.\\s]+$", "").trim();
                    if (!part.isEmpty() && part.length() > 2) {
                        // Remove roles in parentheses (extracted separately)
                        part = part.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
                        // Filter out descriptive text
                        if (!part.isEmpty() && !part.toLowerCase().matches(".*(cuenta|asistencia|propietarios|lista|firmada|reunión|quórum|suficiente|validez|acuerdos|tomados|declara).*")) {
                            items.add(part);
                        }
                    }
                }
            }
        }
        
        // Method 2: Pattern-based extraction (one item per line or separated by newlines)
        if (items.isEmpty()) {
            // Pattern for bullet points (•)
            Pattern pattern1 = Pattern.compile("•\\s*([^•\\n]+?)(?=\\s*•|\\n|$)", Pattern.MULTILINE);
            Matcher matcher1 = pattern1.matcher(text);
            while (matcher1.find()) {
                String item = matcher1.group(1).trim();
                item = item.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
                if (!item.isEmpty() && item.length() > 2) {
                    items.add(item);
                }
            }
            
            // Pattern for dashes (-)
            if (items.isEmpty()) {
                Pattern pattern2 = Pattern.compile("(?:^|\\n)\\s*-\\s*([^\\n\\-]+?)(?=\\s*-|\\n|$)", Pattern.MULTILINE);
                Matcher matcher2 = pattern2.matcher(text);
                while (matcher2.find()) {
                    String item = matcher2.group(1).trim();
                    item = item.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
                    if (!item.isEmpty() && item.length() > 2) {
                        items.add(item);
                    }
                }
            }
            
            // Pattern for asterisks (*)
            if (items.isEmpty()) {
                Pattern pattern3 = Pattern.compile("(?:^|\\n)\\s*\\*\\s*([^\\n\\*]+?)(?=\\s*\\*|\\n|$)", Pattern.MULTILINE);
                Matcher matcher3 = pattern3.matcher(text);
                while (matcher3.find()) {
                    String item = matcher3.group(1).trim();
                    item = item.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
                    if (!item.isEmpty() && item.length() > 2) {
                        items.add(item);
                    }
                }
            }
            
            // Pattern for numbered lists (1., 2., etc.)
            if (items.isEmpty()) {
                Pattern pattern4 = Pattern.compile("\\d+\\.\\s*([^\\n]+?)(?=\\n\\d+\\.|\\n|$)", Pattern.MULTILINE);
                Matcher matcher4 = pattern4.matcher(text);
                while (matcher4.find()) {
                    String item = matcher4.group(1).trim();
                    item = item.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim();
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
            .collect(Collectors.toList());
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
        if (numberCount == maxCount) return "1."; // Represent numbered lists
        
        return null;
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
        
        // Divide into main items
        String[] lines = agendaBlock.split("\\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Detect start of new item (bullet, number, or line starting with capital letter)
            if (line.matches("^[•·▪▫◦‣⁃*\\-]\\s+.+") || 
                line.matches("^\\d+[.)]\\s+.+") ||
                (line.matches("^[A-ZÁÉÍÓÚÑ].+") && currentKey == null)) {
                
                // Save previous item
                if (currentKey != null && currentValue.length() > 0) {
                    agenda.put(currentKey, currentValue.toString().trim());
                    currentValue.setLength(0);
                }
                
                // Extract key of new item (main item or sub-item)
                currentKey = extractAgendaItemKey(line);
            } else if (currentKey != null) {
                // Continuation of current item (sub-item or description)
                if (!line.matches("(?i)^(?:Ruegos|No habiendo|Clausura).*")) {
                    currentValue.append(line).append(" ");
                }
            }
        }
        
        // Guardar último item
        if (currentKey != null && currentValue.length() > 0) {
            agenda.put(currentKey, currentValue.toString().trim());
        }
        
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
        
        // Pattern 1: "Orden del día:" until "Ruegos y preguntas" or "No habiendo más asuntos"
        Pattern pattern = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*?)(?=\\n\\s*(?:Ruegos y preguntas|No habiendo más asuntos|Clausura|$))",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty()) {
                return block;
            }
        }
        
        // Pattern 2: "Orden del día:" until any next section (more flexible)
        pattern = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*?)(?=\\n\\s*(?:Ruegos|Preguntas|Clausura|No habiendo|Asistentes|$))",
            Pattern.DOTALL
        );
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty()) {
                return block;
            }
        }
        
        // Pattern 3: "ORDEN DEL DÍA" (capital letters) until next section
        pattern = Pattern.compile(
            "(?i)ORDEN DEL DÍA:?\\s*(.*?)(?=\\n\\s*(?:Ruegos|Preguntas|Clausura|No habiendo|$))",
            Pattern.DOTALL
        );
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty()) {
                return block;
            }
        }
        
        // Pattern 4: Search for section that contains numbered or bulleted items after "Asistentes:"
        String afterAttendees = extractBlock(content, "(?i)Asistentes:", "(?i)(?:Ruegos|Preguntas|Clausura|No habiendo|$)");
        if (afterAttendees != null && !afterAttendees.trim().isEmpty()) {
            // Search for items that seem to be agenda (numbered or with bullet points)
            Pattern agendaPattern = Pattern.compile(
                "(?i)(?:Orden del día:?\\s*)?((?:[•·▪▫◦‣⁃*\\-]|\\d+[.)])\\s*[^\\n]+(?:\\n(?!Ruegos|Preguntas|Clausura|No habiendo)[^\\n]+)*)",
                Pattern.DOTALL
            );
            Matcher agendaMatcher = agendaPattern.matcher(afterAttendees);
            if (agendaMatcher.find()) {
                String block = agendaMatcher.group(1).trim();
                if (!block.isEmpty() && block.length() > 10) {
                    return block;
                }
            }
        }
        
        // Pattern 5: Search for any block with numbered or bulleted items that seems to be agenda
        pattern = Pattern.compile(
            "(?i)(?:Orden|Agenda|Puntos):?\\s*((?:[•·▪▫◦‣⁃*\\-]|\\d+[.)])\\s*[^\\n]+(?:\\n(?!Ruegos|Preguntas|Clausura|Asistentes|No habiendo)[^\\n]+)*)",
            Pattern.DOTALL
        );
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            String block = matcher.group(1).trim();
            if (!block.isEmpty() && block.length() > 10) {
                return block;
            }
        }
        
        return null;
    }
    
    /**
     * Extrae la clave de un item de agenda, limpiando viñetas y numeración.
     */
    private String extractAgendaItemKey(String line) {
        // Quitar viñetas y numeración
        line = line.replaceAll("^[•·▪▫◦‣⁃*\\-]\\s*", "")
                   .replaceAll("^\\d+[.)]\\s*", "")
                   .trim();
        
        // Tomar solo la primera parte (hasta dos puntos o punto final)
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
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractBlock(String text, String startRegex, String endRegex) {
        Pattern pattern = Pattern.compile(startRegex + "(.*?)" + endRegex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
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