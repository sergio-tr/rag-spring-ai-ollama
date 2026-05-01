/**
 * Research Lab bounded context: async jobs, evaluations, classifier train/eval orchestration.
 * <p>
 * Long-running work uses {@code async_task} and {@code com.uniovi.rag.service.async.AsyncLabTaskRunner};
 * for multi-instance deployments prefer an external queue or DB-backed lease (see {@code LongRunningJobNotes}).
 */
package com.uniovi.rag.domain.research;
