package com.uniovi.rag.controllers;

import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.uniovi.rag.model.QueryResponse;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final QueryExpander expander;
    private final QueryAnalyser analyser;
    private final QueryService queryService;

    public TestController(QueryExpander expander, QueryAnalyser analyser, QueryService queryService) {
        this.expander = expander;
        this.analyser = analyser;
        this.queryService = queryService;
    }

    @RequestMapping("/expand")
    public String expand(String question) {
        return expander.expand(question);
    }

    @RequestMapping("/analyse")
    public String analyse(String question) {
        return analyser.analyse(question).toString();
    }

    @RequestMapping("/agentic")
    public String agentic(@RequestParam String question) {
        QueryResponse response = queryService.generateResponse(question);
        return response.getAnswer();
    }
}
