package io.github.khezyapp.a2a.coredef.client.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.khezyapp.a2a.core.client.RemoteAgent;
import io.github.khezyapp.a2a.core.client.invoke.SendOptions;
import io.github.khezyapp.a2a.core.client.invoke.SendRequest;
import io.github.khezyapp.a2a.coredef.client.TestFixtures;

class SdkA2AInvokerTest {

    private static final String URL = "http://localhost:8080";

    @Test
    void shouldBuildMessageParamsAndCallContext() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, "tenant-1", "weather", false, client);
        answerWith(client, agent.card(), new MessageEvent(agentMessage("done")));
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).build());

        assertNotNull(outcome);
        final var paramsCaptor = ArgumentCaptor.forClass(MessageSendParams.class);
        final var contextCaptor = ArgumentCaptor.forClass(ClientCallContext.class);
        verify(client).sendMessage(paramsCaptor.capture(), anyList(), any(), contextCaptor.capture());

        final var params = paramsCaptor.getValue();
        final var message = params.message();
        assertEquals(Message.Role.ROLE_USER, message.role());
        assertNotNull(message.messageId());
        assertEquals("hello", ((TextPart) message.parts().get(0)).text());
        assertEquals("ctx-1", message.contextId());
        assertEquals("task-1", message.taskId());
        assertEquals(List.of("ref-1"), message.referenceTaskIds());
        assertEquals(List.of("ext-1"), message.extensions());
        assertEquals("weather", message.metadata().get(SdkA2AInvoker.SKILL_ID_METADATA_KEY));
        assertEquals("v", message.metadata().get("m"));
        assertEquals("v", params.metadata().get("p"));
        assertEquals("tenant-1", params.tenant());

        assertEquals(Map.of("X", "Y"), contextCaptor.getValue().getHeaders());
        assertEquals(Map.of("s", "t"), contextCaptor.getValue().getState());

        assertEquals("done", outcome.message());
        verify(client, never()).close();
    }

    @Test
    void explicitTenantShouldWinOverTheAgentTenant() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, "tenant-1", "weather", false, client);
        answerWith(client, agent.card(), new MessageEvent(agentMessage("done")));
        final var invoker = new SdkA2AInvoker();

        invoker.send(request(agent).tenant("tenant-2").build());

        final var paramsCaptor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(client).sendMessage(paramsCaptor.capture(), anyList(), any(), any());
        assertEquals("tenant-2", paramsCaptor.getValue().tenant());
    }

    @Test
    void terminalTaskEventShouldComplete() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        answerWith(client, agent.card(), new TaskEvent(task(TaskState.TASK_STATE_COMPLETED)));
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).build());

        assertEquals(TaskState.TASK_STATE_COMPLETED, outcome.state());
        assertTrue(outcome.isFinal());
        assertEquals("ctx-1", outcome.contextId());
        assertEquals("task-1", outcome.taskId());
        assertEquals("done", outcome.message());
    }

    @Test
    void taskUpdateEventShouldComplete() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        final var task = task(TaskState.TASK_STATE_COMPLETED);
        answerWith(client, agent.card(), new TaskUpdateEvent(task,
                new TaskStatusUpdateEvent("task-1", task.status(), "ctx-1", null)));
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).build());

        assertEquals(TaskState.TASK_STATE_COMPLETED, outcome.state());
        assertTrue(outcome.isFinal());
    }

    @Test
    void interruptedTaskEventShouldCompleteWhenStopOnInterrupt() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        answerWith(client, agent.card(), new TaskEvent(task(TaskState.TASK_STATE_INPUT_REQUIRED)));
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).build());

        assertTrue(outcome.isInterrupted());
        verify(client, never()).close();
    }

    @Test
    void returnImmediatelyShouldCompleteOnTheFirstEvent() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        answerWith(client, agent.card(), new TaskEvent(task(TaskState.TASK_STATE_WORKING)));
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent)
                .configuration(MessageSendConfiguration.builder().returnImmediately(true).build())
                .build());

        assertEquals(TaskState.TASK_STATE_WORKING, outcome.state());
        assertTrue(outcome.isPending());
    }

    @Test
    void timeoutShouldProduceAnError() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", true, client);
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent)
                .options(SendOptions.builder().timeout(Duration.ofMillis(20)).build())
                .build());

        assertTrue(outcome.isError());
        assertTrue(outcome.error().contains("timed out"));
        verify(client, never()).close();
    }

    @Test
    void clientFailureShouldProduceAnError() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        doThrow(new A2AClientException("boom"))
                .when(client)
                .sendMessage(any(MessageSendParams.class), anyList(), any(), any());
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).build());

        assertTrue(outcome.isError());
        assertEquals("boom", outcome.error());
        verify(client, never()).close();
    }

    @Test
    void missingPartsShouldProduceAnError() {
        final var client = mock(Client.class);
        final var agent = TestFixtures.agent("weather", URL, null, "weather", false, client);
        final var invoker = new SdkA2AInvoker();

        final var outcome = invoker.send(request(agent).parts(List.of()).build());

        assertTrue(outcome.isError());
        assertNull(outcome.state());
    }

    private static SendRequest.Builder request(final RemoteAgent agent) {
        return SendRequest.builder()
                .agent(agent)
                .part(new TextPart("hello", null))
                .skillId("weather")
                .contextId("ctx-1")
                .taskId("task-1")
                .referenceTaskIds(List.of("ref-1"))
                .extensions(List.of("ext-1"))
                .messageMetadata(Map.of("m", "v"))
                .paramsMetadata(Map.of("p", "v"))
                .configuration(MessageSendConfiguration.builder()
                        .acceptedOutputModes(List.of("text"))
                        .build())
                .options(SendOptions.builder()
                        .headers(Map.of("X", "Y"))
                        .state(Map.of("s", "t"))
                        .build());
    }

    private static void answerWith(final Client client, final AgentCard card, final ClientEvent event) {
        doAnswer(invocation -> {
            final List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
            consumers.forEach(consumer -> consumer.accept(event, card));
            return null;
        }).when(client).sendMessage(any(MessageSendParams.class), anyList(), any(), any());
    }

    private static Task task(final TaskState state) {
        return Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(state, agentMessage("done"), null))
                .build();
    }

    private static Message agentMessage(final String text) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(text, null)))
                .build();
    }
}
