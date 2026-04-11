package com.uniovi.rag.application.service.runtime.tracecomparison;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.tracecomparison",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayComparisonOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule comparisonDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(RuntimeExecutionTraceRepository.class.getName())
                    .because("P19 reads persisted traces only via RuntimeTraceQueryService");

    @ArchTest
    static final ArchRule comparisonDoesNotDependOnExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class)
                    .because("P19 must not use trace export artifacts or RuntimeTraceExportService");

    @ArchTest
    static final ArchRule comparisonDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class)
                    .because("P19 must not call RagExecutionOrchestrator");

    @ArchTest
    static final ArchRule comparisonDoesNotDependOnExecutionContextFactory =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ExecutionContextFactory.class)
                    .because("P19 must not use ExecutionContextFactory");

    @ArchTest
    static final ArchRule comparisonDoesNotDependOnProcessQueryFacades =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.uniovi.rag.service.query..")
                    .because("P19 must not invoke ProcessQueryService / SimpleProcessQueryService");

    @ArchTest
    static final ArchRule noRestControllersInP19Package =
            classes()
                    .that()
                    .resideInAnyPackage("..tracecomparison..")
                    .should()
                    .notBeAnnotatedWith(RestController.class)
                    .because("P19 is internal-only and adds no REST endpoints");
}
