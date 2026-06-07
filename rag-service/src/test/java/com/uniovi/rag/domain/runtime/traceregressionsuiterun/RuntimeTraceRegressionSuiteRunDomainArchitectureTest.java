package com.uniovi.rag.domain.runtime.traceregressionsuiterun;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunId.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunDomainArchitectureTest {

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionService",
                    "RuntimeTraceRegressionSuiteDefinitionImportService",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportService",
                    "RuntimeTraceRegressionSuiteExportService",
                    "RuntimeTraceExportService",
                    "RuntimeTraceReplayExportService",
                    "RuntimeTraceReplayComparisonExportService",
                    "RuntimeTraceReplayComparisonBatchExportService",
                    "RuntimeTraceReplayBatchExportService",
                    "RuntimeTraceReplayService",
                    "RuntimeTraceReplayComparisonService",
                    "RuntimeTraceReplayComparisonBatchService",
                    "RuntimeTraceQueryService",
                    "RagExecutionOrchestrator",
                    "RuntimeQueryExecutionService",
                                        "TaskExecutor",
                    "AsyncTaskExecutor",
                    "ThreadPoolTaskExecutor");

    private static ArchCondition<JavaClass> doesNotDependOnFd28Forbidden() {
        return new ArchCondition<>("not depend on FD28 forbidden types") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                    String simple = dep.getTargetClass().getSimpleName();
                    if (FD28_FORBIDDEN_SIMPLE_NAMES.contains(simple)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        dep, clazz.getSimpleName() + " must not depend on " + simple));
                    }
                }
            }
        };
    }

    @ArchTest
    static final ArchRule domainPackageDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .resideInAPackage("..domain.runtime.traceregressionsuiterun..")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule domainPackageDoesNotDependOnInterfacesRest =
            noClasses()
                    .that()
                    .resideInAPackage("..domain.runtime.traceregressionsuiterun..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.uniovi.rag.interfaces.rest..");
}
