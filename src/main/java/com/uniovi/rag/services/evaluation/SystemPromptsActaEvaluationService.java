package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemPromptsActaEvaluationService extends ActaEvaluationService {

    private static final String CONFIG_DIRECTORY = "src/main/resources/configuration/actas";
    private static final String[] CONFIG_FILES = {
            "synonyms.txt",
            "potential_confusion.txt",
            "abbreviations.txt",
            "groupings.txt",
            "rules.txt",
            "response_format.txt",
            "operations.txt"
    };

    private final List<String> systemPrompts = new ArrayList<>();

    public SystemPromptsActaEvaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        super(chatModel, documentService, queryService);
        loadSystemPrompts();
    }

    @Override
    public List<String> getSystemPrompts() {
        return systemPrompts;
    }

    private void loadSystemPrompts() {

        for (String fileName : CONFIG_FILES) {

            Path filePath = Paths.get(CONFIG_DIRECTORY, fileName);

            try {
                if (Files.exists(filePath)) {
                    String content = String.join("\n", Files.readAllLines(filePath));
                    systemPrompts.add(content);
                } else {
                    System.err.println("Archivo no encontrado: " + filePath);
                }
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + filePath);
                e.printStackTrace();
            }
        }
    }
}
