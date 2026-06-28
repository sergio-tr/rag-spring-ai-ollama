package com.uniovi.rag.application.service.account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Machine-readable export exclusions for account ZIP (manifest v2 companion file). */
public final class AccountExportExclusions {

    private AccountExportExclusions() {}

    public static List<Map<String, Object>> build() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(exclusion("document_binaries", "Uploaded file bytes are excluded from ZIP due to size; metadata is in documents.json"));
        items.add(exclusion("vector_store_chunks", "Indexed chunk text and embeddings are excluded; removed on account deletion"));
        items.add(exclusion("runtime_execution_trace_full", "Full execution trace JSONB excluded; rows removed on account deletion"));
        items.add(exclusion("mail_outbox", "Operational email outbox copies are not exported; purged on account deletion"));
        items.add(exclusion("async_task_payloads", "Background job request/result payloads are not exported"));
        items.add(exclusion("system_reference_datasets", "Shared reference bundles (e.g. REFERENCE_BUNDLE) are not user-owned"));
        items.add(exclusion("legacy_documents_table", "V1 demo documents table has no per-user ownership"));
        items.add(exclusion("default_classifier_model", "System default classifier model is shared infrastructure"));
        return items;
    }

    private static Map<String, Object> exclusion(String key, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("reason", reason);
        return row;
    }
}
