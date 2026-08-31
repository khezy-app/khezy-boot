package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.starter.security.SecurityCallerIdentityResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentCardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AgentCardController.class, SecurityCallerIdentityResolver.class})
class AgentCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private A2ARequestDispatcher dispatcher;

    @Test
    @DisplayName("GET /.well-known/agent-card.json serves the dispatcher's agent card as JSON")
    void shouldServeAgentCard() throws Exception {
        when(dispatcher.agentCard(any())).thenReturn(WebFixtures.agentCard());

        mockMvc.perform(get("/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stub Agent"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.description").value("stub agent card for web tests"));
    }
}
