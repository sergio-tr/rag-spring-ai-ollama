package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerArchitectureTest {

    @ArchTest
    static final ArchRule controllerConstructorInjectsOnlyExportService =
            constructors()
                    .that()
                    .areDeclaredIn(RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
                    .should()
                    .haveRawParameterTypes(RuntimeTraceRegressionSuiteDefinitionExecutionExportService.class.getName());
}
