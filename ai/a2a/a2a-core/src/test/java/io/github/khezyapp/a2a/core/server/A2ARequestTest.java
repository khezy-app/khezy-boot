package io.github.khezyapp.a2a.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class A2ARequestTest {

    @Test
    void shouldRejectNullConstructorArguments() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);

        assertThrows(NullPointerException.class, () -> new A2ARequest(null, emitter));
        assertThrows(NullPointerException.class, () -> new A2ARequest(context, null));
    }

    @Test
    void replyShouldSendAnAgentMessage() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.reply("hello");

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).sendMessage(captor.capture());
        assertEquals(Message.Role.ROLE_AGENT, captor.getValue().role());
        assertEquals("hello", textOf(captor.getValue().parts().get(0)));
    }

    @Test
    void replyWithMetadataShouldAttachMetadata() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.reply("hello", Map.of("lang", "en"));

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).sendMessage(captor.capture());
        assertEquals(Map.of("lang", "en"), captor.getValue().metadata());
    }

    @Test
    void artifactShouldAddNamedTextArtifact() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var request = new A2ARequest(context, emitter);

        request.artifact("summary", "the text");

        final var captor = ArgumentCaptor.forClass(List.class);
        verify(emitter).addArtifact(captor.capture(), isNull(), eq("summary"), eq(Map.of()));
        assertEquals("the text", textOf((Part<?>) captor.getValue().get(0)));
    }

    @Test
    void artifactShouldKeepDescription() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        when(context.getTaskId()).thenReturn("task-1");
        when(context.getContextId()).thenReturn("ctx-1");
        final var request = new A2ARequest(context, emitter);

        request.artifact("summary", "a short summary", "the text");

        final var captor = ArgumentCaptor.forClass(Event.class);
        verify(emitter).emitEvent(captor.capture());
        final var event = assertInstanceOf(TaskArtifactUpdateEvent.class, captor.getValue());
        assertEquals("task-1", event.taskId());
        assertEquals("ctx-1", event.contextId());
        assertEquals("summary", event.artifact().name());
        assertEquals("a short summary", event.artifact().description());
        assertEquals("the text", textOf(event.artifact().parts().get(0)));
    }

    @Test
    void completeWithoutTextShouldUseNoArgEmitterMethod() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var request = new A2ARequest(context, emitter);

        request.complete();

        verify(emitter).complete();
    }

    @Test
    void completeWithTextShouldSendStatusMessage() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.complete("done");

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).complete(captor.capture());
        assertEquals("done", textOf(captor.getValue().parts().get(0)));
    }

    @Test
    void requireInputShouldSendQuestion() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.requireInput("which one?");

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).requiresInput(captor.capture());
        assertEquals("which one?", textOf(captor.getValue().parts().get(0)));
    }

    @Test
    void requireAuthShouldSendInstruction() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.requireAuth("please log in");

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).requiresAuth(captor.capture());
        assertEquals("please log in", textOf(captor.getValue().parts().get(0)));
    }

    @Test
    void failShouldSendReason() {
        final var context = mock(RequestContext.class);
        final var emitter = mockEmitter();
        final var request = new A2ARequest(context, emitter);

        request.fail("boom");

        final var captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(captor.capture());
        assertEquals("boom", textOf(captor.getValue().parts().get(0)));
    }

    @Test
    void cancelShouldDelegateToEmitter() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var request = new A2ARequest(context, emitter);

        request.cancel();

        verify(emitter).cancel();
    }

    @Test
    void idsShouldFallBackToCurrentTask() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var task = Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .build();
        when(context.getTask()).thenReturn(task);
        final var request = new A2ARequest(context, emitter);

        assertEquals("task-1", request.taskId());
        assertEquals("ctx-1", request.contextId());
        assertEquals(task, request.currentTask().orElseThrow());
    }

    @Test
    void attributesShouldMergeMetadataAndCallContextState() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var callContext = new ServerCallContext(
                new AuthenticatedUser("alice"),
                Map.of("shared", "state", "stateOnly", 2),
                Set.of());
        when(context.getMetadata()).thenReturn(Map.of("shared", "metadata", "metaOnly", 1));
        when(context.getCallContext()).thenReturn(callContext);
        final var request = new A2ARequest(context, emitter);

        final var attributes = request.attributes();

        assertEquals("state", attributes.get("shared"));
        assertEquals(1, attributes.get("metaOnly"));
        assertEquals(2, attributes.get("stateOnly"));
        assertThrows(UnsupportedOperationException.class, () -> attributes.put("new", "value"));
    }

    @Test
    void callerShouldBeEmptyForUnauthenticatedUser() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var callContext = new ServerCallContext(UnauthenticatedUser.INSTANCE, Map.of(), Set.of());
        when(context.getCallContext()).thenReturn(callContext);
        final var request = new A2ARequest(context, emitter);

        assertFalse(request.caller().isPresent());
    }

    @Test
    void callerShouldReturnAuthenticatedUser() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var user = new AuthenticatedUser("alice");
        final var callContext = new ServerCallContext(user, Map.of(), Set.of());
        when(context.getCallContext()).thenReturn(callContext);
        final var request = new A2ARequest(context, emitter);

        assertSame(user, request.caller().orElseThrow());
    }

    @Test
    void tenantShouldFallBackToCallContextState() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var callContext = new ServerCallContext(
                UnauthenticatedUser.INSTANCE,
                Map.of(JSONRPCContextKeys.TENANT_KEY, "tenant-1"),
                Set.of());
        when(context.getCallContext()).thenReturn(callContext);
        final var request = new A2ARequest(context, emitter);

        assertEquals("tenant-1", request.tenant().orElseThrow());
    }

    @Test
    void relatedTasksShouldBeImmutableCopy() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        final var task = Task.builder()
                .id("task-2")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .build();
        when(context.getRelatedTasks()).thenReturn(List.of(task));
        final var request = new A2ARequest(context, emitter);

        final var related = request.relatedTasks();

        assertEquals(1, related.size());
        assertThrows(UnsupportedOperationException.class, () -> related.add(task));
    }

    @Test
    void userInputShouldComeFromSdkContext() {
        final var context = mock(RequestContext.class);
        final var emitter = mock(AgentEmitter.class);
        when(context.getUserInput()).thenReturn("input");
        final var request = new A2ARequest(context, emitter);

        assertEquals("input", request.userInput());
    }

    private static AgentEmitter mockEmitter() {
        final var emitter = mock(AgentEmitter.class);
        when(emitter.messageBuilder()).thenAnswer(invocation -> Message.builder()
                .messageId("agent-message")
                .contextId("ctx-1")
                .taskId("task-1")
                .role(Message.Role.ROLE_AGENT));
        return emitter;
    }

    private static String textOf(final Part<?> part) {
        return ((TextPart) part).text();
    }
}
