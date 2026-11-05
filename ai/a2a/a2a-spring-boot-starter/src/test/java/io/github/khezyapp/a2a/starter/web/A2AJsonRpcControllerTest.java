package io.github.khezyapp.a2a.starter.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.khezyapp.a2a.core.server.A2ACallContextFactory;
import io.github.khezyapp.a2a.starter.A2AServerProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({A2AJsonRpcController.class, A2AJsonRpcControllerTest.TestConfig.class})
class A2AJsonRpcControllerTest {

    private static final String SEND_MESSAGE = "{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"method\":\"SendMessage\","
            + "\"params\":{\"message\":{\"role\":\"ROLE_USER\",\"messageId\":\"msg-1\","
            + "\"parts\":[{\"text\":\"hello\"}]}}}";

    private static final String GET_TASK =
            "{\"jsonrpc\":\"2.0\",\"id\":\"req-2\",\"method\":\"GetTask\",\"params\":{\"id\":\"task-1\"}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JSONRPCHandler jsonRpcHandler;

    @Test
    @DisplayName("Dispatches message/send and serializes the task result")
    void shouldHandleSendMessage() throws Exception {
        when(jsonRpcHandler.onMessageSend(any(SendMessageRequest.class), any(ServerCallContext.class)))
                .thenReturn(new SendMessageResponse("req-1", task()));

        mockMvc.perform(post("/a2a").contentType(MediaType.APPLICATION_JSON).content(SEND_MESSAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.result.task.id").value("task-1"))
                .andExpect(jsonPath("$.result.task.status.state").value("TASK_STATE_COMPLETED"));
    }

    @Test
    @DisplayName("Accepts the SDK application/a2a+json content type")
    void shouldAcceptA2aJson() throws Exception {
        when(jsonRpcHandler.onMessageSend(any(SendMessageRequest.class), any(ServerCallContext.class)))
                .thenReturn(new SendMessageResponse("req-1", task()));

        mockMvc.perform(post("/a2a").contentType(A2AJsonRpcController.A2A_JSON_VALUE).content(SEND_MESSAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.task.id").value("task-1"));
    }

    @Test
    @DisplayName("Dispatches tasks/get")
    void shouldHandleGetTask() throws Exception {
        when(jsonRpcHandler.onGetTask(any(GetTaskRequest.class), any(ServerCallContext.class)))
                .thenReturn(new GetTaskResponse("req-2", task()));

        mockMvc.perform(post("/a2a").contentType(MediaType.APPLICATION_JSON).content(GET_TASK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("task-1"));
    }

    @Test
    @DisplayName("Unknown methods return a MethodNotFound error")
    void shouldRejectUnknownMethod() throws Exception {
        mockMvc.perform(post("/a2a").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":\"req-3\",\"method\":\"Unknown/Method\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    @DisplayName("Malformed bodies return an InvalidRequest error")
    void shouldRejectMalformedBody() throws Exception {
        mockMvc.perform(post("/a2a").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32600));
    }

    private static Task task() {
        return Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of())
                .history(List.of())
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        A2AServerProperties a2aServerProperties() {
            return new A2AServerProperties();
        }

        @Bean
        JSONRPCHandler jsonRpcHandler() {
            return Mockito.mock(JSONRPCHandler.class);
        }

        @Bean
        ServletA2AServerJsonRpcHandler servletA2AServerJsonRpcHandler(final JSONRPCHandler jsonRpcHandler) {
            return new ServletA2AServerJsonRpcHandler(jsonRpcHandler);
        }

        @Bean
        A2ACallContextFactory<HttpServletRequest> callContextFactory(final A2AServerProperties properties) {
            return new ServletA2ACallContextFactory(properties);
        }
    }
}
