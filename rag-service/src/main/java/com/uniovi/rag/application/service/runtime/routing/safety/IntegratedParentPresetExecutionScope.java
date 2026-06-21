package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.function.Supplier;

/** Runs parent preset execution under the Lab binding for that preset when available. */
public final class IntegratedParentPresetExecutionScope {

    private IntegratedParentPresetExecutionScope() {}

    public static <T> T runWithParentPresetBinding(
            RagExperimentalPresetCode parentPreset, Supplier<T> action) {
        return LabBenchmarkExecutionContext.campaignPresetBinding(parentPreset.name())
                .map(
                        binding -> {
                            try (AutoCloseable ignored =
                                    LabBenchmarkExecutionContext.openLab(
                                            ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(
                                                    parentPreset),
                                            binding.runId(),
                                            binding.projectId(),
                                            binding.snapshotIds(),
                                            binding.groupKey(),
                                            parentPreset.name(),
                                            true)) {
                                return action.get();
                            } catch (RuntimeException ex) {
                                throw ex;
                            } catch (Exception ex) {
                                throw new IllegalStateException(
                                        "Parent preset lab scope failed for " + parentPreset, ex);
                            }
                        })
                .orElseGet(action);
    }
}
