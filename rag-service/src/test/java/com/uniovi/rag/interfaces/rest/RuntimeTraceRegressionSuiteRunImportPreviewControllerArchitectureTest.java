package com.uniovi.rag.interfaces.rest;

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
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunImportPreviewController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunImportPreviewControllerArchitectureTest {

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
                    "RuntimeQueryExecutionService",
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
                    "JpaRepository",
                    "CrudRepository",
                    "EntityManager",
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
    static final ArchRule previewControllerConstructorParameters =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunImportPreviewController.class)
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteRunImportPreviewService.class.getName());

    @ArchTest
    static final ArchRule p56_global_preview_controller_single_post_mapping_path =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunImportPreviewController.class)
                    .and()
                    .areAnnotatedWith(PostMapping.class)
                    .should()
                    .haveName("previewImportZip");

    @ArchTest
    static void p56_global_preview_controller_post_mapping_value_frozen(JavaClasses classes) {
        long count =
                Arrays.stream(RuntimeTraceRegressionSuiteRunImportPreviewController.class.getDeclaredMethods())
                        .filter(
                                m -> {
                                    PostMapping pm = m.getAnnotation(PostMapping.class);
                                    if (pm == null) {
                                        return false;
                                    }
                                    String[] paths = pm.path().length > 0 ? pm.path() : pm.value();
                                    for (String p : paths) {
                                        if ("/runtime-trace-regression-suite-runs/import/preview".equals(p)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                        .count();
        assertThat(count).isEqualTo(1);
    }

    @ArchTest
    static void p56_global_preview_controller_never_calls_preview_import_zip_for_definition(JavaClasses classes) {
        JavaClass ctrl = classes.get(RuntimeTraceRegressionSuiteRunImportPreviewController.class);
        for (JavaMethod m : ctrl.getMethods()) {
            if (!m.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            boolean bad =
                    m.getMethodCallsFromSelf().stream()
                            .anyMatch(c -> "previewImportZipForDefinition".equals(c.getName()));
            assertThat(bad)
                    .as("Method %s must not invoke previewImportZipForDefinition", m.getName())
                    .isFalse();
        }
    }

    @ArchTest
    static final ArchRule previewControllerDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewController")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule previewControllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule previewControllerDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunImportPreviewController")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
