package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunImportPreviewService.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunImportPreviewServiceArchitectureTest {

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteRunPersistenceService",
                    "RuntimeTraceRegressionSuiteRunImportService",
                    "RuntimeTraceRegressionSuiteRunExportService",
                    "RuntimeTraceRegressionSuiteExportService",
                    "RuntimeTraceRegressionSuiteExportController",
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionService",
                    "RuntimeTraceRegressionSuiteDefinitionImportService",
                    "RuntimeTraceRegressionSuiteDefinitionImportPreviewService",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionImportController",
                    "RuntimeTraceRegressionSuiteDefinitionImportPreviewController",
                    "RagExecutionOrchestrator",
                    "ProcessQueryService",
                    "SimpleProcessQueryService",
                    "RuntimeTraceQueryService",
                    "RuntimeTraceReplayService",
                    "RuntimeTraceReplayComparisonService",
                    "RuntimeTraceReplayBatchService",
                    "RuntimeTraceReplayComparisonBatchService",
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
                    "TaskExecutor",
                    "AsyncTaskExecutor",
                    "ThreadPoolTaskExecutor",
                    "RuntimeTraceRegressionSuiteRunImportPreviewFacade",
                    "RuntimeTraceRegressionSuiteRunImportPreviewOrchestrator",
                    "RuntimeTraceRegressionSuiteRunImportPreviewApplicationService");

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
    static final ArchRule previewServicePublicConstructorHasNoInjectedBeans =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunImportPreviewService.class)
                    .and()
                    .arePublic()
                    .should()
                    .haveRawParameterTypes((Class<?>[]) new Class<?>[0]);

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewService")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule previewPackageMustNotDependOnRunPersistenceOrImportService =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuiterunimportpreview..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunPersistenceService")
                    .orShould()
                    .dependOnClassesThat()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportService");

    @ArchTest
    static final ArchRule previewPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuiterunimportpreview..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnRepositoriesOrEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewService")
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
    static final ArchRule previewServiceDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule previewServiceDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewService")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
