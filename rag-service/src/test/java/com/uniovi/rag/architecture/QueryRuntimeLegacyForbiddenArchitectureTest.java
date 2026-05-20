package com.uniovi.rag.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.chat.async.ChatMessageJobHandler;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards against reintroduction of deleted query/synthesis legacy packages and facades (Agent J5).
 */
@AnalyzeClasses(packages = "com.uniovi.rag", importOptions = ImportOption.DoNotIncludeTests.class)
class QueryRuntimeLegacyForbiddenArchitectureTest {

    private static final String FORBIDDEN_LEGACY_TYPE_NAME_PATTERN =
            "ProcessQueryService|SimpleProcessQueryService|SimpleQueryService|QueryInputPreparer|"
                    + "ResponseSynthesisPipeline|AnswerGenerationKernel|QueryRuntimeComponents|"
                    + "QueryRuntimeComponentsFactory";

    @ArchTest
    static final ArchRule no_production_classes_under_application_service_query =
            noClasses().should().resideInAnyPackage("com.uniovi.rag.application.service.query..");

    @ArchTest
    static final ArchRule deleted_legacy_query_types_must_not_exist =
            noClasses().should().haveNameMatching(FORBIDDEN_LEGACY_TYPE_NAME_PATTERN);

    @ArchTest
    static final ArchRule chat_message_handler_uses_runtime_query_execution =
            classes()
                    .that()
                    .haveSimpleName(ChatMessageJobHandler.class.getSimpleName())
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class)
                    .because("Chat async jobs must use the canonical RuntimeQueryExecutionService façade");
}
