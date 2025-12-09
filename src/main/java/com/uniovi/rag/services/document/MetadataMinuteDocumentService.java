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
        String secretary = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
        List<String> attendees = extractAttendees(content);
        Map<String, String> agenda = extractAgendaMap(content);

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
        }
        
        if (date == null && place == null && attendees.isEmpty() && decisions.isEmpty() && topics.isEmpty()) {
            log().warn("Failed to extract critical fields from document: {} (date: {}, place: {}, attendees: {}, decisions: {}, topics: {})", 
                      filename, date != null, place != null, attendees.size(), decisions.size(), topics.size());
        }

        // Log the extracted fields
        log().info("Extracted fields for file: {} - Date: {}, Place: {}, Start Time: {}, End Time: {}, President: {}, Secretary: {}, Attendees: {}, Decisions: {}, Mentioned Entities: {}, Topics: {}, Summary: {}",
                      filename, date, place, startTime, endTime, president, secretary, attendees.size(), decisions.size(), mentionedEntities.size(), topics.size(), summary);

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
        
        try {
            String rawResponse = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT_LINE_DATA)
                    .user(prompt + "\nTexto del acta:\n" + content)
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
            log().error("Error extracting information with LLM prompt, trying regex fallback", e);
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
        
        try {
            String summary = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT_SUMMARY)
                    .user(prompt + "\nTexto del acta:\n" + content)
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
            log().error("Error extracting summary with LLM, using fallback", e);
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
            LocalDate parsed = parseDateToLocalDate((String) dateObj);
            if (parsed != null) {
                metadata.put("date_iso", parsed.format(DateTimeFormatter.ISO_LOCAL_DATE));
                metadata.put("year", parsed.getYear());
                metadata.put("month", parsed.getMonthValue());
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
        if (start == null || end == null) {
            return null;
        }
        try {
            LocalTime s = LocalTime.parse(start, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime e = LocalTime.parse(end, DateTimeFormatter.ofPattern("HH:mm"));
            int diff = (e.getHour() * 60 + e.getMinute()) - (s.getHour() * 60 + s.getMinute());
            return diff >= 0 ? diff : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
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
     * Normalizes the date to standard format "DD de MES de YYYY".
     */
    private String normalizeDate(String date) {
        if (date == null) return null;
        
        try {
            // If already in format "day of month of year", only capitalize
            if (date.matches("(?i)\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}")) {
                return capitalizeDate(date);
            }
            
            // If in numeric format, convert
            if (date.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) {
                return convertNumericDate(date);
            }
            
            if (date.matches("\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}")) {
                return convertISODate(date);
            }
            
            return date; // Return original if not possible to normalize
        } catch (Exception e) {
            log().warn("Error normalizing date: {}", date, e);
            return date;
        }
    }
    
    private String capitalizeDate(String date) {
        String[] months = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                         "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        
        String lower = date.toLowerCase();
        for (String month : months) {
            if (lower.contains(month)) {
                String capitalized = month.substring(0, 1).toUpperCase() + month.substring(1);
                return date.replaceAll("(?i)" + month, capitalized);
            }
        }
        return date;
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
        
        // Pattern 1: "Lugar: [lugar]" in a single line (limited to 200 characters)
        String place = extractSingle(content, "(?i)Lugar:\\s*([^\\n]{1,200})");
        if (place != null && !place.trim().isEmpty()) {
            place = place.trim().replaceAll("\\.$", "").trim();
            if (place.length() > 5 && place.length() < 200) {
                return place;
            }
        }
        
        // Pattern 2: Search in header context
        String header = extractBlock(content, "(?i)^(?:ACTA|REUNIÓN)", "(?i)Asistentes:");
        if (header != null) {
            place = extractSingle(header, "(?i)Lugar[^:]*:\\s*([^\\n]{1,200})");
            if (place != null && place.length() > 5 && place.length() < 200) {
                return place.trim().replaceAll("\\.$", "").trim();
            }
        }
        
        // Pattern 3: Place label variants
        String[] placeLabels = {"Lugar de celebración", "Sitio", "Ubicación", "Localización", "Lugar"};
        for (String label : placeLabels) {
            place = extractSingle(content, String.format("(?i)%s[^:]*:\\s*([^\\n]{1,200})", label));
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
        
        // Possible time patterns
        String[] timePatterns = {
            "(\\d{1,2}:\\d{2})",           // 19:00
            "(\\d{1,2}:\\d{2})\\s*h",      // 19:00 h
            "(\\d{1,2}\\.\\d{2})",         // 19.00
            "(\\d{1,2}:\\d{2})\\s*[hH]",  // 19:00 H
            "(\\d{1,2})\\s*[hH]",         // 19 h
            "(\\d{1,2})\\s*[hH]\\s*(\\d{2})?" // 19 h 30 (partial)
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
        
        // Final pattern: Search any time in HH:MM format near keywords
        String keyword = isStartTime ? "inicio|comienzo|comienza" : "fin|final|termina|clausura";
        String time = extractSingle(content, 
            String.format("(?i)(?:%s)[^:]*:\\s*(\\d{1,2}:\\d{2})", keyword));
        if (time != null) {
            return normalizeTime(time);
        }
        
        // Additional pattern: Search format "19:00 - 20:30" or "19:00 a 20:30" and extract the corresponding time
        if (isStartTime) {
            time = extractSingle(content, "(?i)(\\d{1,2}:\\d{2})\\s*[-a]\\s*\\d{1,2}:\\d{2}");
            if (time != null) {
                return normalizeTime(time);
            }
        } else {
            time = extractSingle(content, "(?i)\\d{1,2}:\\d{2}\\s*[-a]\\s*(\\d{1,2}:\\d{2})");
            if (time != null) {
                return normalizeTime(time);
            }
        }
        
        // Additional very flexible pattern: Search any time in HH:MM format in the first lines
        String firstLines = content.length() > 500 ? content.substring(0, 500) : content;
        Pattern timePattern = Pattern.compile("(\\d{1,2}:\\d{2})");
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
        log().info("No se pudo extraer la hora de {} del documento (esto es opcional y no afecta el funcionamiento)", 
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
        
        // Try to extract time from HH:MM or H:MM format
        if (time.matches("\\d{1,2}:\\d{2}")) {
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
            attendees.addAll(extractBullets(block));
        }
        
        // Method 2: If not found, search until finding another section
        if (attendees.isEmpty()) {
            block = extractBlock(content, "(?i)Asistentes:", "(?i)(?:Orden del día|Ruegos|Clausura|No habiendo)");
            if (block != null && !block.trim().isEmpty()) {
                attendees.addAll(extractBullets(block));
            }
        }
        
        // Method 3: Search lines that begin with bullet after "Asistentes:"
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
            .distinct()
            .collect(Collectors.toList());
        
        log().info("Extracted {} attendees: {}", attendees.size(), attendees);
        return attendees;
    }

    /**
     * Extraction of bullets with multiple supported formats.
     */
    private List<String> extractBullets(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }
        
        // Pattern 1: Standard bullets (•)
        Pattern pattern1 = Pattern.compile("•\\s*(.+?)(?=\\n(?:•|\\s*$|Orden|Ruegos|Clausura))", Pattern.MULTILINE);
        Matcher matcher1 = pattern1.matcher(text);
        while (matcher1.find()) {
            String item = matcher1.group(1).trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        
        // Pattern 2: Alternative bullets (-, *, numbers) if not found with •
        if (items.isEmpty()) {
            Pattern pattern2 = Pattern.compile("(?:[-*]|\\d+\\.)\\s*(.+?)(?=\\n(?:[-*]|\\d+\\.|\\s*$|Orden|Ruegos|Clausura))", Pattern.MULTILINE);
            Matcher matcher2 = pattern2.matcher(text);
            while (matcher2.find()) {
                String item = matcher2.group(1).trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }
        
        return items;
    }

    /**
     * Improved agenda extraction with support for sub-items and multiple formats.
     */
    private Map<String, String> extractAgendaMap(String content) {
        Map<String, String> agenda = new LinkedHashMap<>();
        if (content == null || content.trim().isEmpty()) {
            return agenda;
        }
        
        // Extract complete agenda block
        String agendaBlock = extractAgendaBlock(content);
        if (agendaBlock == null || agendaBlock.trim().isEmpty()) {
            // Not critical - the agenda is optional and may be in other formats
            log().info("No found agenda block (this is optional and does not affect functionality)");
            return agenda;
        }
        
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
}