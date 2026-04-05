package com.uniovi.rag.application.service.account;

/**
 * Keys in {@code async_task.result_json} for account jobs (export/deletion).
 */
public final class AccountJobPayloadKeys {

    public static final String EXPORT_ARTIFACT_ID = "exportArtifactId";
    public static final String TASK_TYPE = "taskType";

    private AccountJobPayloadKeys() {
    }
}
