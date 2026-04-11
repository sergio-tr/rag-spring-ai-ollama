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
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteDefinitionController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerConstructorMatchesP36Adapter =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionController.class)
                    .should()
                    .haveRawParameterTypes(
                            RuntimeTraceRegressionSuiteDefinitionService.class.getName(),
                            RuntimeTraceRegressionSuiteService.class.getName(),
                            ObjectMapper.class.getName(),
                            String.class.getName());

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonBatchService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllerDoesNotDependOnExportServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotUseAsyncOrExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteDefinitionController")
                    .should()
                    .beAnnotatedWith(Async.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

}
