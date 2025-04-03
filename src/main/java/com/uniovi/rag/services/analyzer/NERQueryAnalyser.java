package com.uniovi.rag.services.analyzer;

import org.springframework.ai.ollama.OllamaChatModel;

public class NERQueryAnalyser implements QueryAnalyser {

    private final static String NER_PROMPT = """
    
    Analiza la siguiente pregunta con el objetivo de extraer entidades clave que podrían estar presentes en actas de reuniones con la siguiente estructura:
    
    Estas actas siguen una estructura formal con secciones como:
    - ACTA DE LA REUNIÓN DE LA COMUNIDAD DE VECINOS
    - Fecha (día, mes y año)
    - Lugar
    - Hora de inicio
    - Hora de fin
    - Lista de Asistentes: número de asistentes, nombres y cargos (por ejemplo: presidente, secretario).
    - Orden del Día: temas discutidos durante la reunión, entre los cuales se encuentran Acuerdos, Noticias, Decisiones Tomadas; resoluciones aprobadas o votadas.
    - Ruegos y Preguntas: intervenciones abiertas al final de la sesión.
    - Hora de finalización de la reunión.
    
    Tu tarea es identificar los elementos clave que se deben buscar dentro de esa estructura para poder responder correctamente a la pregunta.
    No respondas a la pregunta. No generes explicaciones, ÚNICAMENTE responde con las entidades clave de la estructura de documentos de las cuales se debe sacar información para responder a la pregunta.
    
    Pregunta: "%s"
    
    """;

    private final OllamaChatModel model;

    public NERQueryAnalyser(OllamaChatModel model) {
        this.model = model;
    }

    @Override
    public String analyze(String query) {
        String nerPrompt = String.format(NER_PROMPT, query);
        return this.model.call(nerPrompt);
    }
}
