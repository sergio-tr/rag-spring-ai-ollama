package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionDtoArchitectureTest {

    @ArchTest
    static final ArchRule dtoPackageDoesNotDependOnInfrastructurePersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..dto.traceregressionsuitedefinition..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule dtoPackageDoesNotDependOnJakartaPersistence =
            noClasses()
                    .that()
                    .resideInAPackage("..dto.traceregressionsuitedefinition..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..jakarta.persistence..");
}
