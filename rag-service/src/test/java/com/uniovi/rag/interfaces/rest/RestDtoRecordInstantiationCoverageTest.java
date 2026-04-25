package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Exercises canonical constructors for REST DTO records/classes under {@code interfaces.rest.dto} so JaCoCo counts
 * compact-ctor and field lines after the package is removed from JaCoCo excludes (wave 6.03).
 */
class RestDtoRecordInstantiationCoverageTest {

    private static final String BASE = "com.uniovi.rag.interfaces.rest.dto";

    @TestFactory
    Stream<DynamicTest> instantiateEachConcreteDtoType() {
        return scanDtoClasses()
                .sorted(Comparator.comparing(Class::getName))
                .map(clazz -> dynamicTest(clazz.getName(), () -> assertNotNull(instantiate(clazz))));
    }

    private static Stream<Class<?>> scanDtoClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(
                new TypeFilter() {
                    @Override
                    public boolean match(MetadataReader metadataReader, MetadataReaderFactory factory) {
                        String name = metadataReader.getClassMetadata().getClassName();
                        if (!name.startsWith(BASE)) {
                            return false;
                        }
                        if (name.endsWith("Test")) {
                            return false;
                        }
                        if (name.contains("package-info")) {
                            return false;
                        }
                        if (metadataReader.getClassMetadata().isInterface()) {
                            return false;
                        }
                        return metadataReader.getClassMetadata().isConcrete()
                                && metadataReader.getClassMetadata().isIndependent();
                    }
                });
        return scanner.findCandidateComponents(BASE).stream()
                .map(bd -> {
                    try {
                        return Class.forName(bd.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static Object instantiate(Class<?> clazz) {
        try {
            if (clazz.isRecord()) {
                RecordComponent[] components = clazz.getRecordComponents();
                Object[] args = new Object[components.length];
                IdentityHashMap<Type, Object> memo = new IdentityHashMap<>();
                for (int i = 0; i < components.length; i++) {
                    args[i] = sampleValue(components[i].getGenericType(), memo);
                }
                Constructor<?> ctor = clazz.getDeclaredConstructor(
                        Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            }
            Constructor<?> ctor = pickConstructor(clazz);
            ctor.setAccessible(true);
            Parameter[] params = ctor.getParameters();
            Object[] args = new Object[params.length];
            IdentityHashMap<Type, Object> memo = new IdentityHashMap<>();
            for (int i = 0; i < params.length; i++) {
                args[i] = sampleValue(params[i].getParameterizedType(), memo);
            }
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException ex) {
            fail("Failed to instantiate " + clazz.getName() + ": " + ex.getMessage(), ex);
            return null;
        }
    }

    private static Constructor<?> pickConstructor(Class<?> clazz) {
        List<Constructor<?>> ctors = new ArrayList<>(Arrays.asList(clazz.getConstructors()));
        ctors.sort(Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount).reversed());
        for (Constructor<?> c : ctors) {
            if (c.isAnnotationPresent(JsonCreator.class)) {
                return c;
            }
        }
        if (ctors.isEmpty()) {
            throw new IllegalStateException("No public constructor for " + clazz.getName());
        }
        return ctors.get(0);
    }

    private static Object sampleValue(Type type, IdentityHashMap<Type, Object> memo) {
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                return Array.newInstance(c.getComponentType(), 0);
            }
            return sampleClass(c, memo);
        }
        if (type instanceof ParameterizedType pt) {
            return sampleParameterized(pt, memo);
        }
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0) {
                return sampleValue(upper[0], memo);
            }
            return sampleClass(Object.class, memo);
        }
        if (type instanceof GenericArrayType gat) {
            Type component = gat.getGenericComponentType();
            Class<?> raw = rawClass(component);
            Object array = Array.newInstance(raw.isPrimitive() ? raw : Object.class, 0);
            return array;
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        if (type instanceof WildcardType wt && wt.getUpperBounds().length > 0) {
            return rawClass(wt.getUpperBounds()[0]);
        }
        if (type instanceof GenericArrayType gat) {
            Class<?> inner = rawClass(gat.getGenericComponentType());
            return Array.newInstance(inner, 0).getClass();
        }
        return Object.class;
    }

    private static Object sampleParameterized(ParameterizedType pt, IdentityHashMap<Type, Object> memo) {
        Class<?> raw = (Class<?>) pt.getRawType();
        if (raw == Optional.class) {
            Type inner = pt.getActualTypeArguments()[0];
            Class<?> innerClass = rawClass(inner);
            if (innerClass == String.class) {
                return Optional.of("x");
            }
            if (innerClass.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object v = innerClass.getEnumConstants()[0];
                return Optional.of(v);
            }
            if (innerClass.getName().startsWith("com.uniovi.rag")) {
                return Optional.of(sampleValue(inner, memo));
            }
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)) {
            Type element = pt.getActualTypeArguments()[0];
            return List.of(sampleValue(element, memo));
        }
        if (Map.class.isAssignableFrom(raw)) {
            Type keyT = pt.getActualTypeArguments()[0];
            Type valT = pt.getActualTypeArguments()[1];
            Object k = sampleValue(keyT, memo);
            Object v = sampleValue(valT, memo);
            return Map.of(k, v);
        }
        return sampleClass(raw, memo);
    }

    private static Object sampleClass(Class<?> c, IdentityHashMap<Type, Object> memo) {
        if (c == void.class || c == Void.class) {
            throw new IllegalArgumentException("void");
        }
        if (c.isPrimitive()) {
            if (c == boolean.class) {
                return false;
            }
            if (c == char.class) {
                return 'a';
            }
            if (c == long.class) {
                return 0L;
            }
            if (c == double.class) {
                return 0.0d;
            }
            if (c == float.class) {
                return 0.0f;
            }
            if (c == short.class) {
                return (short) 0;
            }
            if (c == byte.class) {
                return (byte) 0;
            }
            return 0;
        }
        if (c == JsonNode.class) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (c == RuntimeTraceRegressionSuiteEntryRequestDto.class) {
            return new RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto(
                    "BY_TRACE_IDS", List.of(UUID.fromString("00000000-0000-0000-0000-000000000002")));
        }
        if (c == RuntimeTraceRegressionSuiteDefinitionEntryDto.class) {
            return instantiate(RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto.class);
        }
        if (c == RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto.class) {
            return instantiate(RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto.class);
        }
        if (c == String.class) {
            return "s";
        }
        if (c == UUID.class) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        if (c == Instant.class) {
            return Instant.EPOCH;
        }
        if (c.isEnum()) {
            return c.getEnumConstants()[0];
        }
        if (List.class.isAssignableFrom(c)) {
            return List.of();
        }
        if (Map.class.isAssignableFrom(c)) {
            return Map.of();
        }
        if (c.getName().startsWith("com.uniovi.rag")) {
            if (memo.containsKey(c)) {
                return memo.get(c);
            }
            memo.put(c, null);
            Object built = instantiate(c);
            memo.put(c, built);
            return built;
        }
        if (c == Object.class) {
            return "x";
        }
        if (c == Integer.class) {
            return 0;
        }
        if (c == Long.class) {
            return 0L;
        }
        if (c == Boolean.class) {
            return Boolean.FALSE;
        }
        if (c == Double.class) {
            return 0.0d;
        }
        throw new IllegalArgumentException("No sample for " + c.getName());
    }
}
