package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunControllerArchitectureTest {

    private static final Set<String> FD28_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteDefinitionService",
                    "RuntimeTraceRegressionSuiteDefinitionImportService",
                    "RuntimeTraceRegressionSuiteDefinitionExportService",
                    "RuntimeTraceRegressionSuiteRunImportService",
                    "RuntimeTraceRegressionSuiteRunImportPreviewService",
                    "RuntimeTraceRegressionSuiteRunExportService",
                    "RuntimeTraceRegressionSuiteExportController",
                    "RuntimeTraceRegressionSuiteDefinitionImportController",
                    "RuntimeTraceRegressionSuiteDefinitionExportController",
                    "RuntimeTraceReplayService",
                    "RuntimeTraceReplayComparisonService",
                    "RuntimeTraceReplayBatchService",
                    "RuntimeTraceReplayComparisonBatchService",
                    "RuntimeTraceQueryService",
                    "RagExecutionOrchestrator",
                    "ProcessQueryService",
                    "SimpleProcessQueryService",
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
                    "RuntimeTraceRegressionSuiteRunCreationService",
                    "RuntimeTraceRegressionSuiteRunFacade",
                    "RuntimeTraceRegressionSuiteRunOrchestrator",
                    "RuntimeTraceRegressionSuiteRunApplicationService",
                    "RuntimeTraceRegressionSuiteRunDeletionSurfaceService",
                    "RuntimeTraceRegressionSuiteRunDeleteFacade",
                    "RuntimeTraceRegressionSuiteRunDeleteOrchestrator",
                    "RuntimeTraceRegressionSuiteRunDeleteApplicationService");

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

    private static List<String> springWebMappingPaths(JavaMethod method) {
        Method ref = method.reflect();
        List<String> out = new ArrayList<>();
        addPaths(out, ref.getAnnotation(GetMapping.class));
        addPaths(out, ref.getAnnotation(PostMapping.class));
        addPaths(out, ref.getAnnotation(PutMapping.class));
        addPaths(out, ref.getAnnotation(DeleteMapping.class));
        addPaths(out, ref.getAnnotation(PatchMapping.class));
        return out;
    }

    private static void addPaths(List<String> out, GetMapping a) {
        if (a == null) {
            return;
        }
        if (a.path().length > 0) {
            for (String p : a.path()) {
                out.add(p);
            }
        } else {
            for (String p : a.value()) {
                out.add(p);
            }
        }
    }

    private static void addPaths(List<String> out, PostMapping a) {
        if (a == null) {
            return;
        }
        if (a.path().length > 0) {
            for (String p : a.path()) {
                out.add(p);
            }
        } else {
            for (String p : a.value()) {
                out.add(p);
            }
        }
    }

    private static void addPaths(List<String> out, PutMapping a) {
        if (a == null) {
            return;
        }
        if (a.path().length > 0) {
            for (String p : a.path()) {
                out.add(p);
            }
        } else {
            for (String p : a.value()) {
                out.add(p);
            }
        }
    }

    private static void addPaths(List<String> out, DeleteMapping a) {
        if (a == null) {
            return;
        }
        if (a.path().length > 0) {
            for (String p : a.path()) {
                out.add(p);
            }
        } else {
            for (String p : a.value()) {
                out.add(p);
            }
        }
    }

    private static void addPaths(List<String> out, PatchMapping a) {
        if (a == null) {
            return;
        }
        if (a.path().length > 0) {
            for (String p : a.path()) {
                out.add(p);
            }
        } else {
            for (String p : a.value()) {
                out.add(p);
            }
        }
    }

    private static boolean isAllowedRunSurfacePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        if (path.startsWith("/runtime-trace-regression-suite-runs")) {
            return true;
        }
        return path.matches("^/conversations/[^/]+/runtime-trace-regression-suite-runs(/.*)?");
    }

    @ArchTest
    static void p56_run_controller_mappings_use_run_path_families_only(JavaClasses imported) {
        JavaClass controller = imported.get(RuntimeTraceRegressionSuiteRunController.class);
        for (JavaMethod method : controller.getMethods()) {
            if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            List<String> paths = springWebMappingPaths(method);
            if (paths.isEmpty()) {
                continue;
            }
            for (String p : paths) {
                assertThat(isAllowedRunSurfacePath(p))
                        .as("Method %s maps disallowed path %s", method.getName(), p)
                        .isTrue();
            }
        }
    }

    @ArchTest
    static final ArchRule controllerConstructorMatchesFd5 =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunController.class)
                    .should()
                    .haveRawParameterTypes(
                            RuntimeTraceRegressionSuiteRunPersistenceService.class.getName(),
                            RuntimeTraceRegressionSuiteService.class.getName(),
                            ObjectMapper.class.getName(),
                            String.class.getName());

    @ArchTest
    static final ArchRule privateStaticMethodsAreParseUuidOnly =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunController.class)
                    .and()
                    .arePrivate()
                    .and()
                    .areStatic()
                    .should()
                    .haveName("parseUuid");

    @ArchTest
    static final ArchRule controllerDoesNotDependOnFd28Types =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should(doesNotDependOnFd28Forbidden());

    @ArchTest
    static final ArchRule controllerDoesNotDependOnDefinitionServicesForFd28Matrix =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionImportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionExecutionExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRegressionSuiteExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonBatchService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(TaskExecutor.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnAsyncExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(CrudRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(EntityManager.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllerDoesNotUseAsync =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .beAnnotatedWith(Async.class);
}
