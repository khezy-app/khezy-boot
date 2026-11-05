package io.github.khezyapp.a2a.starter.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import io.github.khezyapp.a2a.starter.A2AServerProperties;
import io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration;
import io.github.khezyapp.a2a.starter.server.MapAgentCardRouter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({AgentCardController.class, AgentCardControllerTest.TestConfig.class})
class AgentCardControllerTest {

    private static final String CARD_PATH = "/a2a/.well-known/agent-card.json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Serves the default card as proto-JSON with caching headers")
    void shouldServeDefaultCard() throws Exception {
        mockMvc.perform(get(CARD_PATH))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "max-age=60"))
                .andExpect(jsonPath("$.name").value("default-agent"))
                .andExpect(jsonPath("$.supportedInterfaces[0].protocolBinding").value("JSONRPC"))
                .andExpect(jsonPath("$.supportedInterfaces[0].url").value("http://localhost:8080/a2a"))
                .andExpect(jsonPath("$.capabilities.streaming").value(true))
                .andExpect(jsonPath("$.skills[0].id").value("echo"));
    }

    @Test
    @DisplayName("Returns 304 when If-None-Match matches the card ETag")
    void shouldReturnNotModified() throws Exception {
        final var etag = mockMvc.perform(get(CARD_PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        mockMvc.perform(get(CARD_PATH).header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    @DisplayName("Serves tenant-specific cards")
    void shouldServeTenantCard() throws Exception {
        mockMvc.perform(get("/a2a/.well-known/acme/agent-card.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("tenant-agent"));
    }

    @Test
    @DisplayName("Unknown tenants return 404")
    void shouldRejectUnknownTenant() throws Exception {
        mockMvc.perform(get("/a2a/.well-known/unknown/agent-card.json"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        A2AServerProperties a2aServerProperties() {
            final var properties = new A2AServerProperties();
            properties.getAgentCard().setName("default-agent");
            properties.getAgentCard().setUrl("http://localhost:8080/a2a");
            properties.getAgentCard().setSkills(List.of(propertySkill("echo", "Echo")));
            properties.getCardCache().setMaxAge(Duration.ofSeconds(60));
            return properties;
        }

        @Bean
        AgentCard agentCard(final A2AServerProperties properties) {
            return new A2AServerAutoConfiguration().agentCard(properties, new MockEnvironment());
        }

        @Bean
        AgentCardRouter agentCardRouter(final AgentCard agentCard) {
            return new MapAgentCardRouter(agentCard, Map.of("acme", tenantCard()));
        }

        private static AgentCard tenantCard() {
            return AgentCard.builder()
                    .name("tenant-agent")
                    .description("tenant")
                    .version("1.0.0")
                    .capabilities(AgentCapabilities.builder().build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of(skill("tenant", "Tenant")))
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8080/a2a")))
                    .url("http://localhost:8080/a2a")
                    .build();
        }

        private static A2AServerProperties.Skill propertySkill(final String id, final String name) {
            final var skill = new A2AServerProperties.Skill();
            skill.setId(id);
            skill.setName(name);
            skill.setDescription(name);
            skill.setTags(List.of("test"));
            return skill;
        }

        private static AgentSkill skill(final String id, final String name) {
            return AgentSkill.builder()
                    .id(id)
                    .name(name)
                    .description(name)
                    .tags(List.of("test"))
                    .build();
        }
    }
}
