package io.github.khezyapp.api.security.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RestApiAccessDeniedHandlerTest {

    private RestApiAccessDeniedHandler handler;
    private ObjectMapper objectMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new RestApiAccessDeniedHandler(objectMapper);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        stringWriter = new StringWriter();
    }

    @Test
    void shouldReturn403WithMFADetails() throws Exception {
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        final var decision = new RequiredFactorAuthorityDecision(false, List.of("FACTOR_PASSWORD"));
        final var authException = new AuthorizationDeniedException("MFA required", decision);
        final var accessDenied = new AccessDeniedException("Access denied", authException);

        handler.handle(request, response, accessDenied);

        verify(response).setStatus(403);
        verify(response).setContentType("application/problem+json");

        final var problem = objectMapper.readValue(
                stringWriter.toString(), ProblemDetail.class
        );
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).isEqualTo("Additional authentication required");
        assertThat(problem.getProperties()).containsEntry("requiredMFA", true);
        assertThat(problem.getProperties()).containsEntry("mfaMethod", "password");
    }

    @Test
    void shouldReturnGeneric403ForNonMFAError() throws Exception {
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

        final var accessDenied = new AccessDeniedException("Access denied");

        handler.handle(request, response, accessDenied);

        verify(response).setStatus(403);

        final var problem = objectMapper.readValue(
                stringWriter.toString(), ProblemDetail.class
        );
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).isEqualTo("Insufficient permissions");
        assertThat(problem.getProperties()).satisfies(props -> {
            assertThat(props).isNullOrEmpty();
        });
    }
}
