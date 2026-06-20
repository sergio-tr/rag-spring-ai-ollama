package com.uniovi.rag.application.service.evaluation.preset;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignPresetExecutionOrderTest {

    @Test
    void ordersP15AfterParentPresetsRegardlessOfRequestOrder() {
        List<String> ordered =
                CampaignPresetExecutionOrder.orderForParentReplay(
                        List.of("P15", "P9", "P7", "P6", "P0"));

        assertThat(ordered).containsExactly("P0", "P6", "P7", "P9", "P15");
    }

    @Test
    void preservesCanonicalOrderForFullLadderRequest() {
        List<String> request =
                List.of("P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9", "P10", "P15");
        assertThat(CampaignPresetExecutionOrder.orderForParentReplay(request)).containsExactlyElementsOf(request);
    }

    @Test
    void ensuresP7RunsAfterP6() {
        List<String> ordered =
                CampaignPresetExecutionOrder.orderForParentReplay(List.of("P7", "P6", "P15"));
        assertThat(ordered).containsExactly("P6", "P7", "P15");
    }
}
