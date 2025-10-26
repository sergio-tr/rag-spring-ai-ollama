package com.uniovi.rag.services.document;

import com.uniovi.rag.model.Minute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetadataDocumentService extends AbstractMetadataDocumentService {

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


    public MetadataDocumentService(PgVectorStore vectorStore, ChatClient chatClient) {
        super(vectorStore, chatClient);
    }

    @Override
    protected Minute extractMinute(String content, String filename) {
        String date = extractSingle(content, "(?i)Fecha:\\s*(\\d{1,2} de [a-záéíóú]+ de \\d{4})");
        String place = extractSingle(content, "(?i)Lugar:\\s*(.+)");
        String startTime = extractSingle(content, "(?i)Hora de inicio:\\s*(\\d{1,2}:\\d{2})");
        String endTime = extractSingle(content, "(?i)Hora de finalización:\\s*(\\d{1,2}:\\d{2})");
        String president = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
        String secretary = extractSingle(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
        List<String> attendees = extractBullets(extractBlock(content, "(?i)Asistentes:\\s*", "(?i)Orden del día:"));
        Map<String, String> agenda = extractAgendaMap(content);

        // 🔍 LLM-assisted extraction
        List<String> decisions = extractWithPrompt(content, PROMPT_DECISIONS);

        List<String> mentionedEntities = extractWithPrompt(content, PROMPT_ENTITIES);

        List<String> topics = extractWithPrompt(content, PROMPT_TOPICS);

        String summary = extractSummaryWithPrompt(content, PROMPT_SUMMARY);

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


    private List<String> extractWithPrompt(String content, String prompt) {
        String rawResponse = chatClient
                .prompt()
                .system(SYSTEM_PROMPT_LINE_DATA)
                .user(prompt + "\nTexto del acta:\n" + content)
                .call()
                .content();

        return Arrays.stream(rawResponse.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private String extractSummaryWithPrompt(String content, String prompt) {
        return chatClient
                .prompt()
                .system(SYSTEM_PROMPT_SUMMARY)
                .user(prompt + "\nTexto del acta:\n" + content)
                .call()
                .content()
                .trim();
    }

    @Override
    public Map<String, Object> extractMetadata(Minute minute) {
        Map<String, Object> metadata = new HashMap<>();
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
        log().info("Metadata extracted: {}", metadata);
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

//   METADATA CHUNCK SERVICE
//
//    @Override
//    protected List<Document> splitIntoChunks(String content, Minute minute) {
//        List<Document> chunks = new ArrayList<>();
//
//        Map<String, Object> baseMetadata = extractBaseMetadata(minute);
//        baseMetadata.put("minuteJson", serializeMinute(minute));
//
//        // Chunk 1: fecha, lugar, hora de inicio, hora de fin
//        String datosBasicos = String.format("Fecha: %s\nLugar: %s\nInicio: %s\nFin: %s",
//                minute.date(), minute.place(), minute.startTime(), minute.endTime());
//        chunks.add(new Document(datosBasicos, baseMetadata));
//
//        // Chunk 2: asistentes
//        String asistentes = String.join("\n", minute.attendees());
//        Map<String, Object> asistentesMeta = new HashMap<>(baseMetadata);
//        asistentesMeta.put("section", "asistentes");
//        chunks.add(new Document(asistentes, asistentesMeta));
//
//        // Chunk 3+: puntos del orden del día
//        for (Map.Entry<String, String> entry : minute.agenda().entrySet()) {
//            Map<String, Object> puntoMeta = new HashMap<>(baseMetadata);
//            puntoMeta.put("section", "agenda");
//            puntoMeta.put("pointTitle", entry.getKey());
//            chunks.add(new Document(entry.getValue(), puntoMeta));
//        }
//
//        return chunks;
//    }