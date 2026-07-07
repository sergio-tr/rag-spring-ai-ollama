package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.tool.metadata.StructuredMinuteMetadataSupport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Builds scoped LLM context from metadata-tool matched actas (agenda, topics, decisions) instead of footer chunks.
 */
@Component
public class MetadataToolContextAssembler {

    private static final int MAX_CONTEXT_CHARS = 10_000;

    private static final Pattern FOOTER_BOILERPLATE =
            Pattern.compile(
                    "(?is).*(?:no habiendo más asuntos|se da por finalizada|clausura de la sesión|clausura de la sesion).*");

    public String assembleFromMinutes(List<Minute> minutes) {
        if (minutes == null || minutes.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Minute minute : minutes) {
            appendMinuteBlock(out, minute);
            if (out.length() >= MAX_CONTEXT_CHARS) {
                break;
            }
        }
        String text = out.toString().trim();
        if (text.length() > MAX_CONTEXT_CHARS) {
            return text.substring(0, MAX_CONTEXT_CHARS) + "\n...[context truncated]\n";
        }
        return text;
    }

    public static boolean isFooterBoilerplateChunk(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return false;
        }
        String trimmed = chunkText.trim();
        if (trimmed.length() < 40) {
            return false;
        }
        return FOOTER_BOILERPLATE.matcher(trimmed).matches();
    }

    public List<String> deprioritizeFooterChunks(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<String> body = new ArrayList<>();
        List<String> footers = new ArrayList<>();
        for (String chunk : chunks) {
            if (isFooterBoilerplateChunk(chunk)) {
                footers.add(chunk);
            } else {
                body.add(chunk);
            }
        }
        body.addAll(footers);
        return body;
    }

    private static void appendMinuteBlock(StringBuilder out, Minute minute) {
        if (minute == null) {
            return;
        }
        String label = StructuredMinuteMetadataSupport.formatSourceReference(minute);
        String dateSlash = StructuredMinuteMetadataSupport.resolveCanonicalSlashDate(minute);
        out.append("--- ").append(label);
        if (!dateSlash.isBlank()) {
            out.append(" (").append(dateSlash).append(')');
        }
        out.append(" ---\n");
        if (minute.place() != null && !minute.place().isBlank()) {
            out.append("place: ").append(minute.place().trim()).append('\n');
        }
        appendAgendaField(out, minute.agenda());
        appendListField(out, "topics", minute.topics());
        appendListField(out, "decisions", minute.decisions());
        if (minute.summary() != null && !minute.summary().isBlank() && !isFooterBoilerplateChunk(minute.summary())) {
            out.append("summary: ").append(minute.summary().trim()).append('\n');
        }
        out.append('\n');
    }

    private static void appendAgendaField(StringBuilder out, Map<String, String> agenda) {
        if (agenda == null || agenda.isEmpty()) {
            return;
        }
        out.append("agenda: ").append(String.join("; ", agenda.values())).append('\n');
    }

    private static void appendListField(StringBuilder out, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                distinct.add(v.trim());
            }
        }
        if (distinct.isEmpty()) {
            return;
        }
        out.append(key).append(": ").append(String.join("; ", distinct)).append('\n');
    }

    public static boolean isHighConfidenceListOrCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("cuántas actas")
                || q.contains("cuantas actas")
                || q.contains("dime qué actas")
                || q.contains("dime que actas")
                || q.contains("dime los lugares")
                || q.contains("en cuántas reuniones")
                || q.contains("en cuantas reuniones")
                || q.contains("más de")
                || q.contains("mas de")
                || q.contains("tienen ")
                || q.contains("exactamente");
    }
}
