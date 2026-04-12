package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uniovi.rag.application.arch.DefinitionZipServiceP58ArchAssertions;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
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
        packagesOf = RuntimeTraceRegressionSuiteDefinitionImportService.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionImportServiceArchitectureTest {

    /*
     * FD-def-zip-svc-arch-inventory — @ArchTest members:
     *   importPackageDoesNotDependOnInfrastructurePersistence
     *   importServiceDoesNotDependOnFd28Types
     *   importServiceDoesNotDependOnOrchestrator
     *   importServiceDoesNotDependOnProcessQueryServices
     *   importServiceDoesNotDependOnRepositories
     *   importServiceDoesNotUseAsyncOrExecutors
     *   importServicePublicConstructorInjectsOnlyDefinitionService
     *   p58_def_zip_import_no_run_layer_accesses_in_declared_bodies
     *   p58_def_zip_import_no_run_layer_constructor_or_field_dependencies
     *   p58_def_zip_import_public_import_definition_zip_signature_frozen
     */

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportController",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExportController",
                    "RuntimeTraceExportService",
                    "RuntimeTraceExportController",
                    "RuntimeTraceRegressionSuiteExportService",
                    "RuntimeTraceRegressionSuiteExportController",
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
                    "ProcessQueryService",
                    "SimpleProcessQueryService",
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
    static final ArchRule importServicePublicConstructorInjectsOnlyDefinitionService =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionImportService.class)
                    .and()
                    .arePublic()
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteDefinitionService.class.getName());

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule importPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuitedefinitionimport..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule importServiceDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule importServiceDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static void p58_def_zip_import_no_run_layer_constructor_or_field_dependencies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerDependenciesInConstructorsOrInstanceFields(service);
    }

    @ArchTest
    static void p58_def_zip_import_no_run_layer_accesses_in_declared_bodies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerAccessesInDeclaredCodeUnits(service);
    }

    @ArchTest
    static void p58_def_zip_import_public_import_definition_zip_signature_frozen(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportService.class);
        JavaMethod method = service.getMethod("importDefinitionZip", byte[].class, UUID.class);
        assertThat(method.getModifiers()).contains(JavaModifier.PUBLIC);
        assertThat(method.getRawParameterTypes())
                .extracting(JavaClass::getName)
                .containsExactly(byte[].class.getName(), UUID.class.getName());
        assertThat(method.getRawReturnType().getName()).isEqualTo(UUID.class.getName());
    }
}
