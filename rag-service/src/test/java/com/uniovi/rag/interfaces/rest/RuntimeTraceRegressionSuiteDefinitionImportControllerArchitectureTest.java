package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteDefinitionImportController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionImportControllerArchitectureTest {

    /*
     * FD-def-zip-arch-inventory (P57): @ArchTest members:
     * controllerConstructorParameters,
     * p57_def_zip_import_controller_does_not_depend_on_run_zip_layer,
     * p57_def_zip_import_controller_single_post_mapping,
     * p57_def_zip_import_post_mapping_path_frozen,
     * p57_def_zip_import_class_request_mapping_frozen,
     * controllerDoesNotDependOnSpringDataOrEntityManager,
     * controllerDoesNotDependOnFd28Types,
     * controllerDoesNotDependOnInfrastructurePersistence,
     * controllerDoesNotUseAsyncOrExecutors.
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
    static final ArchRule controllerConstructorParameters =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionImportController.class)
                    .should()
                    .haveRawParameterTypes(
                            RuntimeTraceRegressionSuiteDefinitionImportService.class.getName(),
                            String.class.getName());

    @ArchTest
    static final ArchRule p57_def_zip_import_controller_does_not_depend_on_run_zip_layer =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteRunExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteRunImportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteRunImportPreviewService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class);

    @ArchTest
    static final ArchRule p57_def_zip_import_controller_single_post_mapping =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionImportController.class)
                    .and()
                    .areAnnotatedWith(PostMapping.class)
                    .should()
                    .haveName("importDefinitionZip");

    @ArchTest
    static void p57_def_zip_import_post_mapping_path_frozen(JavaClasses classes) {
        long count =
                Arrays.stream(RuntimeTraceRegressionSuiteDefinitionImportController.class.getDeclaredMethods())
                        .filter(
                                m -> {
                                    PostMapping pm = m.getAnnotation(PostMapping.class);
                                    if (pm == null) {
                                        return false;
                                    }
                                    String[] paths = pm.path().length > 0 ? pm.path() : pm.value();
                                    for (String p : paths) {
                                        if ("/runtime-trace-regression-suite-definitions/import".equals(p)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                        .count();
        assertThat(count).isEqualTo(1);
    }

    @ArchTest
    static void p57_def_zip_import_class_request_mapping_frozen(JavaClasses classes) {
        RequestMapping rm =
                RuntimeTraceRegressionSuiteDefinitionImportController.class.getAnnotation(RequestMapping.class);
        assertThat(rm).isNotNull();
        String[] paths = rm.path().length > 0 ? rm.path() : rm.value();
        assertThat(paths).containsExactly("${rag.api.product-base-path}");
    }

    @ArchTest
    static final ArchRule controllerDoesNotDependOnSpringDataOrEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportController")
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
    static final ArchRule controllerDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportController")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule controllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllerDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionImportController")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
