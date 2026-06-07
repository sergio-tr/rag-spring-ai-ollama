package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M4-CAT1: workbook preset families align with catalog API export. */
class LabExperimentalPresetCatalogFamilyTest {

    @Test
    void catalogFamilies_matchWorkbookDefinitions() {
        EvaluationReferenceBundleLoader loader =
                new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        ReferenceBundleSnapshot snap = loader.getSnapshot();
        assertThat(snap.validationReport().hasErrors()).isFalse();

        Map<RagExperimentalPresetCode, RagPresetDefinition> defs = new EnumMap<>(RagExperimentalPresetCode.class);
        for (RagPresetDefinition d : snap.workbook().ragPresetCatalog()) {
            defs.put(d.presetId(), d);
        }
        assertThat(defs).hasSize(15);

        LabExperimentalPresetCatalogService catalog =
                new LabExperimentalPresetCatalogService(loader, new RagFeatureConfiguration());
        List<ExperimentalPresetCatalogItemDto> items = catalog.list();
        assertThat(items).hasSize(15);

        for (ExperimentalPresetCatalogItemDto item : items) {
            RagExperimentalPresetCode code = RagExperimentalPresetCode.valueOf(item.code());
            RagPresetDefinition def = defs.get(code);
            assertThat(def).as("workbook row for %s", code).isNotNull();
            assertThat(item.family())
                    .as("family for %s", code)
                    .isEqualTo(def.family());
        }
    }
}
