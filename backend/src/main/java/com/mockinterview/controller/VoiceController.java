package com.mockinterview.controller;

import com.mockinterview.dto.ApiResponse;
import com.mockinterview.dto.MurfVoiceDTO;
import com.mockinterview.service.MurfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/voice")
@Tag(name = "Voice", description = "Endpoints for voice provider configuration")
public class VoiceController {

    private final MurfService murfService;

    public VoiceController(MurfService murfService) {
        this.murfService = murfService;
    }

    @GetMapping("/murf")
    @Operation(summary = "List available Murf TTS voices for the setup picker")
    public ResponseEntity<ApiResponse<List<MurfVoiceDTO>>> getMurfVoices() {
        return ResponseEntity.ok(ApiResponse.success("Murf voices", murfService.getVoices()));
    }
}
