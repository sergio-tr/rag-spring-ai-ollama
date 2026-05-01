package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.MeSummaryApplicationService;
import com.uniovi.rag.interfaces.rest.dto.me.MeSummaryResponse;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "Account usage summary")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/summary")
public class MeSummaryController {

    private final MeSummaryApplicationService meSummaryApplicationService;

    public MeSummaryController(MeSummaryApplicationService meSummaryApplicationService) {
        this.meSummaryApplicationService = meSummaryApplicationService;
    }

    @GetMapping
    public MeSummaryResponse get(@AuthenticationPrincipal RagPrincipal principal) {
        return meSummaryApplicationService.summarize(principal.userId());
    }
}
