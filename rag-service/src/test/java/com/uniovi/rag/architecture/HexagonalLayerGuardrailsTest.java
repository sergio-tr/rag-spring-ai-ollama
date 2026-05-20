package com.uniovi.rag.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hexagonal guardrails (Agent A6). Existing transitional package freezes live in
 * {@link LayeredArchitectureTest}; this class blocks new layer violations and documents debt via
 * {@link ArchitectureGuardrailAllowlists}.
 */
@AnalyzeClasses(packages = "com.uniovi.rag", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalLayerGuardrailsTest {

    private static final String REST_PACKAGE_PREFIX = "com.uniovi.rag.interfaces.rest";
    private static final String PERSISTENCE_PACKAGE_PREFIX = "com.uniovi.rag.infrastructure.persistence";

    private HexagonalLayerGuardrailsTest() {}

    private static DescribedPredicate<JavaClass> allowlistedRestDebt() {
        return new DescribedPredicate<>("explicit APPLICATION_REST_ADAPTER_DEBT entry") {
            @Override
            public boolean test(JavaClass input) {
                return ArchitectureGuardrailAllowlists.isApplicationRestAdapterDebt(input.getName());
            }
        };
    }

    private static DescribedPredicate<JavaClass> allowlistedPersistenceDebt() {
        return new DescribedPredicate<>("explicit APPLICATION_PERSISTENCE_ADAPTER_DEBT entry") {
            @Override
            public boolean test(JavaClass input) {
                return ArchitectureGuardrailAllowlists.isApplicationPersistenceAdapterDebt(input.getName());
            }
        };
    }

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworkOrAdapters = noClasses()
            .that()
            .resideInAnyPackage("com.uniovi.rag.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "com.uniovi.rag.interfaces..",
                    "com.uniovi.rag.infrastructure..")
            .because("Domain stays framework-free; use application ports and infrastructure adapters");

    @ArchTest
    static final ArchRule applicationMustNotDependOnRestExceptExplicitDebt = noClasses()
            .that()
            .resideInAnyPackage("com.uniovi.rag.application..")
            .and(JavaClass.Predicates.TOP_LEVEL_CLASSES)
            .and(not(allowlistedRestDebt()))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.uniovi.rag.interfaces.rest..")
            .because(
                    "Application layer must not import REST DTOs/exceptions; map in interfaces.rest (see ArchitectureGuardrailAllowlists)");

    @ArchTest
    static final ArchRule applicationMustNotDependOnPersistenceExceptExplicitDebt = noClasses()
            .that()
            .resideInAnyPackage("com.uniovi.rag.application..")
            .and(JavaClass.Predicates.TOP_LEVEL_CLASSES)
            .and(not(allowlistedPersistenceDebt()))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.uniovi.rag.infrastructure.persistence..")
            .because(
                    "Application layer must use ports, not JPA entities/repositories (see ArchitectureGuardrailAllowlists)");

    @ArchTest
    static void noProductionClassesUnderTransitionalServicePackage(JavaClasses importedClasses) {
        assertPackageEmpty(importedClasses, "com.uniovi.rag.service.", "service..");
    }

    @ArchTest
    static void noProductionClassesUnderGenericApplicationModelPackage(JavaClasses importedClasses) {
        assertPackageEmpty(importedClasses, "com.uniovi.rag.application.model.", "application.model");
    }

    @ArchTest
    static void noProductionClassesUnderServiceModelPackage(JavaClasses importedClasses) {
        assertPackageEmpty(importedClasses, "com.uniovi.rag.service.model.", "service.model");
    }

    @ArchTest
    static void noProductionClassesUnderTransitionalQueryPackages(JavaClasses importedClasses) {
        assertPackageEmpty(importedClasses, "com.uniovi.rag.application.service.query.", "application.service.query");
    }

    @ArchTest
    static void restAllowlistEntriesStillDependOnRest(JavaClasses importedClasses) {
        assertAllowlistStillViolates(
                importedClasses,
                ArchitectureGuardrailAllowlists.APPLICATION_REST_ADAPTER_DEBT,
                REST_PACKAGE_PREFIX,
                "REST");
    }

    @ArchTest
    static void persistenceAllowlistEntriesStillDependOnJpa(JavaClasses importedClasses) {
        assertAllowlistStillViolates(
                importedClasses,
                ArchitectureGuardrailAllowlists.APPLICATION_PERSISTENCE_ADAPTER_DEBT,
                PERSISTENCE_PACKAGE_PREFIX,
                "JPA");
    }

    private static void assertPackageEmpty(JavaClasses importedClasses, String prefix, String label) {
        List<String> found = importedClasses.stream()
                .filter(javaClass -> javaClass.getEnclosingClass().isEmpty())
                .map(JavaClass::getName)
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .toList();

        assertThat(found)
                .as("No production classes under %s (Agent A1–A3 migration complete)", label)
                .isEmpty();
    }

    private static void assertAllowlistStillViolates(
            JavaClasses importedClasses,
            Set<String> allowlist,
            String targetPackagePrefix,
            String violationLabel) {
        for (String className : allowlist) {
            JavaClass javaClass = importedClasses.get(className);
            assertThat(javaClass)
                    .as("Allowlist class must exist in production code: %s", className)
                    .isNotNull();
            boolean stillViolates = dependsOnPackage(javaClass, targetPackagePrefix);
            assertThat(stillViolates)
                    .as(
                            "Remove %s from allowlist when fixed, or restore %s dependency: %s",
                            className,
                            violationLabel,
                            className)
                    .isTrue();
        }
    }

    private static boolean dependsOnPackage(JavaClass javaClass, String packagePrefix) {
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
            if (dependency.getTargetClass().getPackageName().startsWith(packagePrefix)) {
                return true;
            }
        }
        return referencesPackageInSource(javaClass.getName(), packagePrefix);
    }

    private static boolean referencesPackageInSource(String className, String packagePrefix) {
        Path source =
                Path.of("src/main/java").resolve(className.replace('.', '/') + ".java");
        if (!Files.isRegularFile(source)) {
            return false;
        }
        try {
            return Files.readString(source).contains(packagePrefix);
        } catch (Exception e) {
            return false;
        }
    }
}
