package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Properties for selecting service implementations (query-service, retriever, analyser).
 * Keys: rag.query-service-impl (simple|simple-process|process), rag.retriever-impl (basic|filtered|minute-document), rag.analyser-impl (minute-ner|no-op).
 * See EVALUACION_RESULTADOS_2026-03-01.md section 6.
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagImplementationProperties {

    /** rag.query-service-impl: simple, simple-process, process */
    private String queryServiceImpl = "process";
    /** rag.retriever-impl: basic, filtered, minute-document */
    private String retrieverImpl = "basic";
    /** rag.analyser-impl: minute-ner, no-op */
    private String analyserImpl = "minute-ner";

    public String getQueryServiceImpl() {
        return queryServiceImpl;
    }

    public void setQueryServiceImpl(String queryServiceImpl) {
        this.queryServiceImpl = queryServiceImpl;
    }

    public String getRetrieverImpl() {
        return retrieverImpl;
    }

    public void setRetrieverImpl(String retrieverImpl) {
        this.retrieverImpl = retrieverImpl;
    }

    public String getAnalyserImpl() {
        return analyserImpl;
    }

    public void setAnalyserImpl(String analyserImpl) {
        this.analyserImpl = analyserImpl;
    }
}
