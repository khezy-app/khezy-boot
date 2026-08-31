package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.starter.security.SecurityCallerIdentityResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MessageController.class, SecurityCallerIdentityResolver.class})
class MessageControllerTest {

    private static final String SEND_BODY = """
            {
              "jsonrpc": "2.0",
              "id": "req-1",
              "method": "message/send",
              "params": {
                "message": {
                  "role": "ROLE_USER",
                  "messageId": "m-1",
                  "parts": [{"kind": "text", "text": "hello agent"}]
                }
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private A2ARequestDispatcher dispatcher;

    @Test
    @DisplayName("message/send returns the dispatcher result in a JSON-RPC envelope with the request id")
    void shouldSendAndReturnEnvelope() throws Exception {
        when(dispatcher.onMessageSend(any(), any())).thenReturn(WebFixtures.completedUpdate());

        mockMvc.perform(post("/message/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEND_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.result.taskId").value("t-1"))
                .andExpect(jsonPath("$.result.contextId").value("c-1"))
                .andExpect(jsonPath("$.result.status.state").value("TASK_STATE_COMPLETED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("unknown JSON-RPC method yields a MethodNotFoundError envelope (-32601)")
    void shouldRejectUnknownMethod() throws Exception {
        final var body = SEND_BODY.replace("\"method\": \"message/send\"", "\"method\": \"message/bogus\"");

        mockMvc.perform(post("/message/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.error.code").value(-32601))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("malformed params yield an InvalidParamsError envelope (-32602)")
    void shouldRejectEmptyParts() throws Exception {
        final var body = """
                {"jsonrpc":"2.0","id":"req-2","method":"message/send",
                 "params":{"message":{"role":"ROLE_USER","parts":[]}}}
                """;
        when(dispatcher.onMessageSend(any(), any())).thenReturn(WebFixtures.completedUpdate());

        mockMvc.perform(post("/message/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("req-2"))
                .andExpect(jsonPath("$.error.code").value(-32602));
    }
}
