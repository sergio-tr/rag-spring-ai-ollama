package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteRunDtoArchitectureTest {

    @ArchTest
    static final ArchRule dtosDoNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..dto.traceregressionsuiterun..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule dtosDoNotDependOnJakartaPersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..dto.traceregressionsuiterun..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..jakarta.persistence..");

    @ArchTest
    static final ArchRule dtosDoNotDependOnSpringData =
            noClasses()
                    .that()
                    .resideInAPackage("..dto.traceregressionsuiterun..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..org.springframework.data..");
}
