package com.uniovi.rag.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexPreview;
import com.uniovi.rag.domain.config.prompt.PromptFragment;
import com.uniovi.rag.domain.config.prompt.PromptFragmentRole;
import com.uniovi.rag.domain.config.prompt.PromptStack;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityValidator;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link ResolvedRuntimeConfig} from the existing cascade plus prompt layers.
 */
@Service
public class RuntimeConfigResolutionService {

    private final RagConfigurationResolver ragConfigurationResolver;
    private final ProjectRepository projectRepository;
    private final UserPersonalizationRepository userPersonalizationRepository;

    public RuntimeConfigResolutionService(
            RagConfigurationResolver ragConfigurationResolver,
            ProjectRepository projectRepository,
            UserPersonalizationRepository userPersonalizationRepository) {
        this.ragConfigurationResolver = ragConfigurationResolver;
        this.projectRepository = projectRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride) {
        RagConfig core = ragConfigurationResolver.resolve(userId, projectId, runtimeOverride);
        return buildResolved(userId, projectId, core, null, Set.of());
    }

    /**
     * What-if resolution with optional reindex signals from touched profile types or capability diff.
     */
    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig preview(
            UUID userId,
            UUID projectId,
            JsonNode runtimeOverride,
            Set<ConfigProfileType> touchedProfileTypes,
            CapabilitySet baselineCapability) {
        RagConfig core = ragConfigurationResolver.resolve(userId, projectId, runtimeOverride);
        CapabilitySet cap = CapabilitySet.fromRagConfig(core);
        ReindexPreview fromProfiles = ReindexPreview.fromTouchedProfileTypes(touchedProfileTypes);
        ReindexPreview fromDiff =
                baselineCapability != null
                        ? ReindexPreview.fromCapabilityDiff(baselineCapability, cap)
                        : new ReindexPreview(false, List.of());
        ReindexPreview merged = mergeReindexPreview(fromProfiles, fromDiff);
        return buildResolved(userId, projectId, core, merged, touchedProfileTypes);
    }

    private static ReindexPreview mergeReindexPreview(ReindexPreview a, ReindexPreview b) {
        if (a.requiresReindex() || b.requiresReindex()) {
            List<String> reasons = new ArrayList<>();
            reasons.addAll(a.reasons());
            reasons.addAll(b.reasons());
            return new ReindexPreview(true, List.copyOf(reasons));
        }
        return new ReindexPreview(false, List.of());
    }

    private ResolvedRuntimeConfig buildResolved(
            UUID userId,
            UUID projectId,
            RagConfig core,
            ReindexPreview reindexPreview,
            Set<ConfigProfileType> touchedForPreview) {
        CapabilitySet capabilitySet = CapabilitySet.fromRagConfig(core);
        CompatibilityResult compatibility = CompatibilityValidator.validate(core);
        PromptStack stack = buildPromptStack(userId, projectId);
        ConfigProvenance provenance =
                new ConfigProvenance(null, null, null, List.of(), null, null);
        ReindexPreview indexing =
                reindexPreview != null
                        ? reindexPreview
                        : (!touchedForPreview.isEmpty()
                                ? ReindexPreview.fromTouchedProfileTypes(touchedForPreview)
                                : new ReindexPreview(false, List.of()));
        return new ResolvedRuntimeConfig(
                core, capabilitySet, compatibility, stack, indexing, provenance, core);
    }

    private PromptStack buildPromptStack(UUID userId, UUID projectId) {
        List<PromptFragment> fragments = new ArrayList<>();
        fragments.add(new PromptFragment(PromptFragmentRole.SYSTEM_BASE, "deployment", ""));
        String persona = "";
        if (userId != null) {
            Optional<UserPersonalizationEntity> pe = userPersonalizationRepository.findById(userId);
            if (pe.isPresent() && pe.get().getPersonalization() != null) {
                Object p = pe.get().getPersonalization().get("globalPersonaPrompt");
                if (p != null) {
                    persona = p.toString();
                }
            }
        }
        fragments.add(new PromptFragment(PromptFragmentRole.USER_PERSONA, "user_personalization", persona));
        String projectPrompt = "";
        if (projectId != null) {
            Optional<ProjectEntity> proj = projectRepository.findById(projectId);
            if (proj.isPresent() && proj.get().getProjectPrompt() != null) {
                projectPrompt = proj.get().getProjectPrompt();
            }
        }
        fragments.add(new PromptFragment(PromptFragmentRole.PROJECT, "project", projectPrompt));
        fragments.add(new PromptFragment(PromptFragmentRole.PRESET_TECHNICAL, "preset", ""));
        fragments.add(new PromptFragment(PromptFragmentRole.CONVERSATION_SUMMARY, "conversation", ""));
        fragments.add(new PromptFragment(PromptFragmentRole.USER_TASK, "task", ""));
        return new PromptStack(fragments);
    }
}
