package com.uniovi.rag.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Gradual architecture rules toward hexagonal layering. Extend as packages stabilize.
 */
@AnalyzeClasses(packages = "com.uniovi.rag", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

	@ArchTest
	static final ArchRule restControllersLiveInHttpAdapterPackages = classes()
			.that().areAnnotatedWith(RestController.class)
			.should().resideInAnyPackage("..interfaces.rest..")
			.because("REST adapters belong under interfaces.rest (product, auth, admin, legacy, support)");

	@ArchTest
	static final ArchRule productRestControllersDoNotUseRepositories =
			noClasses()
					.that().resideInAnyPackage("com.uniovi.rag.interfaces.rest")
					.and().areAnnotatedWith(RestController.class)
					.and().resideOutsideOfPackages(
							"com.uniovi.rag.interfaces.rest.legacy..",
							"com.uniovi.rag.interfaces.rest.support..")
					.should()
					.dependOnClassesThat()
					.haveNameMatching(".*Repository")
					.because(
							"Product REST adapters use application services for persistence; legacy/support excluded until migrated");

	@ArchTest
	static final ArchRule productRestControllersDoNotDependOnPersistenceLayer =
			noClasses()
					.that().resideInAnyPackage("com.uniovi.rag.interfaces.rest")
					.and().areAnnotatedWith(RestController.class)
					.and().resideOutsideOfPackages(
							"com.uniovi.rag.interfaces.rest.legacy..",
							"com.uniovi.rag.interfaces.rest.support..")
					.should()
					.dependOnClassesThat()
					.resideInAnyPackage("com.uniovi.rag.infrastructure.persistence..")
					.because(
							"REST adapters must not depend on JPA entities or persistence packages; use application services (legacy excluded per Phase B0)");

	@ArchTest
	static final ArchRule topLevelDomainPackageIsFrameworkFree = classes()
			.that().resideInAPackage("com.uniovi.rag.domain")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.uniovi.rag.domain..", "org.jetbrains.annotations..")
			.because("Enums and types directly under domain must stay free of Spring/Jakarta (subpackages may differ until migration completes)");
}
