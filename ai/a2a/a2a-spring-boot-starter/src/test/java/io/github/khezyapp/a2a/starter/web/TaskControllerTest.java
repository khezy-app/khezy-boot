package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.error.TaskNotCancelableException;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({TaskController.class, SecurityCallerIdentityResolver.class})
class TaskControllerTest {

    private static final String GET_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"method\":\"tasks/get\",\"params\":{\"id\":\"t-1\"}}";

    private static final String CANCEL_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":\"req-2\",\"method\":\"tasks/cancel\",\"params\":{\"id\":\"t-1\"}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private A2ARequestDispatcher dispatcher;

    @Test
    @DisplayName("tasks/get returns the task in a JSON-RPC envelope")
    void shouldGetTask() throws Exception {
        when(dispatcher.onGetTask(any(), any())).thenReturn(Optional.of(WebFixtures.workingTask("t-1")));

        mockMvc.perform(post("/tasks/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.result.id").value("t-1"))
                .andExpect(jsonPath("$.result.status.state").value("TASK_STATE_WORKING"));
    }

    @Test
    @DisplayName("tasks/get on an unknown task yields the TaskNotFoundError envelope (-32001)")
    void shouldReportUnknownTask() throws Exception {
        when(dispatcher.onGetTask(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/tasks/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32001))
                .andExpect(jsonPath("$.error.message").value("Task not found"))
                .andExpect(jsonPath("$.error.details.taskId").value("t-1"));
    }

    @Test
    @DisplayName("tasks/cancel returns the canceled task in a JSON-RPC envelope")
    void shouldCancelTask() throws Exception {
        when(dispatcher.onCancelTask(any(), any())).thenReturn(WebFixtures.workingTask("t-1"));

        mockMvc.perform(post("/tasks/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CANCEL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("req-2"))
                .andExpect(jsonPath("$.result.id").value("t-1"));
    }

    @Test
    @DisplayName("tasks/cancel on a non-cancelable task yields the TaskNotCancelableError envelope (-32002)")
    void shouldReportNotCancelable() throws Exception {
        when(dispatcher.onCancelTask(any(), any())).thenThrow(new TaskNotCancelableException("t-1"));

        mockMvc.perform(post("/tasks/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CANCEL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32002))
                .andExpect(jsonPath("$.error.details.taskId").value("t-1"));
    }
}
