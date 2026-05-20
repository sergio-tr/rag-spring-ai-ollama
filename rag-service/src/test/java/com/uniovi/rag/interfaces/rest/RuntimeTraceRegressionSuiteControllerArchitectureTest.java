package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteController.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceRegressionSuiteControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerDependsOnSuiteService =
            classes()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonBatchService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryExecutionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeExecutionTraceRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnJpaRepository =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnP17ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnP21ExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRuntimeQueryExecutionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeQueryExecutionService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerMethodsAreNotAsync =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteController.class)
                    .should()
                    .notBeAnnotatedWith(Async.class);

    @ArchTest
    static final ArchRule controllerDoesNotUseTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static final ArchRule controllerDoesNotUseAsyncTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class);
}
