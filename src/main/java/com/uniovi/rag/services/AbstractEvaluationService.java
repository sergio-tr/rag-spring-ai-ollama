package com.uniovi.rag.services;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractEvaluationService implements EvaluationService {

    protected final OllamaChatModel chatModel;
    protected final DocumentService documentService;
    protected final QueryService queryService;
    protected boolean dataLoaded = false;

    protected final static PromptTemplate EVALUATION_PROMPT_TEMPLATE = new PromptTemplate("""
        Evalúa la calidad de la siguiente respuesta respondiendo en español según estos criterios:
       
        Pregunta: {question}
        Respuesta Correcta: {correctAnswer}
        Respuesta Generada: {generatedAnswer}
       
        - Correcto (1-5): ¿Es correcta?
        - Suficiente contexto (1-5): ¿Se puede responder bien con la información dada?
        - Relevancia (1-5): ¿Es relevante en el contexto?
        - Independencia (1-5): ¿Se entiende sin contexto adicional?
    """);

    public AbstractEvaluationService(OllamaChatModel chatModel, DocumentService documentService, QueryService queryService) {
        this.chatModel = chatModel;
        this.documentService = documentService;
        this.queryService = queryService;
    }

    @Override
    public void loadData() {
        if (dataLoaded) {
            return;
        }
        loadSpecificData();
        dataLoaded = true;
    }

    protected abstract void loadSpecificData();

    @Override
    public Map<String, Object> evaluate() {
        Map<String, Object> results = new HashMap<>();
        List<String> systemPrompts = getSystemPrompts();
        StringBuilder systemPrompt = new StringBuilder(systemPrompts.getFirst());

        for (int i = 1; i <= systemPrompts.size(); i++) {
            List<Map<String, Object>> resultsForPrompt = new ArrayList<>();

            for (Map.Entry<String, String> entry : getQuestionsAndAnswers().entrySet()) {
                String question = entry.getKey();
                String correctAnswer = entry.getValue();
                queryService.setSystemPrompt(systemPrompt.toString());
                String llmResponse = queryService.generateResponse(question);

                String evaluation = evaluateResponse(question, correctAnswer, llmResponse);

                Map<String, Object> result = new HashMap<>();
                result.put("Respuesta Correcta", correctAnswer);
                result.put("Respuesta Generada", llmResponse);
                result.put("Evaluación", evaluation);

                resultsForPrompt.add(result);
            }

            results.put("System prompt: " + systemPrompt, resultsForPrompt);

            if(i < systemPrompts.size()) {
                String prompt = systemPrompts.get(i);
                systemPrompt.append("\n").append(prompt);
            }
        }
        return results;
    }

    protected String evaluateResponse(String question, String correctAnswer, String llmResponse) {
        String prompt = EVALUATION_PROMPT_TEMPLATE.create(
                Map.of(
                "question", question,
                "correctAnswer", correctAnswer,
                "generatedAnswer", llmResponse
                )
        ).getContents();

        return chatModel.call(prompt);
    }
}
