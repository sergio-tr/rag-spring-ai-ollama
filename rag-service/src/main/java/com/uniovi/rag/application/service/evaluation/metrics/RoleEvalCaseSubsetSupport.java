package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkRuntimeParametersSupport;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves and applies role-eval-case filters for LLM baseline runs. */
public final class RoleEvalCaseSubsetSupport {

    public static final String RUNTIME_ROLE_EVAL_MODE = "roleEvalMode";
    public static final String RUNTIME_ROLE_EVAL_SUBSET = "roleEvalSubset";
    public static final String RUNTIME_ROLE_EVAL_ROLE_FAMILY = "roleEvalRoleFamily";
    public static final String RUNTIME_ROLE_EVAL_ROLE_PROFILE = "roleEvalRoleProfile";

    public static final String AGG_KEY_ROLE_EVAL_MODE = "roleEvalMode";
    public static final String AGG_KEY_ROLE_EVAL_SUBSET = "roleEvalSubset";
    public static final String AGG_KEY_ROLE_EVAL_ROLE_FAMILY = "roleEvalRoleFamily";
    public static final String AGG_KEY_ROLE_EVAL_ROLE_PROFILE = "roleEvalRoleProfile";

    private RoleEvalCaseSubsetSupport() {}

    public static boolean isRoleEvalMode(StartBenchmarkRunRequest request) {
        if (request == null) {
            return false;
        }
        if (readRuntimeFlag(request.benchmarkRuntimeParameters(), RUNTIME_ROLE_EVAL_MODE)) {
            return true;
        }
        if (hasRoleEvalSelector(request.benchmarkRuntimeParameters())) {
            return true;
        }
        return !request.datasetQuestionIds().isEmpty()
                && request.datasetQuestionIds().stream().allMatch(RoleEvalCaseSubsetSupport::looksLikeRoleCaseId);
    }

    public static boolean isRoleEvalMode(EvaluationRunEntity run) {
        if (run == null) {
            return false;
        }
        Map<String, Object> runtime = BenchmarkRuntimeParametersSupport.readFromRun(run);
        if (readRuntimeFlag(runtime, RUNTIME_ROLE_EVAL_MODE)) {
            return true;
        }
        if (hasRoleEvalSelector(runtime)) {
            return true;
        }
        if (Boolean.TRUE.equals(run.getAggregatesJson() != null ? run.getAggregatesJson().get(AGG_KEY_ROLE_EVAL_MODE) : null)) {
            return true;
        }
        return DatasetQuestionSubsetSupport.readFromRun(run)
                .filter(s -> !s.allQuestions())
                .map(s -> s.questionIds().stream().allMatch(RoleEvalCaseSubsetSupport::looksLikeRoleCaseId))
                .orElse(false);
    }

    public static boolean looksLikeRoleCaseId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String trimmed = id.trim().toUpperCase(Locale.ROOT);
        return trimmed.startsWith("LLM-RW-")
                || trimmed.startsWith("LLM-EX-")
                || trimmed.startsWith("LLM-JS-")
                || trimmed.startsWith("LLM-NER-")
                || trimmed.startsWith("LLM-MEM-");
    }

    public static RoleEvalFilter resolve(StartBenchmarkRunRequest request) {
        Map<String, Object> runtime =
                request != null && request.benchmarkRuntimeParameters() != null
                        ? request.benchmarkRuntimeParameters()
                        : Map.of();
        List<String> caseIds =
                request != null && !request.datasetQuestionIds().isEmpty()
                        ? List.copyOf(request.datasetQuestionIds())
                        : List.of();
        return new RoleEvalFilter(
                readString(runtime, RUNTIME_ROLE_EVAL_SUBSET),
                readString(runtime, RUNTIME_ROLE_EVAL_ROLE_FAMILY),
                readString(runtime, RUNTIME_ROLE_EVAL_ROLE_PROFILE),
                caseIds);
    }

    public static RoleEvalFilter resolveFilterFromRun(EvaluationRunEntity run) {
        Map<String, Object> runtime = BenchmarkRuntimeParametersSupport.readFromRun(run);
        Map<String, Object> agg = run != null && run.getAggregatesJson() != null ? run.getAggregatesJson() : Map.of();
        String subset = firstNonBlank(readString(runtime, RUNTIME_ROLE_EVAL_SUBSET), str(agg.get(AGG_KEY_ROLE_EVAL_SUBSET)));
        String roleFamily =
                firstNonBlank(readString(runtime, RUNTIME_ROLE_EVAL_ROLE_FAMILY), str(agg.get(AGG_KEY_ROLE_EVAL_ROLE_FAMILY)));
        String roleProfile =
                firstNonBlank(readString(runtime, RUNTIME_ROLE_EVAL_ROLE_PROFILE), str(agg.get(AGG_KEY_ROLE_EVAL_ROLE_PROFILE)));
        List<String> caseIds =
                DatasetQuestionSubsetSupport.readFromRun(run).map(s -> s.questionIds()).orElse(List.of());
        return new RoleEvalFilter(subset, roleFamily, roleProfile, caseIds);
    }

    public static Optional<RoleEvalFilter> readFromRun(EvaluationRunEntity run) {
        if (run == null || !isRoleEvalMode(run)) {
            return Optional.empty();
        }
        return Optional.of(resolveFilterFromRun(run));
    }

    public static List<LlmRoleEvalCase> filter(List<LlmRoleEvalCase> cases, RoleEvalFilter filter) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        if (filter == null || filter.unrestricted()) {
            return List.copyOf(cases);
        }
        Map<String, LlmRoleEvalCase> byId = new LinkedHashMap<>();
        for (LlmRoleEvalCase c : cases) {
            if (c != null) {
                byId.put(c.caseId(), c);
            }
        }
        if (!filter.caseIds().isEmpty()) {
            return resolveExplicitCases(byId, filter.caseIds());
        }
        List<LlmRoleEvalCase> out = new ArrayList<>();
        for (LlmRoleEvalCase c : cases) {
            if (matches(c, filter)) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    public static void applyToAggregates(Map<String, Object> agg, StartBenchmarkRunRequest request) {
        if (agg == null || request == null || !isRoleEvalMode(request)) {
            return;
        }
        agg.put(AGG_KEY_ROLE_EVAL_MODE, true);
        RoleEvalFilter filter = resolve(request);
        if (filter.subset() != null && !filter.subset().isBlank()) {
            agg.put(AGG_KEY_ROLE_EVAL_SUBSET, filter.subset());
        }
        if (filter.roleFamily() != null && !filter.roleFamily().isBlank()) {
            agg.put(AGG_KEY_ROLE_EVAL_ROLE_FAMILY, filter.roleFamily());
        }
        if (filter.roleProfile() != null && !filter.roleProfile().isBlank()) {
            agg.put(AGG_KEY_ROLE_EVAL_ROLE_PROFILE, filter.roleProfile());
        }
    }

    public static Integer resolvedItemCount(StartBenchmarkRunRequest request) {
        if (request == null || !isRoleEvalMode(request)) {
            return null;
        }
        if (!request.datasetQuestionIds().isEmpty()) {
            return request.datasetQuestionIds().size();
        }
        return null;
    }

    private static List<LlmRoleEvalCase> resolveExplicitCases(
            Map<String, LlmRoleEvalCase> byId, List<String> caseIds) {
        List<LlmRoleEvalCase> explicit = new ArrayList<>();
        for (String id : caseIds) {
            LlmRoleEvalCase row = byId.get(id);
            if (row == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown llm_role_eval_cases case_id: " + id);
            }
            explicit.add(row);
        }
        return List.copyOf(explicit);
    }

    private static boolean matches(LlmRoleEvalCase c, RoleEvalFilter filter) {
        if (filter.subset() != null && !filter.subset().isBlank() && !filter.subset().equalsIgnoreCase(c.subset())) {
            return false;
        }
        if (filter.roleFamily() != null
                && !filter.roleFamily().isBlank()
                && !filter.roleFamily().equalsIgnoreCase(c.roleFamily())) {
            return false;
        }
        if (filter.roleProfile() != null
                && !filter.roleProfile().isBlank()
                && !filter.roleProfile().equalsIgnoreCase(c.roleProfile())) {
            return false;
        }
        return true;
    }

    private static boolean hasRoleEvalSelector(Map<String, Object> runtime) {
        if (runtime == null || runtime.isEmpty()) {
            return false;
        }
        return readString(runtime, RUNTIME_ROLE_EVAL_SUBSET) != null
                || readString(runtime, RUNTIME_ROLE_EVAL_ROLE_FAMILY) != null
                || readString(runtime, RUNTIME_ROLE_EVAL_ROLE_PROFILE) != null;
    }

    private static boolean readRuntimeFlag(Map<String, Object> runtime, String key) {
        if (runtime == null || key == null) {
            return false;
        }
        Object raw = runtime.get(key);
        return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
    }

    private static String readString(Map<String, Object> runtime, String key) {
        if (runtime == null || key == null) {
            return null;
        }
        Object raw = runtime.get(key);
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    public record RoleEvalFilter(String subset, String roleFamily, String roleProfile, List<String> caseIds) {

        public RoleEvalFilter {
            caseIds = caseIds != null ? List.copyOf(caseIds) : List.of();
        }

        public boolean unrestricted() {
            return (subset == null || subset.isBlank())
                    && (roleFamily == null || roleFamily.isBlank())
                    && (roleProfile == null || roleProfile.isBlank())
                    && caseIds.isEmpty();
        }
    }
}
