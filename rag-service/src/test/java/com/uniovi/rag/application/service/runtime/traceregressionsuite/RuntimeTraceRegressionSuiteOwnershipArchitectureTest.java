package com.uniovi.rag.application.service.runtime.traceregressionsuite;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.traceregressionsuite",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceRegressionSuiteOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule suitePackageDoesNotDependOnInterfacesRest =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..interfaces.rest..");

    @ArchTest
    static final ArchRule suitePackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule suiteDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnP17Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnP21Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnReplayExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnBatchExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnComparisonBatchExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnRuntimeQueryExecutionService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule suiteDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule noRestControllersInSuitePackage =
            classes()
                    .that()
                    .resideInAnyPackage("..traceregressionsuite..")
                    .should()
                    .notBeAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule suiteServiceIsNotAsync =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteService")
                    .should()
                    .notBeAnnotatedWith(Async.class);

    @ArchTest
    static final ArchRule suiteServiceDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
