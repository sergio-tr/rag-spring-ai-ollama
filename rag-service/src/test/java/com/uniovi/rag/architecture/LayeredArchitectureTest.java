package com.uniovi.rag.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gradual architecture rules toward hexagonal layering. Extend as packages stabilize.
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

	@ArchTest
	static final ArchRule topLevelDomainPackageIsFrameworkFree = classes()
			.that().resideInAPackage("com.uniovi.rag.domain")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.uniovi.rag.domain..", "org.jetbrains.annotations..")
			.because("Enums and types directly under domain must stay free of Spring/Jakarta (subpackages may differ until migration completes)");

	@ArchTest
	static void noNewProductionClassesUnderTransitionalServicePackage(JavaClasses importedClasses) {
		List<String> serviceClasses = importedClasses.stream()
				.filter(javaClass -> javaClass.getEnclosingClass().isEmpty())
				.map(JavaClass::getName)
				.filter(name -> name.startsWith("com.uniovi.rag.service."))
				.sorted()
				.toList();

		assertThat(serviceClasses)
				.as("service.. is a frozen transitional package; move new production code to application/domain/infrastructure")
				.hasSizeLessThanOrEqualTo(147);
	}

	@ArchTest
	static void noNewProductionClassesUnderGenericApplicationModelPackage(JavaClasses importedClasses) {
		List<String> applicationModelClasses = importedClasses.stream()
				.filter(javaClass -> javaClass.getEnclosingClass().isEmpty())
				.map(JavaClass::getName)
				.filter(name -> name.startsWith("com.uniovi.rag.application.model."))
				.sorted()
				.toList();

		assertThat(applicationModelClasses)
				.as("application.model is frozen; use application.result, application.command, or application.query")
				.hasSizeLessThanOrEqualTo(6);
	}
}
