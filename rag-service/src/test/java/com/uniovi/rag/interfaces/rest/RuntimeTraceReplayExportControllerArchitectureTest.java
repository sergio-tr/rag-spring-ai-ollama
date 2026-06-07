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
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceReplayExportController.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayExportControllerArchitectureTest {

    @ArchTest
    static final ArchRule exportControllerDependsOnReplayExportService =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnTraceQueryExecutionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnP17ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnComparisonExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnRuntimeQueryExecutionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
