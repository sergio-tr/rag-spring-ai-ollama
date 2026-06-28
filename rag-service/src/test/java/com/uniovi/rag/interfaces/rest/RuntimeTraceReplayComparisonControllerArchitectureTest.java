package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceReplayComparisonController.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayComparisonControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryExecutionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);
}
