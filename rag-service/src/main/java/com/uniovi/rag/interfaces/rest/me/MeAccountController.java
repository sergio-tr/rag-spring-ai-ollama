package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.AccountExportArtifactStatus;
import com.uniovi.rag.infrastructure.persistence.AccountExportArtifactRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AccountExportArtifactEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.interfaces.rest.dto.me.AccountDeletionRequest;
import com.uniovi.rag.interfaces.rest.dto.me.AccountJobAcceptedDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Account", description = "Async export, deletion, and job polling (not Lab)")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/account")
public class MeAccountController {

    public static final String DELETION_CONFIRM_LITERAL = "DELETE_ACCOUNT_AND_ALL_DATA";

    private final AsyncTaskService asyncTaskService;
    private final AccountExportArtifactRepository accountExportArtifactRepository;
    private final UserRepository userRepository;
    private final RagApiPathProperties apiPathProperties;

    public MeAccountController(
            AsyncTaskService asyncTaskService,
            AccountExportArtifactRepository accountExportArtifactRepository,
            UserRepository userRepository,
            RagApiPathProperties apiPathProperties) {
        this.asyncTaskService = asyncTaskService;
        this.accountExportArtifactRepository = accountExportArtifactRepository;
        this.userRepository = userRepository;
        this.apiPathProperties = apiPathProperties;
    }

    @PostMapping("/export")
    public ResponseEntity<AccountJobAcceptedDto> export(@AuthenticationPrincipal RagPrincipal principal) {
        UUID jobId = asyncTaskService.submitAccountExport(principal.userId());
        String poll = apiPathProperties.getProductBasePath() + "/me/account/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AccountJobAcceptedDto(jobId, "ACCEPTED", poll));
    }

    @PostMapping("/deletion")
    public ResponseEntity<AccountJobAcceptedDto> deletion(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody AccountDeletionRequest body) {
        UserEntity user = userRepository.findById(principal.userId()).orElseThrow();
        if (!user.getEmail().equalsIgnoreCase(body.email().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email does not match the signed-in user");
        }
        if (!DELETION_CONFIRM_LITERAL.equals(body.confirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confirmation literal");
        }
        UUID jobId = asyncTaskService.submitAccountDeletion(principal.userId());
        String poll = apiPathProperties.getProductBasePath() + "/me/account/jobs/" + jobId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AccountJobAcceptedDto(jobId, "ACCEPTED", poll));
    }

    @GetMapping("/jobs/{taskId}")
    public AsyncTaskStatusDto jobStatus(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID taskId) {
        return asyncTaskService.getAccountJobStatus(taskId, principal.userId());
    }

    @GetMapping("/export/{exportId}/download")
    public ResponseEntity<Resource> downloadExport(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID exportId) {
        AccountExportArtifactEntity artifact =
                accountExportArtifactRepository
                        .findByIdAndUser_Id(exportId, principal.userId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found"));
        if (artifact.getStatus() != AccountExportArtifactStatus.READY) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export is not available");
        }
        if (artifact.getExpiresAt().isBefore(Instant.now())) {
            artifact.setStatus(AccountExportArtifactStatus.EXPIRED);
            accountExportArtifactRepository.save(artifact);
            throw new ResponseStatusException(HttpStatus.GONE, "Export has expired");
        }
        Path path = Path.of(artifact.getStorageUri());
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export file missing");
        }
        if (artifact.getDownloadedAt() == null) {
            artifact.setDownloadedAt(Instant.now());
            accountExportArtifactRepository.save(artifact);
        }
        PathResource resource = new PathResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"account-export.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
