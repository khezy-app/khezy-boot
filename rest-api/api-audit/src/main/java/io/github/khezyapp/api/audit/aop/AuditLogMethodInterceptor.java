package io.github.khezyapp.api.audit.aop;

import io.github.khezyapp.api.audit.AuditExpressionEvaluator;
import io.github.khezyapp.api.audit.ClientIpUtil;
import io.github.khezyapp.api.audit.annotation.AuditLog;
import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.api.AuditUserProvider;
import io.github.khezyapp.api.audit.extractor.CompositeBodyExtractor;
import io.github.khezyapp.api.audit.model.AuditLogRecord;
import io.github.khezyapp.api.audit.model.AuditMetadata;
import io.github.khezyapp.api.audit.model.AuditStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Aop Alliance {@link MethodInterceptor} that handles the orchestration of audit logging
 * for methods annotated with {@link AuditLog}.
 * <p>
 * This interceptor captures method execution metadata, evaluates SpEL expressions for
 * entity identifiers, extracts request details from the web context, and dispatches
 * the resulting {@link AuditLogRecord} to the {@link AuditLogService}.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class AuditLogMethodInterceptor implements MethodInterceptor {
    private final AuditLogAttributeSource attributeSource;
    private final AuditExpressionEvaluator auditExpressionEvaluator;
    private final CompositeBodyExtractor compositeBodyExtractor;
    private final AuditLogService auditLogService;
    private final ObjectProvider<AuditUserProvider> auditUserProvider;

    /**
     * Intercepts the method invocation to determine if audit logging is required.
     * <p>
     * If an {@link AuditLog} attribute is found and not explicitly ignored, the
     * execution is routed through the {@link #audit(MethodInvocation, AuditLog)} process.
     * </p>
     *
     * @param invocation the method invocation join point
     * @return the result of the method invocation
     * @throws Throwable if the underlying method throws an exception
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        final var auditLog = attributeSource.findAttribute(invocation.getMethod(),
                AopProxyUtils.ultimateTargetClass(invocation.getThis()));
        if (Objects.isNull(auditLog) || auditLog.ignore()) {
            return invocation.proceed();
        }
        return audit(invocation, auditLog);
    }

    /**
     * Executes the target method and captures the outcome (Success or Failure)
     * for auditing purposes.
     * <p>
     * This method uses a try-catch-finally block to ensure that an audit record is
     * created and sent to the service regardless of whether the method execution
     * succeeded or threw an exception.
     * </p>
     *
     * @param invocation the method invocation
     * @param auditLog   the resolved audit configuration
     * @return the result of the invocation
     * @throws Throwable rethrows any exception encountered during execution
     */
    private Object audit(final MethodInvocation invocation,
                         final AuditLog auditLog) throws Throwable {
        final var stopWatch = new StopWatch();
        stopWatch.start();
        Object result = null;
        AuditStatus status = AuditStatus.SUCCESS;
        Throwable error = null;

        try {
            result = invocation.proceed();
            return result;
        } catch (final Throwable e) {
            status = AuditStatus.FAILURE;
            error = e;
            throw e;
        } finally {
            stopWatch.stop();
            final var entry = buildEntry(invocation, result, auditLog, status, error, stopWatch);
            auditLogService.onRequest(entry);
        }
    }

    /**
     * Constructs a comprehensive {@link AuditLogRecord} using the invocation context
     * and the execution results.
     * <p>
     * It extracts tracing identifiers (traceId, spanId) from the MDC and uses
     * {@link AuditExpressionEvaluator} to resolve dynamic entity IDs.
     * </p>
     *
     * @param invocation the method invocation
     * @param result     the return value of the method (if any)
     * @param auditLog   the annotation configuration
     * @param status     the final status of the execution
     * @param error      the exception thrown (if any)
     * @return a populated audit log record
     */
    private AuditLogRecord buildEntry(final MethodInvocation invocation,
                                      final Object result,
                                      final AuditLog auditLog,
                                      final AuditStatus status,
                                      final Throwable error,
                                      final StopWatch stopWatch) {
        final var entityId = auditExpressionEvaluator.execute(auditLog.entityId(), invocation, result);
        final var entityClass = auditLog.entityClass() != void.class ? auditLog.entityClass() : null;
        final var currentUser = Optional.ofNullable(auditUserProvider)
                .map(ObjectProvider::getIfUnique)
                .map(AuditUserProvider::getCurrentUser)
                .orElse(null);
        return AuditLogRecord.builder()
                .user(currentUser)
                .traceId(MDC.get("traceId"))
                .spanId(MDC.get("spanId"))
                .action(auditLog.action())
                .entityId(entityId)
                .entity(Optional.ofNullable(entityClass).map(Class::getSimpleName).orElse(null))
                .status(status)
                .error(error)
                .timestamp(Instant.now())
                .duration(stopWatch.getTotalTimeMillis())
                .metadata(buildMetadata(invocation))
                .build();
    }

    /**
     * Extracts HTTP-specific metadata from the current request context.
     * <p>
     * If the execution is within a web request (Servlet context), it captures
     * URI, HTTP method, client IP, User-Agent, and extracts the request body/parameters
     * via the {@link CompositeBodyExtractor}.
     * </p>
     *
     * @param invocation the method invocation
     * @return a metadata object containing request details
     */
    private AuditMetadata buildMetadata(final MethodInvocation invocation) {
        final var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return AuditMetadata.builder().build();
        }

        final var request = servletAttributes.getRequest();
        final var requestData = compositeBodyExtractor.extract(invocation, request);
        final var builder = AuditMetadata.builder()
                .requestUri(request.getRequestURI())
                .queryString(request.getQueryString())
                .httpMethod(request.getMethod())
                .ip(ClientIpUtil.getClientIp(request))
                .userAgent(request.getHeader("User-Agent"));
        if (!CollectionUtils.isEmpty(requestData)) {
            builder.property("requestData", new LinkedHashMap<>(requestData));
        }
        return builder.build();
    }
}
