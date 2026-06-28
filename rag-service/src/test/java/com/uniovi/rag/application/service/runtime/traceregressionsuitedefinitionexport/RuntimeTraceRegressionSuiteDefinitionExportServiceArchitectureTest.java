package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
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
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteDefinitionExportService.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionExportServiceArchitectureTest {

    /*
     * FD-def-zip-svc-arch-inventory — @ArchTest members:
     *   exportPackageDoesNotDependOnInfrastructurePersistence
     *   exportServiceDoesNotDependOnFd28Types
     *   exportServiceDoesNotDependOnOrchestrator
     *   exportServiceDoesNotDependOnRuntimeQueryExecutionServices
     *   exportServiceDoesNotDependOnRepositories
     *   exportServiceDoesNotUseAsyncOrExecutors
     *   exportServicePublicConstructorInjectsOnlyDefinitionService
     *   p58_def_zip_export_no_run_layer_accesses_in_declared_bodies
     *   p58_def_zip_export_no_run_layer_constructor_or_field_dependencies
     *   p58_def_zip_export_package_private_constructor_for_tests_frozen
     *   p58_def_zip_export_public_export_definition_zip_signature_frozen
     */

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportService",
                    "RuntimeTraceRegressionSuiteDefinitionExecutionExportController",
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
    static final ArchRule exportServicePublicConstructorInjectsOnlyDefinitionService =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionExportService.class)
                    .and()
                    .arePublic()
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteDefinitionService.class.getName());

    @ArchTest
    static final ArchRule exportServiceDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionExportService")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule exportPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..traceregressionsuitedefinitionexport..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule exportServiceDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionExportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule exportServiceDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionExportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule exportServiceDoesNotDependOnRuntimeQueryExecutionServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionExportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule exportServiceDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionExportService")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static void p58_def_zip_export_package_private_constructor_for_tests_frozen(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionExportService.class);
        String definitionServiceName = RuntimeTraceRegressionSuiteDefinitionService.class.getName();
        List<JavaConstructor> packagePrivateTestCtors =
                service.getConstructors().stream()
                        .filter(
                                c ->
                                        c.getModifiers().stream()
                                                .noneMatch(
                                                        m ->
                                                                m.equals(JavaModifier.PUBLIC)
                                                                        || m.equals(JavaModifier.PROTECTED)
                                                                        || m.equals(
                                                                                JavaModifier.PRIVATE)))
                        .filter(
                                c ->
                                        c.getRawParameterTypes().size() == 2
                                                && definitionServiceName.equals(
                                                        c.getRawParameterTypes().get(0).getName())
                                                && "long"
                                                        .equals(
                                                                c.getRawParameterTypes()
                                                                        .get(1)
                                                                        .getName()))
                        .toList();
        assertThat(packagePrivateTestCtors).hasSize(1);
    }

    @ArchTest
    static void p58_def_zip_export_no_run_layer_constructor_or_field_dependencies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionExportService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerDependenciesInConstructorsOrInstanceFields(service);
    }

    @ArchTest
    static void p58_def_zip_export_no_run_layer_accesses_in_declared_bodies(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionExportService.class);
        DefinitionZipServiceP58ArchAssertions.assertNoRunLayerAccessesInDeclaredCodeUnits(service);
    }

    @ArchTest
    static void p58_def_zip_export_public_export_definition_zip_signature_frozen(JavaClasses classes) {
        JavaClass service = classes.get(RuntimeTraceRegressionSuiteDefinitionExportService.class);
        JavaMethod method = service.getMethod("exportDefinitionZip", UUID.class, UUID.class);
        assertThat(method.getModifiers()).contains(JavaModifier.PUBLIC);
        assertThat(method.getRawParameterTypes())
                .extracting(JavaClass::getName)
                .containsExactly(UUID.class.getName(), UUID.class.getName());
        assertThat(method.getRawReturnType().getName())
                .isEqualTo(RuntimeTraceRegressionSuiteDefinitionExportArtifact.class.getName());
    }
}
