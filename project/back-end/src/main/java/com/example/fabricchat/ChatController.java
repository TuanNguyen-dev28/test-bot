package com.example.fabricchat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {
    public record ChatRequest(
        @NotBlank(message = "Bạn hãy nhập câu hỏi.")
        @Size(max = 1000, message = "Câu hỏi tối đa 1.000 ký tự.") String message) {}
    public record ChatResponse(String answer) {}

    private final FabricService service;

    public ChatController(FabricService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return new ChatResponse(service.answer(request.message()));
    }

    @GetMapping("/fabrics")
    public Map<String, FabricService.Fabric> fabrics() {
        return service.getFabrics();
    }
}
