package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Implementation selection (query service, retriever, analyser).
 * <p>Single configuration source: {@code rag.*} prefix (do not duplicate under {@code rag.features.*}).
 * Keys: {@code rag.query-service-impl}, {@code rag.retriever-impl}, {@code rag.analyser-impl}.</p>
 */
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

    /** Shallow copy for evaluation / overrides without mutating the Spring bean. */
    public static RagImplementationProperties copyOf(RagImplementationProperties source) {
        RagImplementationProperties c = new RagImplementationProperties();
        if (source != null) {
            c.setQueryServiceImpl(source.getQueryServiceImpl());
            c.setRetrieverImpl(source.getRetrieverImpl());
            c.setAnalyserImpl(source.getAnalyserImpl());
        }
        return c;
    }
}
