package com.uniovi.rag.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * REST adapter and transitional-package freeze rules. Domain/application hexagonal guardrails:
 * {@link HexagonalLayerGuardrailsTest} and {@link ArchitectureGuardrailAllowlists} (Agent A6).
 */
@AnalyzeClasses(packages = "com.uniovi.rag", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

	private LayeredArchitectureTest() {}

	@ArchTest
	static final ArchRule restControllersLiveInHttpAdapterPackages = classes()
			.that().areAnnotatedWith(RestController.class)
			.should().resideInAnyPackage("..interfaces.rest..")
			.because("REST adapters belong under interfaces.rest (product, auth, admin, support)");

	@ArchTest
	static final ArchRule productRestControllersDoNotUseRepositories =
			noClasses()
					.that().resideInAnyPackage("com.uniovi.rag.interfaces.rest")
					.and().areAnnotatedWith(RestController.class)
					.and().resideOutsideOfPackages(
							"com.uniovi.rag.interfaces.rest.support..")
					.should()
					.dependOnClassesThat()
					.haveNameMatching(".*Repository")
					.because(
							"Product REST adapters use application services for persistence; support excluded until migrated");

	@ArchTest
	static final ArchRule productRestControllersDoNotDependOnPersistenceLayer =
			noClasses()
					.that().resideInAnyPackage("com.uniovi.rag.interfaces.rest")
					.and().areAnnotatedWith(RestController.class)
					.and().resideOutsideOfPackages(
							"com.uniovi.rag.interfaces.rest.support..")
					.should()
					.dependOnClassesThat()
					.resideInAnyPackage("com.uniovi.rag.infrastructure.persistence..")
					.because(
							"REST adapters must not depend on JPA entities or persistence packages; use application services (support excluded until migrated)");

}
