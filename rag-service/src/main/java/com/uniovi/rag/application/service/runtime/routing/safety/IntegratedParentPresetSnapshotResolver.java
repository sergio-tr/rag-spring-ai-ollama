package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import org.springframework.stereotype.Component;

/** Resolves parent-preset snapshot bindings for isolated integrated-route parent execution. */
@Component
public class IntegratedParentPresetSnapshotResolver {

    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;

    public IntegratedParentPresetSnapshotResolver(
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector) {
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
    }

    public KnowledgeSnapshotSelection resolve(ExecutionContext base, RagExperimentalPresetCode parentPreset) {
        return LabBenchmarkExecutionContext.campaignPresetBinding(parentPreset.name())
                .filter(binding -> binding.snapshotIds() != null && !binding.snapshotIds().isEmpty())
                .map(
                        binding ->
                                knowledgeRuntimeSnapshotSelector.selectExplicit(
                                        binding.projectId(), binding.snapshotIds()))
                .orElseGet(() -> IntegratedParentCandidateMaterializer.fallbackSnapshots(base));
    }
}
