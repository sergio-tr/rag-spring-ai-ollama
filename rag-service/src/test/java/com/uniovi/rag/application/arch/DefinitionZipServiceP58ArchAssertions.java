package com.uniovi.rag.application.arch;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P58 (FD-def-zip-svc-no-run-deps, FD-def-zip-svc-no-run-calls): shared checks that definition-document
 * ZIP services stay isolated from run-layer ZIP/persistence services.
 */
public final class DefinitionZipServiceP58ArchAssertions {

    private DefinitionZipServiceP58ArchAssertions() {}

    private static boolean isRunLayerServiceOwner(JavaClass owner) {
        return owner.isAssignableTo(RuntimeTraceRegressionSuiteRunExportService.class)
                || owner.isAssignableTo(RuntimeTraceRegressionSuiteRunImportService.class)
                || owner.isAssignableTo(RuntimeTraceRegressionSuiteRunImportPreviewService.class)
                || owner.isAssignableTo(RuntimeTraceRegressionSuiteRunPersistenceService.class);
    }

    /**
     * Rejects run-layer service types as raw constructor parameter types and as types of non-static fields
     * declared on {@code serviceClass} (FD-def-zip-svc-no-run-deps).
     */
    public static void assertNoRunLayerDependenciesInConstructorsOrInstanceFields(JavaClass serviceClass) {
        for (JavaConstructor ctor : serviceClass.getConstructors()) {
            for (JavaClass param : ctor.getRawParameterTypes()) {
                assertThat(isRunLayerServiceOwner(param))
                        .as(
                                "Constructor %s must not take run-layer service parameters, but had %s",
                                ctor.getDescription(), param.getName())
                        .isFalse();
            }
        }
        for (JavaField field : serviceClass.getAllFields()) {
            if (!field.getOwner().equals(serviceClass)) {
                continue;
            }
            if (field.getModifiers().contains(JavaModifier.STATIC)) {
                continue;
            }
            JavaClass raw = field.getRawType();
            assertThat(isRunLayerServiceOwner(raw))
                    .as(
                            "Field %s must not use a run-layer service type, but had %s",
                            field.getFullName(), raw.getName())
                    .isFalse();
        }
    }

    /**
     * Rejects field accesses, method calls, and constructor calls from any constructor or method declared on
     * {@code serviceClass} whose target owner is assignable to a run-layer service (FD-def-zip-svc-no-run-calls).
     */
    public static void assertNoRunLayerAccessesInDeclaredCodeUnits(JavaClass serviceClass) {
        for (JavaConstructor ctor : serviceClass.getConstructors()) {
            assertCodeUnitHasNoRunLayerAccesses(serviceClass, ctor);
        }
        for (JavaMethod method : serviceClass.getMethods()) {
            if (!method.getOwner().equals(serviceClass)) {
                continue;
            }
            assertCodeUnitHasNoRunLayerAccesses(serviceClass, method);
        }
    }

    private static void assertCodeUnitHasNoRunLayerAccesses(JavaClass serviceClass, JavaCodeUnit unit) {
        for (JavaAccess<?> access : unit.getAccessesFromSelf()) {
            JavaClass targetOwner = access.getTargetOwner();
            assertThat(isRunLayerServiceOwner(targetOwner))
                    .as(
                            "%s (%s) must not access run-layer services; violation: %s targets owner %s",
                            serviceClass.getSimpleName(),
                            unit.getDescription(),
                            access.getDescription(),
                            targetOwner.getName())
                    .isFalse();
        }
    }
}
