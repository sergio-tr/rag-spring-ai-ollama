package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ensures campaign child runs execute parent presets before P15 parent replay. */
public final class CampaignPresetExecutionOrder {

    private static final List<RagExperimentalPresetCode> CANONICAL_ORDER =
            List.of(
                    RagExperimentalPresetCode.P0,
                    RagExperimentalPresetCode.P1,
                    RagExperimentalPresetCode.P2,
                    RagExperimentalPresetCode.P3,
                    RagExperimentalPresetCode.P4,
                    RagExperimentalPresetCode.P5,
                    RagExperimentalPresetCode.P6,
                    RagExperimentalPresetCode.P7,
                    RagExperimentalPresetCode.P8,
                    RagExperimentalPresetCode.P9,
                    RagExperimentalPresetCode.P10,
                    RagExperimentalPresetCode.P11,
                    RagExperimentalPresetCode.P12,
                    RagExperimentalPresetCode.P13,
                    RagExperimentalPresetCode.P14,
                    RagExperimentalPresetCode.P15);

    private CampaignPresetExecutionOrder() {}

    /**
     * Orders requested preset codes so P6/P7/P9 complete before P15 when all are present.
     * Unknown codes are appended after known codes in request order.
     */
    public static List<String> orderForParentReplay(List<String> presetCodes) {
        if (presetCodes == null || presetCodes.isEmpty()) {
            return List.of();
        }
        Set<RagExperimentalPresetCode> wanted = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : presetCodes) {
            RagExperimentalPresetCode.tryParse(raw).ifPresentOrElse(wanted::add, () -> unknown.add(raw));
        }
        List<String> ordered = new ArrayList<>();
        for (RagExperimentalPresetCode code : CANONICAL_ORDER) {
            if (wanted.remove(code)) {
                ordered.add(code.name());
            }
        }
        wanted.stream().map(RagExperimentalPresetCode::name).forEach(ordered::add);
        ordered.addAll(unknown);
        return List.copyOf(ordered);
    }
}
