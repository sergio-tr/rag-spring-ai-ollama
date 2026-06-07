package com.uniovi.rag.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Production REST controllers must not declare unprefixed {@code /api/auth/**} or {@code /api/admin/**} mirrors.
 * Canonical paths use {@code ${rag.api.product-base-path}} (e.g. {@code /api/v5/auth/**}).
 */
@AnalyzeClasses(packages = "com.uniovi.rag.interfaces.rest", importOptions = ImportOption.DoNotIncludeTests.class)
class UnprefixedAuthAdminMirrorForbiddenArchitectureTest {

    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            RequestMapping.class.getName(),
            GetMapping.class.getName(),
            PostMapping.class.getName(),
            PutMapping.class.getName(),
            PatchMapping.class.getName(),
            DeleteMapping.class.getName());

    @ArchTest
    static final ArchRule rest_controllers_must_not_declare_unprefixed_auth_admin_paths =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should(notDeclareUnprefixedAuthOrAdminMirrorPaths());

    private static ArchCondition<JavaClass> notDeclareUnprefixedAuthOrAdminMirrorPaths() {
        return new ArchCondition<>("not declare unprefixed /api/auth or /api/admin request paths") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (String path : collectMappingPaths(javaClass)) {
                    if (isUnprefixedAuthOrAdminMirror(path)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        javaClass,
                                        javaClass.getName() + " declares forbidden mirror path: " + path));
                    }
                }
                for (JavaMethod method : javaClass.getMethods()) {
                    for (String path : collectMappingPaths(method)) {
                        if (isUnprefixedAuthOrAdminMirror(path)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            method,
                                            method.getFullName() + " declares forbidden mirror path: " + path));
                        }
                    }
                }
            }
        };
    }

    private static List<String> collectMappingPaths(JavaClass javaClass) {
        List<String> paths = new ArrayList<>();
        for (JavaAnnotation<JavaClass> ann : javaClass.getAnnotations()) {
            paths.addAll(extractPaths(ann));
        }
        return paths;
    }

    private static List<String> collectMappingPaths(JavaMethod method) {
        List<String> paths = new ArrayList<>();
        for (JavaAnnotation<JavaMethod> ann : method.getAnnotations()) {
            if (!MAPPING_ANNOTATIONS.contains(ann.getRawType().getName())) {
                continue;
            }
            paths.addAll(extractPaths(ann));
        }
        return paths;
    }

    private static List<String> extractPaths(JavaAnnotation<?> ann) {
        List<String> out = new ArrayList<>();
        ann.get("value").ifPresent(v -> addPathValue(out, v));
        ann.get("path").ifPresent(v -> addPathValue(out, v));
        return out;
    }

    private static void addPathValue(List<String> out, Object raw) {
        if (raw instanceof String s) {
            out.add(s);
            return;
        }
        if (raw instanceof String[] arr) {
            for (String s : arr) {
                out.add(s);
            }
        }
    }

    private static boolean isUnprefixedAuthOrAdminMirror(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return p.equals("/api/auth")
                || p.startsWith("/api/auth/")
                || p.equals("/api/admin")
                || p.startsWith("/api/admin/");
    }
}
