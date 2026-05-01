package com.uniovi.rag.application.service.runtime.tracereplay;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.application.service.runtime.tracereplay",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceReplayOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule replayDoesNotDependOnTraceRepository =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplay..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(RuntimeExecutionTraceRepository.class.getName())
                    .because("P18 replay reads persisted traces only via RuntimeTraceQueryService");

    @ArchTest
    static final ArchRule replayDoesNotDependOnOrchestrator =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplay..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(RagExecutionOrchestrator.class)
                    .because("P18 replay must not call RagExecutionOrchestrator");

    @ArchTest
    static final ArchRule replayDoesNotDependOnExecutionContextFactory =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplay..")
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ExecutionContextFactory.class)
                    .because("P18 replay must not use ExecutionContextFactory as a black-box request builder");

    @ArchTest
    static final ArchRule replayDoesNotDependOnProcessQueryFacades =
            noClasses()
                    .that()
                    .resideInAnyPackage("..tracereplay..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.uniovi.rag.service.query..")
                    .because("P18 replay must not invoke ProcessQueryService / SimpleProcessQueryService");
}
