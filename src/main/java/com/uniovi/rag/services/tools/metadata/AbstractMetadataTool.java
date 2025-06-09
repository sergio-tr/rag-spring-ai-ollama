package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.AbstractTool;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;

public abstract class AbstractMetadataTool extends AbstractTool {

    public AbstractMetadataTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    protected boolean matchesBooleanCondition(Document doc, String[] keywords, JSONObject nerEntities) {
        // Nivel 1: Entidades extraídas (NER) exactas en metadatos
        if (nerEntities != null && nerEntities.has("entities")) {
            JSONObject entidades = nerEntities.getJSONObject("entities");
            if (containsInMetadata(doc, extractTermsFromNER(entidades))) return true;
        }

        // Nivel 2: keywords en metadatos
        if (containsInMetadata(doc, keywords)) return true;

        // Nivel 3: keywords en contenido
        if (containsAnyKeyword(doc.getContent(), keywords)) return true;

        // Nivel 4 (opcional): verificación semántica con LLM si no se encontró nada
        return semanticallyMatches(doc.getContent(), keywords);
    }

    protected boolean containsInMetadata(Document doc, String[] terms) {
        for (Object val : doc.getMetadata().values()) {
            if (val instanceof String str && containsAnyKeyword(str, terms)) return true;
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && containsAnyKeyword(s, terms)) return true;
                }
            }
        }
        return false;
    }

    protected String[] extractTermsFromNER(JSONObject entidades) {
        Set<String> terms = new HashSet<>();

        if (entidades.has("person"))
            entidades.getJSONArray("person").forEach(item -> terms.add(item.toString().toLowerCase()));

        if (entidades.has("filters")) {
            JSONObject filtros = entidades.getJSONObject("filters");
            for (String key : new String[]{"date", "place", "section", "time"}) {
                if (filtros.has(key))
                    filtros.getJSONArray(key).forEach(item -> terms.add(item.toString().toLowerCase()));
            }
        }

        if (entidades.has("answer_type"))
            terms.add(entidades.getString("answer_type").toLowerCase());

        return terms.toArray(new String[0]);
    }

    protected boolean semanticallyMatches(String content, String[] keywords) {
        String prompt = """
                Dado el siguiente contenido de acta, dime si se hace alguna mención relacionada con estos temas: %s
                Contenido del acta:
                %s
                
                Responde solo con "Sí" o "No".
                """.formatted(String.join(", ", keywords), content);

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("sí");
    }
}

