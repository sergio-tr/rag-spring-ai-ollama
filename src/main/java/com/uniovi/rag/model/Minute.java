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
        Map<String, String> agenda,
        List<String> decisions,
        List<String> mentionedEntities,
        List<String> topics,
        String summary
) {
}
