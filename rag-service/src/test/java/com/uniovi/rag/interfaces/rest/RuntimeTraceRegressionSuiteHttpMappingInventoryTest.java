package com.uniovi.rag.interfaces.rest;

import com.uniovi.Application;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P59: {@link RequestMappingHandlerMapping} inventory, collision keys, and prefix ownership for the
 * runtime trace regression suite HTTP surface (FD-p59-controller-set).
 */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
                "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
                "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Set SPRING_DATASOURCE_URL (e.g. CI) or start Docker for Testcontainers")
class RuntimeTraceRegressionSuiteHttpMappingInventoryTest {

    /*
     * FD-p59-arch-inventory — @Test methods:
     *   p59_collision_keys_unique
     *   p59_controller_beans
     *   p59_handler_count_matches_tracked_inventory
     *   p59_prefix_ownership
     */

    private static final String TRACKED_INVENTORY = "p59-runtime-trace-regression-suite-http-inventory.md";
    private static final String EM_DASH = "\u2014";

    private static final Set<Class<?>> FD_P59_CONTROLLER_SET =
            Set.of(
                    RuntimeTraceRegressionSuiteRunController.class,
                    RuntimeTraceRegressionSuiteRunExportController.class,
                    RuntimeTraceRegressionSuiteRunImportController.class,
                    RuntimeTraceRegressionSuiteRunImportPreviewController.class,
                    RuntimeTraceRegressionSuiteDefinitionController.class,
                    RuntimeTraceRegressionSuiteDefinitionExportController.class,
                    RuntimeTraceRegressionSuiteDefinitionImportController.class,
                    RuntimeTraceRegressionSuiteDefinitionImportPreviewController.class,
                    RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class);

    private static final Pattern PREFIX_RUNS =
            Pattern.compile("^/runtime-trace-regression-suite-runs(/|$)");
    private static final Pattern PREFIX_DEFINITIONS =
            Pattern.compile("^/runtime-trace-regression-suite-definitions(/|$)");
    private static final Pattern PREFIX_CONV_RUNS =
            Pattern.compile("^/conversations/[^/]+/runtime-trace-regression-suite-runs(/|$)");
    private static final Pattern PREFIX_CONV_DEFINITIONS =
            Pattern.compile("^/conversations/[^/]+/runtime-trace-regression-suite-definitions(/|$)");

    @Autowired private ApplicationContext applicationContext;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired private Environment environment;

    /**
     * FD-p59-collision-key — collision key from live {@link RequestMappingInfo} (no source reconstruction).
     */
    public static String toCollisionKey(RequestMappingInfo info, RequestMethod requestMethod) {
        String p = patternListP(info);
        String c = mediaTypeSegment(info.getConsumesCondition() != null
                ? info.getConsumesCondition().getConsumableMediaTypes()
                : Set.of());
        String r = mediaTypeSegment(info.getProducesCondition() != null
                ? info.getProducesCondition().getProducibleMediaTypes()
                : Set.of());
        return requestMethod.name() + "|" + p + "|" + c + "|" + r;
    }

    private static String mediaTypeSegment(Set<MediaType> types) {
        return types.stream()
                .map(MediaType::toString)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /** FD-p59-pattern-list */
    static String patternListP(RequestMappingInfo info) {
        PathPatternsRequestCondition pathPatterns = info.getPathPatternsCondition();
        if (pathPatterns != null && !pathPatterns.getPatterns().isEmpty()) {
            return pathPatterns.getPatterns().stream()
                    .map(PathPattern::getPatternString)
                    .sorted()
                    .collect(Collectors.joining(","));
        }
        return info.getPatternsCondition().getPatterns().stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    static String stripBase(String fullPattern, String productBasePath) {
        if (productBasePath != null
                && !productBasePath.isEmpty()
                && fullPattern.startsWith(productBasePath)) {
            return fullPattern.substring(productBasePath.length());
        }
        return fullPattern;
    }

    private static boolean matchesPrefixFamilies(String stripped) {
        return PREFIX_RUNS.matcher(stripped).find()
                || PREFIX_DEFINITIONS.matcher(stripped).find()
                || PREFIX_CONV_RUNS.matcher(stripped).find()
                || PREFIX_CONV_DEFINITIONS.matcher(stripped).find();
    }

    private String productBasePath() {
        return Objects.requireNonNullElse(
                environment.getProperty("rag.api.product-base-path"), "");
    }

    private List<Map.Entry<RequestMappingInfo, HandlerMethod>> p59HandlerEntries() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e :
                requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            Class<?> beanType = ClassUtils.getUserClass(e.getValue().getBeanType());
            if (FD_P59_CONTROLLER_SET.contains(beanType)) {
                out.add(e);
            }
        }
        return out;
    }

    private static Class<?> controllerClass(HandlerMethod hm) {
        return ClassUtils.getUserClass(hm.getBeanType());
    }

    @Test
    void p59_controller_beans() {
        for (Class<?> c : FD_P59_CONTROLLER_SET) {
            assertThat(applicationContext.getBeansOfType(c))
                    .as("single bean for %s", c.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void p59_collision_keys_unique() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> entries = p59HandlerEntries();
        Set<String> keys = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : entries) {
            RequestMappingInfo info = e.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            assertThat(methods).as("FD-p59-single-http-method for %s", e.getValue()).hasSize(1);
            RequestMethod m = methods.iterator().next();
            assertThat(patternListP(info)).as("FD-p59-single-path-pattern for %s", e.getValue()).doesNotContain(",");
            String key = toCollisionKey(info, m);
            assertThat(keys).as("T-P59-collision duplicate key: %s (%s)", key, e.getValue()).doesNotContain(key);
            keys.add(key);
        }
        assertThat(keys).hasSize(28);
    }

    @Test
    void p59_prefix_ownership() {
        String base = productBasePath();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e :
                requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();
            String p = patternListP(info);
            for (String pattern : splitPatterns(p)) {
                String stripped = stripBase(pattern, base);
                if (matchesPrefixFamilies(stripped)) {
                    assertThat(FD_P59_CONTROLLER_SET)
                            .as(
                                    "T-P59-prefix-ownership: handler for pattern %s (stripped %s) must be FD-p59",
                                    pattern, stripped)
                            .contains(controllerClass(hm));
                }
            }
        }
    }

    private static List<String> splitPatterns(String p) {
        if (p.contains(",")) {
            return List.of(p.split(","));
        }
        return List.of(p);
    }

    @Test
    void p59_handler_count_matches_tracked_inventory() throws Exception {
        List<ExpectedInventoryRow> expected = parseTrackedInventory();
        assertThat(expected).hasSize(28);

        String base = productBasePath();
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> actualEntries = p59HandlerEntries();
        assertThat(actualEntries).hasSize(28);

        List<Map.Entry<RequestMappingInfo, HandlerMethod>> unmatched =
                new ArrayList<>(actualEntries);
        for (ExpectedInventoryRow row : expected) {
            Map.Entry<RequestMappingInfo, HandlerMethod> match = null;
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : unmatched) {
                if (inventoryRowMatches(row, e.getKey(), e.getValue(), base)) {
                    match = e;
                    break;
                }
            }
            assertThat(match)
                    .as("T-P59-handler-count row match for HTTP=%s path=%s", row.http, row.pathCell)
                    .isNotNull();
            unmatched.remove(match);
        }
        assertThat(unmatched).as("FD-p59-inventory-match: unmatched handler methods").isEmpty();
    }

    private boolean inventoryRowMatches(
            ExpectedInventoryRow row,
            RequestMappingInfo info,
            HandlerMethod hm,
            String basePath) {
        if (!controllerClass(hm).getSimpleName().equals(row.controllerSimpleName)) {
            return false;
        }
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.size() != 1) {
            return false;
        }
        if (!methods.equals(EnumSet.of(RequestMethod.valueOf(row.http)))) {
            return false;
        }
        String p = patternListP(info);
        if (p.contains(",")) {
            return false;
        }
        if (!stripBase(p, basePath).equals(row.pathCell)) {
            return false;
        }
        String c =
                mediaTypeSegment(
                        info.getConsumesCondition() != null
                                ? info.getConsumesCondition().getConsumableMediaTypes()
                                : Set.of());
        String r =
                mediaTypeSegment(
                        info.getProducesCondition() != null
                                ? info.getProducesCondition().getProducibleMediaTypes()
                                : Set.of());
        return c.equals(row.expectC) && r.equals(row.expectR);
    }

    private static final class ExpectedInventoryRow {
        final String http;
        final String pathCell;
        final String controllerSimpleName;
        final String expectC;
        final String expectR;

        ExpectedInventoryRow(String http, String pathCell, String controllerSimpleName, String expectC, String expectR) {
            this.http = http;
            this.pathCell = pathCell;
            this.controllerSimpleName = controllerSimpleName;
            this.expectC = expectC;
            this.expectR = expectR;
        }
    }

    private List<ExpectedInventoryRow> parseTrackedInventory() throws Exception {
        List<ExpectedInventoryRow> rows = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        getClass().getClassLoader().getResourceAsStream(TRACKED_INVENTORY)),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()
                        || line.startsWith("|---")
                        || line.startsWith("| # | HTTP |")) {
                    continue;
                }
                if (!line.startsWith("|")) {
                    continue;
                }
                String[] cells = line.split("\\|", -1);
                if (cells.length < 7) {
                    continue;
                }
                // cells[0] empty, cells[1] #, cells[2] HTTP, ...
                String num = cells[1].strip();
                if (num.isEmpty() || !Character.isDigit(num.charAt(0))) {
                    continue;
                }
                String http = cells[2].strip();
                String pathRaw = cells[3].strip().replace('`', ' ').strip();
                String consumesCell = cells[4].strip();
                String producesCell = cells[5].strip();
                String controller = cells[6].strip();
                rows.add(
                        new ExpectedInventoryRow(
                                http,
                                pathRaw,
                                controller,
                                consumesCell.equals(EM_DASH) ? "" : consumesCell,
                                producesCell.equals(EM_DASH) ? "" : producesCell));
            }
        }
        return rows;
    }
}
