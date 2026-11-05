package io.github.khezyapp.a2a.coredef.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.AgentCardResolver;
import io.github.khezyapp.a2a.core.client.ClientBuildOptions;

class RemoteAgentRegistryTest {

    private static final String URL = "http://localhost:8080";

    private final AgentCardResolver resolver = mock(AgentCardResolver.class);
    private final A2AClientFactory factory = mock(A2AClientFactory.class);
    private final ClientBuildOptions options = ClientBuildOptions.of(mock(A2AHttpClient.class));

    @Test
    void constructorShouldRegisterConfiguredUrlsFailSoft() {
        final var card = TestFixtures.card("weather", URL, null, "weather", false);
        final var client = mock(Client.class);
        when(resolver.resolve(URL)).thenReturn(Optional.of(card));
        when(factory.create(eq(card), any())).thenReturn(client);

        final var registry = new RemoteAgentRegistry(resolver, factory, options, List.of(URL, "bad-url"));

        assertTrue(registry.find("weather").isPresent());
        assertEquals(1, registry.agents().size());
    }

    @Test
    void registerShouldFailSoftWhenTheCardIsMissing() {
        when(resolver.resolve(URL)).thenReturn(Optional.empty());

        final var registry = new RemoteAgentRegistry(resolver, factory, options);

        assertFalse(registry.register(URL).isPresent());
        assertTrue(registry.agents().isEmpty());
    }

    @Test
    void registerShouldFailSoftWhenTheClientCannotBeBuilt() {
        final var card = TestFixtures.card("weather", URL, null, "weather", false);
        when(resolver.resolve(URL)).thenReturn(Optional.of(card));
        when(factory.create(eq(card), any())).thenThrow(new IllegalStateException("boom"));

        final var registry = new RemoteAgentRegistry(resolver, factory, options);

        assertFalse(registry.register(URL).isPresent());
        assertTrue(registry.agents().isEmpty());
    }

    @Test
    void findShouldFallBackToCaseInsensitiveNames() {
        final var registry = registryWith(TestFixtures.card("Weather Agent", URL, null, "weather", false));

        assertTrue(registry.find("Weather Agent").isPresent());
        assertTrue(registry.find("weather agent").isPresent());
        assertTrue(registry.find(URL).isPresent());
    }

    @Test
    void findBySkillIdShouldReturnTheOwningAgent() {
        final var registry = registryWith(TestFixtures.card("weather", URL, null, "weather", false));

        final var agent = registry.findBySkillId("weather").orElseThrow();

        assertEquals("weather", agent.name());
        assertEquals(1, registry.skills().size());
        assertEquals("weather", registry.skills().get(0).id());
    }

    @Test
    void refreshShouldSwapClientsAndKeepTheOldOneOnFailure() {
        final var oldCard = TestFixtures.card("weather", URL, null, "weather", false);
        final AgentCard newCard = TestFixtures.card("weather-v2", URL, null, "weather", false);
        final var oldClient = mock(Client.class);
        final var newClient = mock(Client.class);
        when(resolver.resolve(URL)).thenReturn(Optional.of(oldCard));
        when(factory.create(eq(oldCard), any())).thenReturn(oldClient);
        final var registry = new RemoteAgentRegistry(resolver, factory, options);
        registry.register(URL);

        when(resolver.resolve(URL)).thenReturn(Optional.of(newCard));
        when(factory.create(eq(newCard), any())).thenReturn(newClient);
        registry.refresh();

        assertSame(newCard, registry.find("weather-v2").orElseThrow().card());
        verify(oldClient).close();

        when(resolver.resolve(URL)).thenReturn(Optional.empty());
        registry.refresh();

        assertSame(newCard, registry.find("weather-v2").orElseThrow().card());
        verify(newClient, never()).close();
    }

    @Test
    void unregisterShouldCloseTheClient() {
        final var card = TestFixtures.card("weather", URL, null, "weather", false);
        final var client = mock(Client.class);
        when(resolver.resolve(URL)).thenReturn(Optional.of(card));
        when(factory.create(eq(card), any())).thenReturn(client);
        final var registry = new RemoteAgentRegistry(resolver, factory, options);
        registry.register(URL);

        assertTrue(registry.unregister("WEATHER"));
        verify(client).close();
        assertTrue(registry.agents().isEmpty());
    }

    @Test
    void closeShouldBeIdempotent() {
        final var card = TestFixtures.card("weather", URL, null, "weather", false);
        final var client = mock(Client.class);
        when(resolver.resolve(URL)).thenReturn(Optional.of(card));
        when(factory.create(eq(card), any())).thenReturn(client);
        final var registry = new RemoteAgentRegistry(resolver, factory, options);
        registry.register(URL);

        registry.close();
        registry.close();

        verify(client, times(1)).close();
        assertTrue(registry.agents().isEmpty());
    }

    private RemoteAgentRegistry registryWith(final AgentCard card) {
        final var client = mock(Client.class);
        when(resolver.resolve(URL)).thenReturn(Optional.of(card));
        when(factory.create(eq(card), any())).thenReturn(client);
        final var registry = new RemoteAgentRegistry(resolver, factory, options);
        registry.register(URL);
        return registry;
    }
}
