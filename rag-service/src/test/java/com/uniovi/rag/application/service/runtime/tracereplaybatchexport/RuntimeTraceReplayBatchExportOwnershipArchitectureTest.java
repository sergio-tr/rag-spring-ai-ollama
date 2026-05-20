package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.tracereplaybatchexport",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayBatchExportOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule exportServiceDependsOnBatchService =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchExportService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnComparisonBatchService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnTraceQueryExecutionService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnP17Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnP21ComparisonExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnP23ReplayExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnRuntimeQueryExecutionService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule exportDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule noRestControllersInExportPackage =
            classes()
                    .that()
                    .resideInAnyPackage("..tracereplaybatchexport..")
                    .should()
                    .notBeAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule exportServiceIsNotAsync =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchExportService")
                    .should()
                    .notBeAnnotatedWith(Async.class);
}
