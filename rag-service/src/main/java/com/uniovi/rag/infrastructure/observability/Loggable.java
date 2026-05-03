package com.uniovi.rag.infrastructure.observability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Loggable {
    default Logger log() {
        return LoggerFactory.getLogger(this.getClass());
    }
}