package com.uniovi.rag.application.config;

import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical runtime configuration resolution (microphase 2.1).
 */
@Service
public class ConfigResolverService {

    private final RagConfigurationResolver ragConfigurationResolver;
    private final ProjectRepository projectRepository;
    private final UserPersonalizationRepository userPersonalizationRepository;
    private final CompatibilityValidator compatibilityValidator;
    private final ReindexImpactAnalyzer reindexImpactAnalyzer;
    private final SystemPromptComposer systemPromptComposer;

    public ConfigResolverService(
            RagConfigurationResolver ragConfigurationResolver,
            ProjectRepository projectRepository,
            UserPersonalizationRepository userPersonalizationRepository,
            CompatibilityValidator compatibilityValidator,
            ReindexImpactAnalyzer reindexImpactAnalyzer,
            SystemPromptComposer systemPromptComposer) {
        this.ragConfigurationResolver = ragConfigurationResolver;
        this.projectRepository = projectRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
        this.compatibilityValidator = compatibilityValidator;
        this.reindexImpactAnalyzer = reindexImpactAnalyzer;
        this.systemPromptComposer = systemPromptComposer;
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig resolve(RuntimeConfigResolutionInput input) {
        ResolvedRuntimeConfig built = buildResolved(input, false);
        if (!built.compatibility().valid()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid runtime configuration: " + summarizeCompatibility(built.compatibility()));
        }
        return built;
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig preview(RuntimeConfigResolutionInput input) {
        return buildResolved(input, true);
    }

    public ResolvedConfigSnapshot snapshot(ResolvedRuntimeConfig resolved) {
        UUID id = UUID.randomUUID();
        return new ResolvedConfigSnapshot(
                id,
                Instant.now(),
                resolved,
                resolved.capabilitySet(),
                resolved.compatibility(),
                resolved.reindexImpact(),
                resolved.effectiveSystemPrompt(),
                resolved.provenance());
    }

    private static String summarizeCompatibility(CompatibilityResult c) {
        if (c.errors().isEmpty()) {
            return c.severity().name();
        }
        return c.errors().getFirst().message();
    }

    private ResolvedRuntimeConfig buildResolved(RuntimeConfigResolutionInput input, boolean previewMode) {
        RagConfig core =
                ragConfigurationResolver.resolve(
                        input.userId(), input.projectId(), input.effectiveRuntimeOverride());
        CapabilitySet capabilitySet = CapabilitySet.fromRagConfig(core);
        CompatibilityResult compatibility = compatibilityValidator.validate(capabilitySet, core);

        CapabilitySet baseline =
                input.baselineCapabilitySet()
                        .or(() -> input.baselineResolved().map(ResolvedRuntimeConfig::capabilitySet))
                        .orElse(null);
        ReindexImpact reindexImpact;
        if (previewMode
                && (!input.touchedProfileTypes().isEmpty() || baseline != null)) {
            reindexImpact =
                    reindexImpactAnalyzer.analyze(baseline, capabilitySet, input.touchedProfileTypes());
        } else {
            reindexImpact = ReindexImpact.none();
        }

        SystemPromptLayers layers = loadSystemPromptLayers(input.userId(), input.projectId());
        String effective = systemPromptComposer.compose(layers);
        ConfigProvenance provenance = buildProvenance(input);

        return new ResolvedRuntimeConfig(
                core, capabilitySet, compatibility, reindexImpact, layers, effective, provenance, core);
    }

    private ConfigProvenance buildProvenance(RuntimeConfigResolutionInput input) {
        UUID presetUuid =
                input.presetId()
                        .map(
                                s -> {
                                    try {
                                        return UUID.fromString(s);
                                    } catch (IllegalArgumentException e) {
                                        return null;
                                    }
                                })
                        .orElse(null);
        return new ConfigProvenance(null, null, null, List.of(), presetUuid, null);
    }

    private SystemPromptLayers loadSystemPromptLayers(UUID userId, UUID projectId) {
        String base = "";
        String account = "";
        if (userId != null) {
            Optional<UserPersonalizationEntity> pe = userPersonalizationRepository.findById(userId);
            if (pe.isPresent() && pe.get().getPersonalization() != null) {
                Object p = pe.get().getPersonalization().get("globalPersonaPrompt");
                if (p != null) {
                    account = p.toString();
                }
            }
        }
        String project = "";
        if (projectId != null) {
            Optional<ProjectEntity> proj = projectRepository.findById(projectId);
            if (proj.isPresent() && proj.get().getProjectPrompt() != null) {
                project = proj.get().getProjectPrompt();
            }
        }
        String presetWorkflow = "";
        return new SystemPromptLayers(base, account, project, presetWorkflow);
    }
}
