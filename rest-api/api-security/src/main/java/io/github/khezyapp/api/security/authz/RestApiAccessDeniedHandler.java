package io.github.khezyapp.api.security.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import io.github.khezyapp.api.security.authority.RequiredFactorError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.ThrowableAnalyzer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static io.github.khezyapp.api.security.util.FactorAuthorities.FACTOR_PREFIX;
import static io.github.khezyapp.api.security.util.FactorAuthorities.getFactorMethod;

/**
 * An {@link AccessDeniedHandler} that produces structured
 * {@link ProblemDetail} (RFC 7807) JSON responses for REST API requests.
 * When the denial is caused by missing multi-factor authorities,
 * the response includes {@code requiredMFA: true} and the
 * specific {@code mfaMethod} needed.
 */
@RequiredArgsConstructor
public class RestApiAccessDeniedHandler implements AccessDeniedHandler {
    private static final String DEFAULT_MSG = "Insufficient permissions";
    private static final String MFA_REQUIRED_MSG = "Additional authentication required";
    private final ThrowableAnalyzer analyzer = new ThrowableAnalyzer();
    private final ObjectMapper objectMapper;

    /**
     * Writes a {@code 403} ProblemDetail response. If the exception chains to an
     * {@link AuthorizationDeniedException} with factor-related errors, the body
     * signals that additional MFA is required.
     */
    @Override
    public void handle(final HttpServletRequest request,
                       final HttpServletResponse response,
                       final AccessDeniedException accessDeniedException) throws IOException, ServletException {
        final var authorityErrors = authorityErrors(accessDeniedException);
        final var factorAuthorityError = authorityErrors.stream()
                .filter(a -> Objects.nonNull(a.requiredFactorError()))
                .toList();

        final var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                DEFAULT_MSG
        );
        problem.setTitle("Access Denied");

        if (!factorAuthorityError.isEmpty()) {
            problem.setProperty("requiredMFA", true);
            problem.setProperty("mfaMethod", getFactorMethod(factorAuthorityError.get(0).authority));
            problem.setDetail(MFA_REQUIRED_MSG);
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private List<AuthorityRequiredFactorErrorEntry> authorityErrors(
            final AccessDeniedException accessDeniedException
    ) {
        final var authorityDenied = findAuthorizationDeniedException(accessDeniedException);
        if (Objects.isNull(authorityDenied)) {
            return Collections.emptyList();
        }
        final var authorizationResult = authorityDenied.getAuthorizationResult();

        if (authorizationResult instanceof RequiredFactorAuthorityDecision rfd) {
            return rfd.getFactorErrors()
                    .stream()
                    .map(error -> new AuthorityRequiredFactorErrorEntry(error.getAuthority(), error))
                    .toList();
        }

        if (authorizationResult instanceof AuthorityAuthorizationDecision ad) {
            return ad.getAuthorities()
                    .stream()
                    .map(authority -> {
                        if (authority instanceof RequiredFactorAuthority) {
                            return new AuthorityRequiredFactorErrorEntry(
                                    authority.getAuthority(),
                                    RequiredFactorError.createMissing(authority.getAuthority())
                            );
                        } else if (authority.getAuthority().startsWith(FACTOR_PREFIX)) {
                            return new AuthorityRequiredFactorErrorEntry(
                                    authority.getAuthority(),
                                    RequiredFactorError.createMissing(authority.getAuthority())
                            );
                        } else {
                            return new AuthorityRequiredFactorErrorEntry(authority.getAuthority(), null);
                        }
                    })
                    .toList();
        }
        return Collections.emptyList();
    }

    private AuthorizationDeniedException findAuthorizationDeniedException(
            final AccessDeniedException accessDeniedException
    ) {
        if (accessDeniedException instanceof AuthorizationDeniedException) {
            return (AuthorizationDeniedException) accessDeniedException;
        }
        final var chains = analyzer.determineCauseChain(accessDeniedException);
        return (AuthorizationDeniedException) analyzer.getFirstThrowableOfType(
                AuthorizationDeniedException.class, chains
        );
    }

    private record AuthorityRequiredFactorErrorEntry(
            String authority,
            RequiredFactorError requiredFactorError
    ) {
    }
}
