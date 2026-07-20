package io.github.khezyapp.api.security.authn;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * An {@link AuthenticationEntryPoint} that produces structured
 * {@link ProblemDetail} (RFC 7807) JSON responses for unauthorized
 * REST API requests.
 */
@RequiredArgsConstructor
public class RestApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(final HttpServletRequest request,
                         final HttpServletResponse response,
                         final AuthenticationException authException) throws IOException, ServletException {
        final var problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication required"
        );
        problem.setTitle("Unauthorized");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
