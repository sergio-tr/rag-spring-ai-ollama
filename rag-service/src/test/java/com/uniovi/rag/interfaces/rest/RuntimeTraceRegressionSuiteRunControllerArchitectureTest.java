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
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import jakarta.persistence.EntityManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerConstructorSinglePersistenceDependency =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunController.class)
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteRunPersistenceService.class.getName());

    @ArchTest
    static final ArchRule privateStaticMethodsAreParseUuidOnly =
            methods()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteRunController.class)
                    .and()
                    .arePrivate()
                    .and()
                    .areStatic()
                    .should()
                    .haveName("parseUuid");

    @ArchTest
    static final ArchRule controllerDoesNotDependOnSuiteService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnDefinitionService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnDefinitionImportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionImportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnDefinitionExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnDefinitionExecutionExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteDefinitionExecutionExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRegressionSuiteExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceRegressionSuiteExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayBatchExportService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayBatchExportService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnReplayComparisonBatchService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceReplayComparisonBatchService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTraceQueryService =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RuntimeTraceQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnProcessQueryServices =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessQueryService.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(SimpleProcessQueryService.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnTaskExecutor =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(TaskExecutor.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnAsyncExecutors =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(AsyncTaskExecutor.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(ThreadPoolTaskExecutor.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRepositories =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(Repository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(JpaRepository.class)
                    .orShould()
                    .dependOnClassesThat()
                    .areAssignableTo(CrudRepository.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnEntityManager =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(EntityManager.class);

    @ArchTest
    static final ArchRule controllerDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllerDoesNotUseAsync =
            noClasses()
                    .that()
                    .haveSimpleName("RuntimeTraceRegressionSuiteRunController")
                    .should()
                    .beAnnotatedWith(Async.class);
}
