package com.uniovi.rag.domain;

public enum AsyncTaskStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
