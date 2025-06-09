package com.uniovi.rag.model;

import java.util.List;
import java.util.Map;


public record Minute(
        String id,
        String filename,
        String date,
        String place,
        String startTime,
        String endTime,
        String president,
        String secretary,
        List<String> attendees,
        int numberOfAttendees,
        Map<String, String> agenda,        // punto → contenido
        List<String> decisions,            // acuerdos explícitos: "Se aprobó...", "Se decidió..."
        List<String> mentionedEntities,    // empresas, organismos, técnicos
        List<String> topics,               // temas tratados (ej: “seguridad”, “iluminación”)
        String summary                     // resumen del acta completo
) {
}
