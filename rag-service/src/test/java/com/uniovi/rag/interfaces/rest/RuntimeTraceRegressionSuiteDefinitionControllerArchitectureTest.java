package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
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
        packagesOf = RuntimeTraceRegressionSuiteDefinitionController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionControllerArchitectureTest {

    /*
     * FD-definition-arch-inventory (P56 + P57): @ArchTest members that enforce merged P50/P52/P53/P54/P55 and P57 rules:
     * controllerConstructorMatchesFd8,
     * importRunZipForDefinitionDoesNotTouchRunPersistence,
     * previewImportZipForDefinitionDoesNotTouchRunPersistenceOrImport,
     * exportRunZipForDefinitionDoesNotAccessRunPersistence,
     * deleteRunForDefinitionDoesNotCallDeleteRunForUser,
     * p56_definition_controller_mappings_use_definition_path_families,
     * definitionControllerDoesNotDependOnDefinitionDocumentZipServices,
     * p57_definition_controller_must_not_declare_global_def_zip_mappings,
     * controllerDoesNotDependOnP50ForbiddenFacadeTypes,
     * controllerDoesNotDependOnComparisonBatchService,
     * controllerDoesNotDependOnComparisonService,
     * controllerDoesNotDependOnReplayService,
     * controllerDoesNotDependOnTraceQueryService,
     * controllerDoesNotDependOnRepositories,
     * controllerDoesNotDependOnInfrastructurePersistence,
     * controllerDoesNotDependOnExportServices,
     * controllerDoesNotDependOnOrchestrator,
     * controllerDoesNotDependOnProcessQueryServices,
     * controllerDoesNotUseAsyncOrExecutors.
     */

    private static final Set<String> P50_P52_P53_FD_O_FORBIDDEN_SIMPLE_NAMES =
            Set.of(
                    "RuntimeTraceRegressionSuiteDefinitionRunQueryService",
                    "RuntimeTraceRegressionSuiteDefinitionRunReadFacade",
                    "RuntimeTraceRegressionSuiteDefinitionRunQueryOrchestrator",
                    "RuntimeTraceRegressionSuiteDefinitionRunQueryApplicationService",
                    "RuntimeTraceRegressionSuiteDefinitionRunDeletionSurfaceService",
                    "RuntimeTraceRegressionSuiteDefinitionRunDeleteFacade",
                    "RuntimeTraceRegressionSuiteDefinitionRunDeleteOrchestrator",
                    "RuntimeTraceRegressionSuiteDefinitionRunDeleteApplicationService",
                    "RuntimeTraceRegressionSuiteDefinitionRunExportFacade",
                    "RuntimeTraceRegressionSuiteDefinitionRunExportOrchestrator",
                    "RuntimeTraceRegressionSuiteDefinitionRunExportApplicationService",
                    "RuntimeTraceRegressionSuiteDefinitionRunZipService",
                    "RuntimeTraceRegressionSuiteDefinitionRunImportFacade",
                    "RuntimeTraceRegressionSuiteDefinitionRunImportOrchestrator",
                    "RuntimeTraceRegressionSuiteDefinitionRunZipImportService",
                    "RuntimeTraceRegressionSuiteDefinitionRunImportPreviewService");

    private static ArchCondition<JavaClass> doesNotDependOnP50Forbidden() {
        return new ArchCondition<>("not depend on P50/P52 FD-O-forbidden types") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                    String simple = dep.getTargetClass().getSimpleName();
                    if (P50_P52_P53_FD_O_FORBIDDEN_SIMPLE_NAMES.contains(simple)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        dep, clazz.getSimpleName() + " must not depend on " + simple));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> importRunZipForDefinitionDoesNotAccessRunPersistenceService() {
        return new ArchCondition<>("not access RuntimeTraceRegressionSuiteRunPersistenceService") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaAccess<?> access : method.getAccessesFromSelf()) {
                    if (access.getTargetOwner().isAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        access,
                                        method.getFullName()
                                                + " must not access "
                                                + RuntimeTraceRegressionSuiteRunPersistenceService.class.getSimpleName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> previewImportZipForDefinitionDoesNotAccessPersistenceOrImportService() {
        return new ArchCondition<>("not access run persistence or import service") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaAccess<?> access : method.getAccessesFromSelf()) {
                    if (access.getTargetOwner().isAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        access,
                                        method.getFullName()
                                                + " must not access "
                                                + RuntimeTraceRegressionSuiteRunPersistenceService.class.getSimpleName()));
                    }
                    if (access.getTargetOwner().isAssignableTo(RuntimeTraceRegressionSuiteRunImportService.class)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        access,
                                        method.getFullName()
                                                + " must not access "
                                                + RuntimeTraceRegressionSuiteRunImportService.class.getSimpleName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> exportRunZipForDefinitionDoesNotAccessRunPersistenceService() {
        return new ArchCondition<>("not access RuntimeTraceRegressionSuiteRunPersistenceService") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaAccess<?> access : method.getAccessesFromSelf()) {
                    if (access.getTargetOwner().isAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        access,
                                        method.getFullName()
                                                + " must not access "
                                                + RuntimeTraceRegressionSuiteRunPersistenceService.class.getSimpleName()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> deleteRunForDefinitionDoesNotCallDeleteRunForUser() {
        return new ArchCondition<>("not call deleteRunForUser on run persistence") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (call.getTargetOwner().isAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class)
                            && "deleteRunForUser".equals(call.getName())) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        call,
                                        method.getFullName()
                                                + " must not call "
                                                + RuntimeTraceRegressionSuiteRunPersistenceService.class.getSimpleName()
                                                + "#deleteRunForUser"));
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

    private static boolean isAllowedDefinitionSurfacePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        String[] segs =
                java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (segs.length == 0) {
            return false;
        }
        if ("runtime-trace-regression-suite-runs".equals(segs[0])) {
            return false;
        }
        if ("runtime-trace-regression-suite-definitions".equals(segs[0])) {
            return true;
        }
        return "conversations".equals(segs[0])
                && segs.length >= 3
                && "runtime-trace-regression-suite-definitions".equals(segs[2]);
    }

    @ArchTest
    static void p56_definition_controller_mappings_use_definition_path_families(JavaClasses imported) {
        JavaClass controller = imported.get(RuntimeTraceRegressionSuiteDefinitionController.class);
        for (JavaMethod method : controller.getMethods()) {
            if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            List<String> paths = springWebMappingPaths(method);
            if (paths.isEmpty()) {
                continue;
            }
            for (String p : paths) {
                assertThat(isAllowedDefinitionSurfacePath(p))
                        .as("Method %s maps disallowed path %s", method.getName(), p)
                        .isTrue();
            }
        }
    }

    @ArchTest
    static final ArchRule definitionControllerDoesNotDependOnDefinitionDocumentZipServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionImportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionImportPreviewService.class);

    @ArchTest
    static void p57_definition_controller_must_not_declare_global_def_zip_mappings(JavaClasses classes) {
        Set<String> forbiddenGlobalDefZipPaths =
                Set.of(
                        "/runtime-trace-regression-suite-definitions/{definitionId}/export",
                        "/runtime-trace-regression-suite-definitions/import",
                        "/runtime-trace-regression-suite-definitions/import/preview");
        for (Method m : RuntimeTraceRegressionSuiteDefinitionController.class.getDeclaredMethods()) {
            GetMapping gm = m.getAnnotation(GetMapping.class);
            if (gm != null) {
                String[] paths = gm.path().length > 0 ? gm.path() : gm.value();
                for (String p : paths) {
                    assertThat(forbiddenGlobalDefZipPaths)
                            .as(
                                    "Method %s must not declare P38/P39/P40 global definition ZIP path %s",
                                    m.getName(),
                                    p)
                            .doesNotContain(p);
                }
            }
            PostMapping pm = m.getAnnotation(PostMapping.class);
            if (pm != null) {
                String[] paths = pm.path().length > 0 ? pm.path() : pm.value();
                for (String p : paths) {
                    assertThat(forbiddenGlobalDefZipPaths)
                            .as(
                                    "Method %s must not declare P38/P39/P40 global definition ZIP path %s",
                                    m.getName(),
                                    p)
                            .doesNotContain(p);
                }
            }
        }
    }

    @ArchTest
    static final ArchRule controllerConstructorMatchesFd8 =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .should()
                    .haveRawParameterTypes(
                            RuntimeTraceRegressionSuiteDefinitionService.class.getName(),
                            RuntimeTraceRegressionSuiteService.class.getName(),
                            RuntimeTraceRegressionSuiteRunPersistenceService.class.getName(),
                            ObjectMapper.class.getName(),
                            String.class.getName(),
                            RuntimeTraceRegressionSuiteRunExportService.class.getName(),
                            RuntimeTraceRegressionSuiteRunImportService.class.getName(),
                            RuntimeTraceRegressionSuiteRunImportPreviewService.class.getName());

    @ArchTest
    static final ArchRule importRunZipForDefinitionDoesNotTouchRunPersistence =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .and()
                    .haveName("importRunZipForDefinition")
                    .should(importRunZipForDefinitionDoesNotAccessRunPersistenceService());

    @ArchTest
    static final ArchRule previewImportZipForDefinitionDoesNotTouchRunPersistenceOrImport =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .and()
                    .haveName("previewImportZipForDefinition")
                    .should(previewImportZipForDefinitionDoesNotAccessPersistenceOrImportService());

    @ArchTest
    static final ArchRule exportRunZipForDefinitionDoesNotAccessRunPersistence =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .and()
                    .haveName("exportRunZipForDefinition")
                    .should(exportRunZipForDefinitionDoesNotAccessRunPersistenceService());

    @ArchTest
    static final ArchRule deleteRunForDefinitionDoesNotCallDeleteRunForUser =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .and()
                    .haveName("deleteRunForDefinition")
                    .should(deleteRunForDefinitionDoesNotCallDeleteRunForUser());

    @ArchTest
    static final ArchRule controllerDoesNotDependOnP50ForbiddenFacadeTypes =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should(doesNotDependOnP50Forbidden());

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonBatchService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllerDoesNotDependOnExportServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

}
