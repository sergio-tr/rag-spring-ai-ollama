package com.uniovi.rag.application.service.runtime.tracereplaybatch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.tracereplaybatch",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayBatchOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule batchPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule batchDoesNotDependOnComparisonBatch =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnP17Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnP21Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnReplayExport =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnProcessQueryService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnSimpleProcessQueryService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule noRestControllersInBatchPackage =
            classes()
                    .that()
                    .resideInAnyPackage("..tracereplaybatch..")
                    .should()
                    .notBeAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule batchServiceIsNotAsync =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchService")
                    .should()
                    .notBeAnnotatedWith(Async.class);

    @ArchTest
    static final ArchRule batchServiceDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayBatchService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
