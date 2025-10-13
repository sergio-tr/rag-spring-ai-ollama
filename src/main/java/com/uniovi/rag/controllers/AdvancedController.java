package com.uniovi.rag.controllers;

import com.uniovi.rag.model.Minute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class AdvancedController {

    private final ChatClient chatClient;
    private final PgVectorStore vectorStore;

    public AdvancedController(ChatClient chatClient, PgVectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;

        // request = chatClient.prompt().advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()));
    }

    @RequestMapping("/simple")
    public String simple(@RequestParam String question) {
        return chatClient
                .prompt()
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()))
                .user(question)
                .call()
                .content();
    }

    @RequestMapping("/complex")
    public String complex() {
        BeanOutputConverter<List<Minute>> outputConverter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<>() {
                }
        );

        String template = """
                A partir del contenido contextual proporcionado (actas de reuniones), 
                extrae únicamente aquellas celebradas en el MES DE FEBRERO y devuélvelas como una lista JSON.
                
                Sigue estrictamente esta estructura de objeto por cada acta:
                
                $format$
                
                IMPORTANTE:
                - No incluyas explicaciones, encabezados, ni formato markdown como ```json.
                - Devuelve solo un JSON válido que cumpla con el esquema proporcionado.
                - Usa los nombres de campo exactamente como se indican: "date", "place", "startTime", "endTime", "president", "secretary", "attendees", "numberOfAttendees", "agenda".
                - El campo "agenda" debe ser un objeto (mapa) donde las claves son los títulos de los puntos del orden del día y los valores son descripciones breves. Ejemplo textual:
                  agenda: {{
                    "Lectura y aprobación del acta anterior": "Se aprueba por unanimidad",
                    "Ruegos y preguntas": "Se comentan temas de interés"
                  }}
                
                No generes ningún contenido adicional. Solo devuelve un array JSON.
                """;


        System.out.println(outputConverter.getFormat());

        PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", outputConverter.getFormat()));
        String userPrompt = promptTemplate.createMessage().getContent();

        Generation generation = chatClient
                .prompt()
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()))
                .user(userPrompt) 
                .call()
                .chatResponse()
                .getResult();

        String cleanResponse = generation.getOutput().getContent().replace("```json\n", "");
        cleanResponse = cleanResponse.replace("\n```", "");

        System.out.println(cleanResponse);

        List<Minute> minutes = outputConverter.convert(cleanResponse);

        StringBuilder response = new StringBuilder();
        for (Minute minute : minutes) {
            response.append(minute).append("\n");
        }


        return response.toString().replaceAll(",", ",\n");
    }


    @RequestMapping("/tools")
    public String tools() {
        String question = "Cuántas actas se realizaron en el mes de febrero?";
        ChatResponse response = chatClient
                .prompt()
                .system("Eres un asistente que sirve como intermediario entre el usuario y la base de conocimiento sobre actas de reuniones.")
                .advisors(
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults())
                )
                .user(question)
                .call()
                .chatResponse();

        return response.getResult().getOutput().getContent();
    }

}
