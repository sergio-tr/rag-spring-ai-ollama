package com.uniovi.rag.application.service.runtime.tracecomparisonbatch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
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
        packages = "com.uniovi.rag.application.service.runtime.tracecomparisonbatch",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayComparisonBatchOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule batchPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule batchDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnP17Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnP21Export =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnRuntimeQueryExecutionService =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule batchDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule noRestControllersInBatchPackage =
            classes()
                    .that()
                    .resideInAnyPackage("..tracecomparisonbatch..")
                    .should()
                    .notBeAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule batchServiceIsNotAsync =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonBatchService")
                    .should()
                    .notBeAnnotatedWith(Async.class);

    @ArchTest
    static final ArchRule batchServiceDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceReplayComparisonBatchService")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);
}
