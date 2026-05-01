package com.uniovi.rag.services.expand;

import org.springframework.ai.ollama.OllamaChatModel;

public class SimpleQueryExpander extends AbstractQueryExpander {

    public SimpleQueryExpander(OllamaChatModel model) {
        super(model);
    }

    @Override
    public String expand(String query) {
        String expansionPrompt = String.format(
                "Tu tarea es reformular la siguiente pregunta para hacerla más clara y precisa, " +
                "sin cambiar su significado. No respondas la pregunta, solo devuélvela reformulada." +
                " Devuelve únicamente la nueva pregunta sin añadir comentarios ni otro texto.\n" +
                "Pregunta original: \"%s\"\n" +
                "Pregunta reformulada:", query
        );
        return this.model.call(expansionPrompt);
    }
}
