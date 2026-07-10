package com.mockinterview.controller;

import com.mockinterview.service.ai.OpenRouterProvider;
import com.mockinterview.service.ai.GroqProvider;
import com.mockinterview.service.ai.OllamaProvider;
import com.mockinterview.service.ai.AIProviderRouter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health/ai")
public class AIHealthController {

    private final OpenRouterProvider openRouter;
    private final GroqProvider groq;
    private final OllamaProvider ollama;
    private final AIProviderRouter router;

    public AIHealthController(OpenRouterProvider openRouter, GroqProvider groq, OllamaProvider ollama, AIProviderRouter router) {
        this.openRouter = openRouter;
        this.groq = groq;
        this.ollama = ollama;
        this.router = router;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        long start = System.currentTimeMillis();
        
        Map<String, Object> response = new HashMap<>();
        response.put("openrouter", openRouter.isHealthy() ? "UP" : "DOWN");
        response.put("groq", groq.isHealthy() ? "UP" : "DOWN");
        response.put("ollama", ollama.isHealthy() ? "UP" : "DOWN");
        
        response.put("activeProvider", router.getActiveProvider());
        
        long end = System.currentTimeMillis();
        response.put("latency", (end - start));
        
        return ResponseEntity.ok(response);
    }
}

