package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparator;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.tracecomparisonexport",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayComparisonExportOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule exportDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(RuntimeExecutionTraceRepository.class.getName());

    @ArchTest
    static final ArchRule exportDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnComparator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparator.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnP17Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule noRestControllersInExportPackage =
            classes()
                    .that()
                    .resideInAnyPackage("..tracecomparisonexport..")
                    .should()
                    .notBeAnnotatedWith(RestController.class);
}
