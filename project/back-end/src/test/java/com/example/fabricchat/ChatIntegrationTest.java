package com.example.fabricchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "fabric.data-dir=")
@AutoConfigureMockMvc
class ChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired FabricService service;

    @Test
    void answersAllSourceExamplesForBothFabrics() throws Exception {
        FabricService.IntentData data;
        try (var input = new ClassPathResource("data/intents.json").getInputStream()) {
            data = mapper.readValue(input, FabricService.IntentData.class);
        }
        for (var intent : data.intents()) {
            for (String example : intent.examples()) {
                for (String key : service.getFabrics().keySet()) {
                    String question = example.replace("cotton", key).replace("Cotton", key);
                    var fabric = service.getFabrics().get(key);
                    String expected = switch (intent.intent()) {
                        case "ask_price" -> key.equals("cotton") ? "85.000" : "120.000";
                        case "ask_color" -> String.join(", ", fabric.colors());
                        case "ask_stock" -> String.valueOf(fabric.stock());
                        default -> throw new AssertionError("Unknown intent");
                    };
                    for (String variant : new String[]{question, FabricService.normalize(question)}) {
                        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(Map.of("message", variant))))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.answer", containsString(fabric.name())))
                            .andExpect(jsonPath("$.answer", containsString(expected)));
                    }
                }
            }
        }
    }

    @Test
    void validatesInput() throws Exception {
        for (String body : new String[]{"{}", "{\"message\":\"   \"}", "{\"message\":null}", "not-json",
                mapper.writeValueAsString(Map.of("message", "a".repeat(1001)))}) {
            mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        }
    }

    @Test
    void handlesUnknownQueriesAndMultipleIntents() {
        assertThat(service.answer("Giá vải silk?")).contains("Hiện có").doesNotContain("85.000");
        assertThat(service.answer("Cotton dùng may gì?")).contains("Tôi có thể tra");
        assertThat(service.answer("Giá cotton và linen?")).contains("85.000", "120.000");
        assertThat(service.answer("Cotton giá bao nhiêu và có màu gì?")).contains("85.000", "đen, trắng, be");
    }

    @Test
    void servesCatalogAndFrontend() throws Exception {
        mvc.perform(get("/api/fabrics")).andExpect(status().isOk())
            .andExpect(jsonPath("$.cotton.price").value(85000))
            .andExpect(jsonPath("$.linen.stock").value(70));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Trợ lý Xưởng Vải")));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/style.css")).andExpect(status().isOk());
    }
}
