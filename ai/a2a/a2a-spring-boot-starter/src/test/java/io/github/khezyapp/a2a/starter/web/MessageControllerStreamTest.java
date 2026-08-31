package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.starter.security.SecurityCallerIdentityResolver;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MessageController.class, SecurityCallerIdentityResolver.class})
class MessageControllerStreamTest {

    private static final String STREAM_BODY = """
            {
              "jsonrpc": "2.0",
              "id": "req-7",
              "method": "message/stream",
              "params": {
                "message": {
                  "role": "ROLE_USER",
                  "messageId": "m-7",
                  "parts": [{"kind": "text", "text": "stream please"}]
                }
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private A2ARequestDispatcher dispatcher;

    @Test
    @DisplayName("message/stream responds with text/event-stream and at least one SSE data frame")
    void shouldStreamSseFrames() throws Exception {
        doAnswer(invocation -> {
            final var sink = invocation.getArgument(1, AgentEventSink.class);
            sink.completed(WebFixtures.completedUpdate());
            return null;
        }).when(dispatcher).onMessageStream(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        final var async = mockMvc.perform(post("/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STREAM_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();

        final var response = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse();
        final var body = response.getContentAsString();

        final var frames = Arrays.stream(body.split("data:")).filter(frame -> !frame.isBlank()).toList();
        assertThat(frames.size()).isGreaterThanOrEqualTo(1);
        assertThat(body).contains("\"taskId\":\"t-1\"");
        assertThat(body).contains("\"id\":\"req-7\"");
    }

    @Test
    @DisplayName("intermediate sink events become additional SSE frames before the terminal frame")
    void shouldEmitIntermediateFrames() throws Exception {
        doAnswer(invocation -> {
            final var sink = invocation.getArgument(1, AgentEventSink.class);
            sink.statusChanged(TaskState.TASK_STATE_WORKING, "working");
            sink.completed(WebFixtures.completedUpdate());
            return null;
        }).when(dispatcher).onMessageStream(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        final var async = mockMvc.perform(post("/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STREAM_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();

        final var body = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("status-update");
        final var frames = Arrays.stream(body.split("data:")).filter(frame -> !frame.isBlank()).toList();
        assertThat(frames.size()).isGreaterThanOrEqualTo(2);
    }
}
