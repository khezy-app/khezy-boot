package io.github.khezyapp.aielements.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end smoke test for the runnable sample. Runs under the default {@code mock}
 * profile (no DeepSeek key needed) and asserts the SSE stream starts with a
 * {@code start} event and terminates with the {@code [DONE]} sentinel.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiElementsSampleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/chat streams the mock UI event set and ends with DONE")
    void mockChatStreamsAndEndsWithDone() throws Exception {
        final var request = post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept("text/event-stream")
                .content("{\"id\":\"chat_1\",\"messages\":[{\"id\":\"m1\",\"role\":\"user\","
                        + "\"content\":\"hello\"}],\"trigger\":\"submit-message\",\"messageId\":null}");

        final var result = mockMvc.perform(request)
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(stream -> {
                    final var body = stream.getResponse().getContentAsString();
                    assertTrue(body.contains("data:{\"type\":\"start\""));
                    assertTrue(body.contains("data:[DONE]"));
                });
    }
}
