package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.service.query.ProcessQueryService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceReplayBatchController.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayBatchControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerDependsOnBatchService =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnP17ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnP21ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnProcessQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerMethodsAreNotAsync =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceReplayBatchController.class)
                    .should()
                    .notBeAnnotatedWith(Async.class);

    @ArchTest
    static final ArchRule controllerDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static final ArchRule dtoTracereplaybatchDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .resideInAPackage("..interfaces.rest.dto.tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);
}
