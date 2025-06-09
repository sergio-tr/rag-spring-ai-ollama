package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public abstract class AbstractEvaluationService implements EvaluationService {

    protected final ChatClient chatClient;
    protected final DocumentService documentService;
    protected final QueryService queryService;
    protected final RagFeatureConfiguration featureConfig;
    protected boolean dataLoaded = false;

    protected final static PromptTemplate EVALUATION_PROMPT_TEMPLATE = new PromptTemplate("""
            Actúa como un evaluador experto en sistemas RAG. 
            Evalúa la calidad de la respuesta generada a una pregunta, comparándola con una respuesta correcta esperada.
            
            **IMPORTANTE**: No inventes ni uses conocimiento externo. 
            Evalúa únicamente lo que puedes deducir a partir de las tres piezas de entrada: pregunta realizada, respuesta correcta y respuesta generada por el sistema.
            
            Pregunta: {question}
            Respuesta Correcta Esperada: {correctAnswer}
            Respuesta Generada por el Sistema: {generatedAnswer}
            
            Evalúa en una escala de 1 a 5 los siguientes criterios:
            
            1. **Corrección**: ¿La respuesta es correcta en base a lo esperado?
            2. **Suficiencia del contexto**: ¿Es posible responder correctamente con la información que se presenta?
            3. **Relevancia**: ¿La respuesta trata exclusivamente sobre lo que se pregunta, sin desviarse?
            4. **Independencia**: ¿Se entiende por sí sola, sin depender de contexto adicional?
            
            Responde en este formato:
            
            Corrección: [1-5] - Justificación: ...
            Suficiencia del contexto: [1-5] - Justificación: ...
            Relevancia: [1-5] - Justificación: ...
            Independencia: [1-5] - Justificación: ...
            Resumen general: [Breve evaluación global de la calidad de la respuesta]
            """);


    public AbstractEvaluationService(RagFeatureConfiguration featureConfig, ChatClient chatClient, DocumentService documentService, QueryService queryService) {
        this.featureConfig = featureConfig;
        this.chatClient = chatClient;
        this.documentService = documentService;
        this.queryService = queryService;
    }

    @Override
    public void loadData() {
        if (!dataLoaded) {
            loadSpecificData();
            dataLoaded = true;
        }
    }

    protected abstract void loadSpecificData();

    @Override
    public Map<String, Object> evaluate() {
        Map<String, Object> results = new HashMap<>();

        results.put("configuration", featureConfig.getConfiguration());

        List<Map<String, Object>> resultsForPrompt = new ArrayList<>();

        for (Map.Entry<String, String> entry : getQuestionsAndAnswers().entrySet()) {
            String question = entry.getKey();
            String correctAnswer = entry.getValue();
            String llmResponse = queryService.generateResponse(question);

            String evaluation = evaluateResponse(question, correctAnswer, llmResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("correct_answer", correctAnswer);
            result.put("generated_answer", llmResponse);
            result.put("llm_evaluation", evaluation);

            resultsForPrompt.add(result);

            System.out.println(result);
        }

        results.put("results", resultsForPrompt);

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

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}
