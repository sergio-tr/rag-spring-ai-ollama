package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.interfaces.rest.admin.dto.AdminAllowlistEntryDto;
import com.uniovi.rag.interfaces.rest.admin.dto.CreateAllowlistEntryRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.PullOllamaModelRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.UpdateAllowlistEntryRequest;
import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.service.admin.AllowlistAdminService;
import com.uniovi.rag.service.async.AsyncTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only operations: health, model allowlist CRUD, on-demand Ollama pull.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AllowlistAdminService allowlistAdminService;
    private final AdminSystemDefaultsService adminSystemDefaultsService;
    private final OllamaModelProvisioningService ollamaModelProvisioningService;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;

    public AdminController(
            AllowlistAdminService allowlistAdminService,
            AdminSystemDefaultsService adminSystemDefaultsService,
            OllamaModelProvisioningService ollamaModelProvisioningService,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties) {
        this.allowlistAdminService = allowlistAdminService;
        this.adminSystemDefaultsService = adminSystemDefaultsService;
        this.ollamaModelProvisioningService = ollamaModelProvisioningService;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "scope", "admin");
    }

    @GetMapping("/system-defaults")
    public Map<String, Object> getSystemDefaults() {
        return adminSystemDefaultsService.getDefaults();
    }

    @PutMapping("/system-defaults")
    public Map<String, Object> putSystemDefaults(@RequestBody Map<String, Object> body) {
        return adminSystemDefaultsService.putDefaults(body);
    }

    @GetMapping("/allowlist")
    public List<AdminAllowlistEntryDto> listAllowlist() {
        return allowlistAdminService.list();
    }

    @PostMapping("/allowlist")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAllowlistEntryDto createAllowlist(@Valid @RequestBody CreateAllowlistEntryRequest body) {
        return allowlistAdminService.create(body);
    }

    @PutMapping("/allowlist/{id}")
    public AdminAllowlistEntryDto updateAllowlist(
            @PathVariable UUID id, @Valid @RequestBody UpdateAllowlistEntryRequest body) {
        return allowlistAdminService.update(id, body);
    }

    @DeleteMapping("/allowlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllowlist(@PathVariable UUID id) {
        allowlistAdminService.delete(id);
    }

    @PostMapping("/ollama/pull")
    public ResponseEntity<?> pullModel(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody PullOllamaModelRequest body,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync) {
        if (sync) {
            try {
                ollamaModelProvisioningService.ensureModelPresent(body.model().trim());
                return ResponseEntity.ok(Map.of("status", "ok", "model", body.model().trim()));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interrupted: " + e.getMessage());
            }
        }
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID jobId = asyncTaskService.submitOllamaPull(principal.userId(), body.model().trim());
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LabJobAcceptedDto(jobId, "ACCEPTED", base, base + "/events"));
    }
}
