package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolApplicability;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolBenchmarkContext;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalSource;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.uniovi.rag.domain.model.QueryType;

/** Proposes function calls from deterministic query-shape evidence and query plan fields. */
@Component
public class BackendFunctionCallProposer {

    public FunctionCallProposal propose(QueryPlan plan, FunctionCallingDecision decision) {
        Set<DeterministicToolKind> allowed =
                decision.exposedToolKinds().stream().collect(Collectors.toSet());

        Optional<DeterministicToolKind> oracleKind = oracleKindIfEnabled(plan);
        if (oracleKind.isPresent()) {
            return buildProposal(
                    oracleKind.get(),
                    plan,
                    allowed,
                    FunctionProposalSource.LAB_ORACLE,
                    DeterministicEvidenceLevel.ORACLE);
        }

        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        if (!evaluation.toolApplicabilityEligible() || evaluation.matchedKinds().isEmpty()) {
            return FunctionCallProposal.none("no_applicable_tool");
        }
        Optional<DeterministicToolKind> kind = evaluation.singleKind();
        if (kind.isEmpty()) {
            return FunctionCallProposal.none("ambiguous_tool_kind");
        }
        if (!allowed.contains(kind.get())) {
            return FunctionCallProposal.none("tool_not_exposed");
        }
        if (!DeterministicToolEvidenceEvaluator.requiredArgumentsPresent(kind.get(), plan)) {
            return FunctionCallProposal.none("missing_required_slots");
        }
        return buildProposal(
                kind.get(),
                plan,
                allowed,
                FunctionProposalSource.QUERY_SHAPE,
                evaluation.evidenceLevel());
    }

    public FunctionCallProposal proposeFromOptionalModelJson(
            String modelJson, DeterministicToolKind kind, QueryPlan plan, FunctionCallingDecision decision) {
        Set<DeterministicToolKind> allowed =
                decision.exposedToolKinds().stream().collect(Collectors.toSet());
        if (!allowed.contains(kind)) {
            return FunctionCallProposal.none("tool_not_exposed");
        }
        FunctionCallSchemaValidator.ValidationWithRepairResult validation =
                FunctionCallSchemaValidator.validateWithOptionalRepair(modelJson, kind, plan);
        String functionName = ToolDescriptor.getName(kind.toQueryType());
        return new FunctionCallProposal(
                FunctionProposalMode.MODEL_JSON,
                functionName,
                Optional.of(kind),
                validation.argumentsJson(),
                validation.valid(),
                validation.valid() ? Optional.empty() : Optional.of(validation.validationError()),
                validation.repairAttempted(),
                validation.repairSucceeded(),
                Optional.empty(),
                Optional.empty(),
                FunctionProposalSource.MODEL_LLM);
    }

    private static FunctionCallProposal buildProposal(
            DeterministicToolKind kind,
            QueryPlan plan,
            Set<DeterministicToolKind> allowed,
            FunctionProposalSource source,
            DeterministicEvidenceLevel evidenceLevel) {
        if (!allowed.contains(kind)) {
            return FunctionCallProposal.none("tool_not_exposed");
        }
        if (!DeterministicToolEvidenceEvaluator.requiredArgumentsPresent(kind, plan)) {
            return FunctionCallProposal.none("missing_required_slots");
        }
        String functionName = ToolDescriptor.getName(kind.toQueryType());
        String arguments;
        try {
            arguments = FunctionCallArgumentBuilder.buildJson(kind, plan);
        } catch (IllegalArgumentException e) {
            return FunctionCallProposal.none(e.getMessage());
        }
        String validationError = FunctionCallSchemaValidator.validationError(arguments, kind, plan);
        boolean valid = validationError.isBlank();
        return new FunctionCallProposal(
                FunctionProposalMode.BACKEND_DETERMINISTIC,
                functionName,
                Optional.of(kind),
                arguments,
                valid,
                valid ? Optional.empty() : Optional.of(validationError),
                false,
                false,
                confidenceFor(evidenceLevel),
                Optional.of(evidenceLevel),
                source);
    }

    private static Optional<DeterministicToolKind> oracleKindIfEnabled(QueryPlan plan) {
        if (!DeterministicToolBenchmarkContext.routingOracleEnabled()) {
            return Optional.empty();
        }
        return DeterministicToolBenchmarkContext.expectedQueryType()
                .flatMap(
                        qt -> {
                            try {
                                return DeterministicToolApplicability.toolKindForQueryType(
                                        QueryType.valueOf(qt.trim()));
                            } catch (IllegalArgumentException e) {
                                return Optional.empty();
                            }
                        });
    }

    private static Optional<Double> confidenceFor(DeterministicEvidenceLevel level) {
        return switch (level) {
            case ORACLE, STRONG -> Optional.of(1.0);
            case WEAK -> Optional.of(0.5);
            case NONE -> Optional.of(0.0);
        };
    }
}
