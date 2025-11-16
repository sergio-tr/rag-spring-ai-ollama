package com.uniovi.rag.services.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.model.Minute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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


    public MetadataMinuteDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        super(vectorStore, chatClient);
    }

    @Override
    protected Minute extractModel(String content, String filename) {
        // Extract structured fields with regex (fast, sequential)
        String date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2} de [a-záéíóú]+ de \\d{4})");
        String place = extractSingle(content, "(?i)Lugar:\\s*(.+)");
        String startTime = extractSingle(content, "(?i)Hora de inicio:\\s*(\\d{1,2}:\\d{2})");
        String endTime = extractSingle(content, "(?i)Hora de finalización:\\s*(\\d{1,2}:\\d{2})");
        String president = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
        String secretary = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
        List<String> attendees = extractBullets(extractBlock(content, "(?i)Asistentes:\\s*", "(?i)Orden del día:"));
        Map<String, String> agenda = extractAgendaMap(content);

        // MEJORA 3: Parallelize LLM-assisted extraction for better performance
        // All 4 LLM calls can run in parallel, reducing total extraction time by ~75%
        CompletableFuture<List<String>> decisionsFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_DECISIONS));
        CompletableFuture<List<String>> entitiesFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_ENTITIES));
        CompletableFuture<List<String>> topicsFuture = 
            CompletableFuture.supplyAsync(() -> extractWithPrompt(content, PROMPT_TOPICS));
        CompletableFuture<String> summaryFuture = 
            CompletableFuture.supplyAsync(() -> extractSummaryWithPrompt(content, PROMPT_SUMMARY));

        // Wait for all extractions to complete
        List<String> decisions = decisionsFuture.join();
        List<String> mentionedEntities = entitiesFuture.join();
        List<String> topics = topicsFuture.join();
        String summary = summaryFuture.join();

        return new Minute(
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
        );
    }


    /**
     * Extracts information using LLM with error handling and fallback to regex.
     * Returns empty list if both LLM and regex extraction fail.
     * 
     * MEJORA 4: Cached extraction to avoid re-extracting same content.
     * Cache key is based on content hash and prompt hash.
     */
    @org.springframework.cache.annotation.Cacheable(
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

            List<String> extracted = Arrays.stream(rawResponse.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            
            if (extracted.size() < 2 && content.length() > 500) {
                log().warn("LLM extraction returned very few items ({}), trying regex fallback", extracted.size());
                List<String> regexExtracted = extractWithRegexFallback(content, prompt);
                if (regexExtracted.size() > extracted.size()) {
                    log().info("Regex fallback found more items ({} vs {}), using regex results", regexExtracted.size(), extracted.size());
                    return regexExtracted;
                }
            }
            
            log().debug("Extracted {} items using LLM prompt", extracted.size());
            return extracted;
        } catch (Exception e) {
            log().error("Error extracting information with LLM prompt, trying regex fallback", e);
            return extractWithRegexFallback(content, prompt);
        }
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
            log().warn("Regex fallback also failed to extract items");
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
     * MEJORA 4: Cached extraction to avoid re-extracting same content.
     * Cache key is based on content hash.
     */
    @org.springframework.cache.annotation.Cacheable(
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
            log().debug("Extracted summary with {} characters", trimmed.length());
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
        
        // MEJORA 1: Store document_id to identify chunks from the same document
        // This allows grouping chunks by document and avoiding duplicate processing
        metadata.put("document_id", minute.id());
        
        // Store individual fields for direct access
        metadata.put("id", minute.id());
        metadata.put("filename", minute.filename());
        metadata.put("date", minute.date());
        metadata.put("place", minute.place());
        metadata.put("startTime", minute.startTime());
        metadata.put("endTime", minute.endTime());
        metadata.put("president", minute.president());
        metadata.put("secretary", minute.secretary());
        metadata.put("attendees", minute.attendees());
        metadata.put("numberOfAttendees", minute.numberOfAttendees());
        metadata.put("topics", minute.topics());
        metadata.put("decisions", minute.decisions());
        metadata.put("mentionedEntities", minute.mentionedEntities());
        metadata.put("summary", minute.summary());
        metadata.put("agenda", minute.agenda()); // Store agenda map for consistency with NER
        
        try {
            String minuteJson = objectMapper.writeValueAsString(minute);
            metadata.put("minute", minuteJson);
            log().debug("Minute object stored in metadata as JSON for document: {}", minute.id());
        } catch (Exception e) {
            log().warn("Failed to serialize Minute object to JSON for document: {}", minute.id(), e);
            // Continue without the complete object - tools can reconstruct from individual fields
        }
        
        log().info("Metadata extracted for document: {} with {} fields (document_id: {})", 
                  minute.id(), metadata.size(), minute.id());
        return metadata;
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

    private List<String> extractBullets(String text) {
        List<String> items = new ArrayList<>();
        Matcher matcher = Pattern.compile("•\\s*(.+)").matcher(text);
        while (matcher.find()) {
            items.add(matcher.group(1).trim());
        }
        return items;
    }

    private Map<String, String> extractAgendaMap(String content) {
        Map<String, String> agenda = new LinkedHashMap<>();
        String[] lines = content.split("\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        boolean inAgenda = false;

        for (String line : lines) {
            line = line.trim();
            if (line.matches("(?i)^Orden del día:?$")) {
                inAgenda = true;
                continue;
            }
            if (inAgenda) {
                if (line.startsWith("•")) {
                    if (currentKey != null) {
                        agenda.put(currentKey, currentValue.toString().trim());
                        currentValue.setLength(0);
                    }
                    currentKey = line.substring(1).trim();
                } else if (!line.isEmpty()) {
                    currentValue.append(line).append(" ");
                }
                if (line.matches("(?i)^No habiendo más asuntos.*")) {
                    break;
                }
            }
        }

        if (currentKey != null) {
            agenda.put(currentKey, currentValue.toString().trim());
        }

        return agenda;
    }
}