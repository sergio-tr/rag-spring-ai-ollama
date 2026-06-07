package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview;

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
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionImportPreviewServiceArchitectureTest {

    /*
     * FD-def-zip-svc-arch-inventory — @ArchTest members:
     *   p58_def_zip_preview_no_run_layer_accesses_in_declared_bodies
     *   p58_def_zip_preview_no_run_layer_constructor_or_field_dependencies
     *   p58_def_zip_preview_public_preview_import_zip_signature_frozen
     *   previewPackageDoesNotDependOnInfrastructurePersistence
     *   previewServiceDoesNotDependOnDefinitionOrImportService
     *   previewServiceDoesNotDependOnFd28Types
     *   previewServiceDoesNotDependOnOrchestrator
     *   previewServiceDoesNotDependOnRuntimeQueryExecutionServices
     *   previewServiceDoesNotDependOnRepositoriesOrJdbcOrEntityManager
     *   previewServiceDoesNotUseAsyncOrExecutors
     *   previewServicePublicConstructorHasNoParameters
     */

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteDefinitionService",
                    "RuntimeTraceRegressionSuiteDefinitionImportService",
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportController",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExportController",
                    "RuntimeTraceRegressionSuiteDefinitionImportController",
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
    static final ArchRule previewServicePublicConstructorHasNoParameters =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class)
                    .and()
                    .arePublic()
                    .should()
                    .haveRawParameterTypes((Class<?>[]) new Class<?>[0]);

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnDefinitionOrImportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionService")
                    .orShould()
                    .dependOnClassesThat()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportService");

    @ArchTest
    static final ArchRule previewPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuitedefinitionimportpreview..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnRepositoriesOrJdbcOrEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JdbcTemplate.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(EntityManager.class);

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule previewServiceDoesNotDependOnRuntimeQueryExecutionServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule previewServiceDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportPreviewService")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static void p58_def_zip_preview_no_run_layer_constructor_or_field_dependencies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerDependenciesInConstructorsOrInstanceFields(service);
    }

    @ArchTest
    static void p58_def_zip_preview_no_run_layer_accesses_in_declared_bodies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerAccessesInDeclaredCodeUnits(service);
    }

    @ArchTest
    static void p58_def_zip_preview_public_preview_import_zip_signature_frozen(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class);
        JavaMethod method = service.getMethod("previewImportZip", byte[].class);
        assertThat(method.getModifiers()).contains(JavaModifier.PUBLIC);
        assertThat(method.getRawParameterTypes()).extracting(JavaClass::getName).containsExactly(byte[].class.getName());
        assertThat(method.getRawReturnType().getName())
                .isEqualTo(RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto.class.getName());
    }
}
