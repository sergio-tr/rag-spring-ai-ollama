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
        // MEJORA: Extracción mejorada con múltiples formatos y fallbacks
        String date = extractDate(content);
        String place = extractPlace(content);
        String startTime = extractStartTime(content);
        String endTime = extractEndTime(content);
        String president = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
        String secretary = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
        List<String> attendees = extractAttendees(content);
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

    /**
     * MEJORA: Extracción de fecha con múltiples formatos soportados.
     * Acepta: "día de mes de año", "día/mes/año", "día-mes-año", "año-mes-día"
     */
    private String extractDate(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Patrón 1: "25 de agosto de 2026" (formato actual, con mayúsculas/minúsculas flexibles)
        String date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Patrón 2: "25/08/2026" o "25-08-2026"
        date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Patrón 3: "2026-08-25" (formato ISO)
        date = extractSingle(content, "(?i)Fecha:\\s*(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        // Patrón 4: Sin etiqueta "Fecha:", buscar en encabezado
        date = extractSingle(content, "(?i)^(?:ACTA|ACTA DE REUNIÓN|REUNIÓN).*?(\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4})");
        if (date != null) {
            return normalizeDate(date);
        }
        
        log().warn("No se pudo extraer la fecha del documento");
        return null;
    }
    
    /**
     * Normaliza la fecha a formato estándar "DD de MES de YYYY".
     */
    private String normalizeDate(String date) {
        if (date == null) return null;
        
        try {
            // Si ya está en formato "día de mes de año", solo normalizar mayúsculas
            if (date.matches("(?i)\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}")) {
                return capitalizeDate(date);
            }
            
            // Si está en formato numérico, convertir
            if (date.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) {
                return convertNumericDate(date);
            }
            
            if (date.matches("\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}")) {
                return convertISODate(date);
            }
            
            return date; // Devolver original si no se puede normalizar
        } catch (Exception e) {
            log().warn("Error normalizando fecha: {}", date, e);
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
            log().warn("Error convirtiendo fecha numérica: {}", date, e);
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
            log().warn("Error convirtiendo fecha ISO: {}", date, e);
        }
        return date;
    }
    
    /**
     * MEJORA: Extracción mejorada de lugar con validación de longitud.
     */
    private String extractPlace(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Patrón 1: "Lugar: [lugar]" en una sola línea (limitado a 200 caracteres)
        String place = extractSingle(content, "(?i)Lugar:\\s*([^\\n]{1,200})");
        if (place != null && !place.trim().isEmpty()) {
            place = place.trim().replaceAll("\\.$", "").trim();
            if (place.length() > 5 && place.length() < 200) {
                return place;
            }
        }
        
        // Patrón 2: Buscar en contexto de encabezado
        String header = extractBlock(content, "(?i)^(?:ACTA|REUNIÓN)", "(?i)Asistentes:");
        if (header != null) {
            place = extractSingle(header, "(?i)Lugar[^:]*:\\s*([^\\n]{1,200})");
            if (place != null && place.length() > 5 && place.length() < 200) {
                return place.trim().replaceAll("\\.$", "").trim();
            }
        }
        
        log().warn("No se pudo extraer el lugar del documento");
        return null;
    }
    
    /**
     * MEJORA: Extracción de hora de inicio con múltiples formatos.
     */
    private String extractStartTime(String content) {
        return extractTime(content, "inicio");
    }
    
    /**
     * MEJORA: Extracción de hora de finalización con múltiples formatos.
     */
    private String extractEndTime(String content) {
        return extractTime(content, "finalización|fin");
    }
    
    /**
     * Extrae la hora con múltiples formatos soportados.
     */
    private String extractTime(String content, String timeType) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Patrón 1: "Hora de inicio: 19:00" (formato actual)
        String time = extractSingle(content, 
            String.format("(?i)Hora de %s:\\s*(\\d{1,2}:\\d{2})", timeType));
        if (time != null) {
            return normalizeTime(time);
        }
        
        // Patrón 2: "Hora de inicio: 19:00 h"
        time = extractSingle(content, 
            String.format("(?i)Hora de %s:\\s*(\\d{1,2}:\\d{2})\\s*h", timeType));
        if (time != null) {
            return normalizeTime(time);
        }
        
        // Patrón 3: "Hora de inicio: 19.00"
        time = extractSingle(content, 
            String.format("(?i)Hora de %s:\\s*(\\d{1,2}\\.\\d{2})", timeType));
        if (time != null) {
            return normalizeTime(time.replace(".", ":"));
        }
        
        // Patrón 4: "Inicio: 19:00" (sin "Hora de")
        String timeLabel = timeType.contains("inicio") ? "Inicio" : "Fin";
        time = extractSingle(content, 
            String.format("(?i)%s:\\s*(\\d{1,2}:\\d{2})", timeLabel));
        if (time != null) {
            return normalizeTime(time);
        }
        
        log().warn("No se pudo extraer la hora de {} del documento", timeType);
        return null;
    }
    
    /**
     * Normaliza la hora al formato HH:MM estándar.
     */
    private String normalizeTime(String time) {
        if (time == null) return null;
        
        // Asegurar formato HH:MM
        time = time.trim().replaceAll("\\s*h\\s*$", "").trim();
        
        if (time.matches("\\d{1,2}:\\d{2}")) {
            try {
                String[] parts = time.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                
                // Validar rango
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    return String.format("%02d:%02d", hour, minute);
                }
            } catch (NumberFormatException e) {
                log().warn("Error parseando hora: {}", time, e);
            }
        }
        
        return time;
    }
    
    /**
     * MEJORA: Extracción robusta de asistentes con múltiples métodos de fallback.
     */
    private List<String> extractAttendees(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> attendees = new ArrayList<>();
        
        // Método 1: Entre "Asistentes:" y "Orden del día:" (actual)
        String block = extractBlock(content, "(?i)Asistentes:", "(?i)Orden del día:");
        if (block != null && !block.trim().isEmpty()) {
            attendees.addAll(extractBullets(block));
        }
        
        // Método 2: Si no se encontró, buscar hasta encontrar otra sección
        if (attendees.isEmpty()) {
            block = extractBlock(content, "(?i)Asistentes:", "(?i)(?:Orden del día|Ruegos|Clausura|No habiendo)");
            if (block != null && !block.trim().isEmpty()) {
                attendees.addAll(extractBullets(block));
            }
        }
        
        // Método 3: Buscar líneas que empiecen con viñeta después de "Asistentes:"
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
        
        // Limpiar y normalizar nombres
        attendees = attendees.stream()
            .map(name -> name.trim())
            .filter(name -> !name.isEmpty())
            .filter(name -> name.length() > 2)
            // Quitar roles entre paréntesis (se extraen por separado)
            .map(name -> name.replaceAll("\\s*\\([^)]*\\)\\s*", "").trim())
            .filter(name -> !name.isEmpty())
            .distinct()
            .collect(Collectors.toList());
        
        log().debug("Extracted {} attendees", attendees.size());
        return attendees;
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
     * MEJORA: Extracción de bullets con múltiples formatos soportados.
     */
    private List<String> extractBullets(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }
        
        // Patrón 1: Viñetas estándar (•)
        Pattern pattern1 = Pattern.compile("•\\s*(.+?)(?=\\n(?:•|\\s*$|Orden|Ruegos|Clausura))", Pattern.MULTILINE);
        Matcher matcher1 = pattern1.matcher(text);
        while (matcher1.find()) {
            String item = matcher1.group(1).trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        
        // Patrón 2: Viñetas alternativas (-, *, números) si no se encontraron con •
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
     * MEJORA: Extracción mejorada de agenda con soporte para sub-items y múltiples formatos.
     */
    private Map<String, String> extractAgendaMap(String content) {
        Map<String, String> agenda = new LinkedHashMap<>();
        if (content == null || content.trim().isEmpty()) {
            return agenda;
        }
        
        // Extraer bloque completo del orden del día
        String agendaBlock = extractAgendaBlock(content);
        if (agendaBlock == null || agendaBlock.trim().isEmpty()) {
            log().warn("No se encontró el bloque del orden del día");
            return agenda;
        }
        
        // Dividir en items principales
        String[] lines = agendaBlock.split("\\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Detectar inicio de nuevo item (viñeta, número, o línea que empieza con mayúscula)
            if (line.matches("^[•·▪▫◦‣⁃-*]\\s+.+") || 
                line.matches("^\\d+[.)]\\s+.+") ||
                (line.matches("^[A-ZÁÉÍÓÚÑ].+") && currentKey == null)) {
                
                // Guardar item anterior
                if (currentKey != null && currentValue.length() > 0) {
                    agenda.put(currentKey, currentValue.toString().trim());
                    currentValue.setLength(0);
                }
                
                // Extraer clave del nuevo item
                currentKey = extractAgendaItemKey(line);
            } else if (currentKey != null) {
                // Continuación del item actual (sub-item o descripción)
                if (!line.matches("(?i)^(?:Ruegos|No habiendo|Clausura).*")) {
                    currentValue.append(line).append(" ");
                }
            }
        }
        
        // Guardar último item
        if (currentKey != null && currentValue.length() > 0) {
            agenda.put(currentKey, currentValue.toString().trim());
        }
        
        log().debug("Extracted {} agenda items", agenda.size());
        return agenda;
    }
    
    /**
     * Extrae el bloque completo del orden del día.
     */
    private String extractAgendaBlock(String content) {
        // Buscar desde "Orden del día:" hasta "Ruegos y preguntas" o "No habiendo más asuntos"
        Pattern pattern = Pattern.compile(
            "(?i)Orden del día:?\\s*(.*?)(?=\\n\\s*(?:Ruegos y preguntas|No habiendo más asuntos|Clausura|$))",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * Extrae la clave de un item de agenda, limpiando viñetas y numeración.
     */
    private String extractAgendaItemKey(String line) {
        // Quitar viñetas y numeración
        line = line.replaceAll("^[•·▪▫◦‣⁃-*]\\s*", "")
                   .replaceAll("^\\d+[.)]\\s*", "")
                   .trim();
        
        // Tomar solo la primera parte (hasta dos puntos o punto final)
        String[] parts = line.split("[:.]", 2);
        return parts[0].trim();
    }
}