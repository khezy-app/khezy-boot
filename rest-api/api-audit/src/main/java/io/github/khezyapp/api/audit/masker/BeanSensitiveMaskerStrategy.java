package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.annotation.SensitiveData;
import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.util.ProxyUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Implementation of {@link SensitiveMaskerStrategy} designed to process standard Java Beans.
 * <p>
 * This strategy traverses bean properties using {@link BeanUtils} and {@link ReflectionUtils}.
 * It applies masking or exclusion logic based on the {@link SensitiveData} annotation,
 * with field-level annotations taking priority over getter-level annotations.
 * </p>
 */
@Slf4j
public class BeanSensitiveMaskerStrategy implements SensitiveMaskerStrategy {

    /**
     * Default set of internal or proxy-related properties to be excluded from masking.
     */
    private static final Set<String> IGNORE_PROPERTIES = Set.of(
            "class",
            "hibernateLazyInitializer",
            "handler",
            "fieldHandler",
            "$_hibernate_interceptor"
    );

    private final Set<String> ignoreProperties;
    private Function<Object, Class<?>> getBeanClass = ProxyUtils::getUserClass;

    /**
     * Constructs a strategy instance with default ignored properties.
     */
    public BeanSensitiveMaskerStrategy() {
        this.ignoreProperties = new HashSet<>(IGNORE_PROPERTIES);
    }

    /**
     * Constructs a strategy instance with custom ignored properties and class resolution logic.
     *
     * @param ignoreProperties additional property names to exclude
     * @param getBeanClass      function to resolve the actual class of the bean (e.g., handling proxies)
     */
    public BeanSensitiveMaskerStrategy(final Set<String> ignoreProperties,
                                       final Function<Object, Class<?>> getBeanClass) {
        Objects.requireNonNull(ignoreProperties, "ignoreProperties can not be null");
        Objects.requireNonNull(getBeanClass, "getBeanClass can not be null");
        this.ignoreProperties = new HashSet<>(IGNORE_PROPERTIES);
        this.ignoreProperties.addAll(ignoreProperties);
        this.getBeanClass = getBeanClass;
    }

    /**
     * Evaluates if the payload is a single bean (not a collection, array, or map).
     *
     * @param payload the object to check
     * @return {@code true} if the payload is a bean; {@code false} otherwise
     */
    @Override
    public boolean supports(final Object payload) {
        return !isCollection(payload) &&
                !isArray(payload) &&
                !isMap(payload);
    }

    /**
     * Transforms the bean into a {@link java.util.Map} where sensitive fields are masked or omitted.
     * <p>
     * The method performs the following:
     * <ul>
     * <li>Registers the payload to prevent infinite recursion in circular graphs.</li>
     * <li>Identifies sensitive data via annotations on fields or getters.</li>
     * <li>Omits properties marked as {@code ignore = true}.</li>
     * <li>Masks primitive or complex types if annotated.</li>
     * <li>Recursively processes nested complex objects if no direct annotation is found.</li>
     * </ul>
     * </p>
     *
     * @param payload the bean instance to mask
     * @param context the current masking context for recursion and visitor tracking
     * @return a map representation of the masked bean
     */
    @Override
    public Object mask(final Object payload,
                       final SensitiveMaskerContext context) {
        final var proceedObject = new HashMap<String, Object>();
        context.registerVisited(payload, proceedObject);

        final var clz = getBeanClass.apply(payload);
        final var pds = BeanUtils.getPropertyDescriptors(clz);

        for (final var pd : pds) {
            if (ignoreProperties.contains(pd.getName())) {
                continue;
            }

            final var field = ReflectionUtils.findField(clz, pd.getName());
            if (Objects.nonNull(field) &&
                    (Modifier.isTransient(field.getModifiers()) ||
                            Modifier.isStatic(field.getModifiers()))) {
                continue;
            }

            final var getter = pd.getReadMethod();

            // Resolve Annotation: Field priority over Getter
            var sensitiveData = Objects.nonNull(field) ? field.getAnnotation(SensitiveData.class) : null;
            if (Objects.isNull(sensitiveData) && Objects.nonNull(getter)) {
                sensitiveData = getter.getAnnotation(SensitiveData.class);
            }

            // If ignore is true, skip this property entirely from the output map
            if (Objects.nonNull(sensitiveData) && sensitiveData.ignore()) {
                continue;
            }

            final Object valueToMask;
            try {
                if (Objects.nonNull(getter)) {
                    valueToMask = getter.invoke(payload);
                } else if (Objects.nonNull(field)) {
                    ReflectionUtils.makeAccessible(field);
                    valueToMask = field.get(payload);
                } else {
                    continue;
                }
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
                log.warn("Unable to access property '{}' for masking", pd.getName());
                continue;
            }

            if (Objects.isNull(valueToMask)) {
                proceedObject.put(pd.getName(), null);
                continue;
            }

            final var propertyType = Objects.nonNull(field) ? field.getType() : getter.getReturnType();

            if (isPrimitive(propertyType)) {
                // If annotated and not ignored (checked above), apply mask
                if (Objects.nonNull(sensitiveData)) {
                    proceedObject.put(pd.getName(), sensitiveData.mask());
                } else {
                    proceedObject.put(pd.getName(), valueToMask);
                }
            } else {
                // For complex objects, if annotated, use mask; otherwise, recurse
                if (Objects.nonNull(sensitiveData)) {
                    proceedObject.put(pd.getName(), sensitiveData.mask());
                } else {
                    final var mask = context.processMask(valueToMask);
                    proceedObject.put(pd.getName(), mask);
                }
            }
        }
        return proceedObject;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
