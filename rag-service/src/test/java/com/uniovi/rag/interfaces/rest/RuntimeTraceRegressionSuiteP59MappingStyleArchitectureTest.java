package com.uniovi.rag.interfaces.rest;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * P59 T-P59-mapping-style: handler methods on FD-p59-controller-set use {@code @GetMapping} / {@code @PostMapping} /
 * {@code @PutMapping} / {@code @DeleteMapping} only — no method-level {@code @RequestMapping}.
 */
@AnalyzeClasses(
        packagesOf = RuntimeTraceRegressionSuiteRunController.class,
        importOptions = ImportOption.DoNotIncludeTests.class)
class RuntimeTraceRegressionSuiteP59MappingStyleArchitectureTest {

    private static final Set<String> FD_P59_CONTROLLER_CLASS_NAMES =
            Stream.of(
                            RuntimeTraceRegressionSuiteRunController.class,
                            RuntimeTraceRegressionSuiteRunExportController.class,
                            RuntimeTraceRegressionSuiteRunImportController.class,
                            RuntimeTraceRegressionSuiteRunImportPreviewController.class,
                            RuntimeTraceRegressionSuiteDefinitionController.class,
                            RuntimeTraceRegressionSuiteDefinitionExportController.class,
                            RuntimeTraceRegressionSuiteDefinitionImportController.class,
                            RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class,
                            RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
                    .map(Class::getName)
                    .collect(Collectors.toUnmodifiableSet());

    private static final DescribedPredicate<JavaClass> FD_P59_CONTROLLER_SET =
            DescribedPredicate.describe(
                    "FD-p59-controller-set",
                    clazz -> FD_P59_CONTROLLER_CLASS_NAMES.contains(clazz.getFullName()));

    @ArchTest
    static final ArchRule p59_handler_methods_do_not_use_method_level_request_mapping =
            methods()
                    .that()
                    .areDeclaredInClassesThat(FD_P59_CONTROLLER_SET)
                    .should()
                    .notBeAnnotatedWith(RequestMapping.class);
}
