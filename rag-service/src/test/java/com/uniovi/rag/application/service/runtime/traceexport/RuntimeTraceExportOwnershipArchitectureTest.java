package com.uniovi.rag.application.service.runtime.traceexport;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.uniovi.rag",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class RuntimeTraceExportOwnershipArchitectureTest {

    @ArchTest
    static final ArchRule traceExportServiceDoesNotDependOnPersistenceLayer =
            noClasses()
                    .that()
                    .resideInAnyPackage("..application.service.runtime.traceexport..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.uniovi.rag.infrastructure.persistence..")
                    .because("P17 export must depend on the canonical query owner, not repositories/entities");
}

