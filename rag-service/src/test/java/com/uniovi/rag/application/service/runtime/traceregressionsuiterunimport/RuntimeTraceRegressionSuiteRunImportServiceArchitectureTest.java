package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Set;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunImportService.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunImportServiceArchitectureTest {

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionService",
                    "RuntimeTraceRegressionSuiteDefinitionImportService",
                    "RuntimeTraceRegressionSuiteDefinitionImportPreviewService",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExportController",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportController",
                    "RuntimeTraceRegressionSuiteExportService",
                    "RuntimeTraceRegressionSuiteExportController",
                    "RuntimeTraceRegressionSuiteRunExportService",
                    "RuntimeTraceRegressionSuiteRunExportController",
                    "RuntimeTraceExportService",
                    "RuntimeTraceExportController",
                    "RuntimeTraceReplayExportService",
                    "RuntimeTraceReplayExportController",
                    "RuntimeTraceReplayComparisonExportService",
                    "RuntimeTraceReplayComparisonExportController",
                    "RuntimeTraceReplayComparisonBatchExportService",
                    "RuntimeTraceReplayComparisonBatchExportController",
                    "RuntimeTraceReplayBatchExportService",
                    "RuntimeTraceReplayBatchExportController",
                    "RuntimeTraceReplayService",
                    "RuntimeTraceReplayComparisonService",
                    "RuntimeTraceReplayComparisonBatchService",
                    "RuntimeTraceReplayBatchService",
                    "RuntimeTraceQueryService",
                    "RagExecutionOrchestrator",
                    "RuntimeQueryExecutionService",
                                        "TaskExecutor",
                    "AsyncTaskExecutor",
                    "ThreadPoolTaskExecutor",
                    "RuntimeTraceRegressionSuiteRunImportFacade",
                    "RuntimeTraceRegressionSuiteRunImportOrchestrator",
                    "RuntimeTraceRegressionSuiteRunWriteService",
                    "RuntimeTraceRegressionSuiteRunMutationService",
                    "RuntimeTraceRegressionSuiteDefinitionRunImportFacade",
                    "RuntimeTraceRegressionSuiteDefinitionRunImportOrchestrator",
                    "RuntimeTraceRegressionSuiteDefinitionRunZipImportService");

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
    static final ArchRule importServicePublicConstructorInjectsOnlyRunPersistenceService =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunImportService.class)
                    .and()
                    .arePublic()
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteRunPersistenceService.class.getName());

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule importPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuiterunimport..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnRepositoriesOrEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(CrudRepository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(EntityManager.class);

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnDefinitionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionService.class);

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnRuntimeQueryExecutionServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule importServiceDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static void p56_import_run_zip_does_not_delegate_to_import_run_zip_for_definition(JavaClasses classes) {
        JavaClass svc = classes.get(RuntimeTraceRegressionSuiteRunImportService.class);
        JavaMethod method = svc.getMethod("importRunZip", byte[].class, UUID.class);
        assertThat(
                        method.getMethodCallsFromSelf().stream()
                                .anyMatch(c -> "importRunZipForDefinition".equals(c.getName())))
                .isFalse();
    }
}
