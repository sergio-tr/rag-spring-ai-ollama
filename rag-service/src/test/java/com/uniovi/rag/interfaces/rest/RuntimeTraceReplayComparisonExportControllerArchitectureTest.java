package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceReplayComparisonExportController.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayComparisonExportControllerArchitectureTest {

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnP17ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule exportControllerDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonExportController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);
}
