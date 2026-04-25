package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag.domain.runtime.traceregressionsuitedefinition",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionDomainArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure =
            noClasses()
                    .that()
                    .resideInAnyPackage("..domain.runtime.traceregressionsuitedefinition..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnInterfacesRest =
            noClasses()
                    .that()
                    .resideInAnyPackage("..domain.runtime.traceregressionsuitedefinition..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..interfaces.rest..");
}
