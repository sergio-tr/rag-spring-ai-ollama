package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetKindMapping;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetLabService;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetListItemDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetValidationReportDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Lab experimental datasets: downloadable templates, upload + validation, listing, re-validation.
 *
 * <p>Canonical benchmark start remains {@code POST …/lab/benchmarks/{kind}/runs} (Phase 4+ wiring).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabExperimentalDatasetController {

    private final ExperimentalDatasetLabService experimentalDatasetLabService;

    public LabExperimentalDatasetController(ExperimentalDatasetLabService experimentalDatasetLabService) {
        this.experimentalDatasetLabService = experimentalDatasetLabService;
    }

    @GetMapping("/dataset-templates/{kind}")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable String kind) throws IOException {
        ExperimentalDatasetType experimentalType =
                ExperimentalDatasetKindMapping.parseTemplatePathSegment(kind)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown template kind"));
        byte[] bytes = experimentalDatasetLabService.templateBytes(experimentalType);
        String filename =
                ExperimentalDatasetKindMapping.templateKindSegment(experimentalType).orElse("template") + "-template.xlsx";
        ByteArrayResource body = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(body);
    }

    @PostMapping(value = "/experimental-datasets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExperimentalDatasetUploadResponseDto> uploadDataset(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("datasetType") String datasetType,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description)
            throws IOException {
        ExperimentalDatasetUploadResponseDto body =
                experimentalDatasetLabService.upload(requireUserId(principal), file, datasetType, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/experimental-datasets")
    public List<ExperimentalDatasetListItemDto> listDatasets(@AuthenticationPrincipal RagPrincipal principal) {
        return experimentalDatasetLabService.listForUser(requireUserId(principal));
    }

    @GetMapping("/experimental-datasets/{id}/validation")
    public ExperimentalDatasetValidationReportDto validation(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID id) {
        return experimentalDatasetLabService.validationReport(requireUserId(principal), id);
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
