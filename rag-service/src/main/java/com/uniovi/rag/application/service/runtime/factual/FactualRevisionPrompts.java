package com.uniovi.rag.application.service.runtime.factual;

import java.util.stream.Collectors;

public final class FactualRevisionPrompts {

    private FactualRevisionPrompts() {}

    public static String revisionUserTurn(
            String question, String context, String draftAnswer, FactualVerifierResult failed) {
        String reasons =
                failed.failures().stream().map(Enum::name).collect(Collectors.joining(", "));
        return """
                Revisa la respuesta usando SOLO el contexto documental abajo.
                Si la evidencia no respalda la respuesta, indica que no hay información suficiente en las fuentes.

                Problemas detectados: %s

                <Question> %s </Question>
                <Context> %s </Context>
                <DraftAnswer> %s </DraftAnswer>

                Respuesta corregida:
                """
                .formatted(safe(reasons), safe(question), safe(context), safe(draftAnswer));
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
