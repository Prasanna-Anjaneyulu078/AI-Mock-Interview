package com.mockinterview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MurfServiceTest {

    private static final String MURF_URL = "https://api.murf.ai/v1/speech/generate";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MurfService murfService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        murfService = new MurfService(restTemplate, "test-key", "en-US-natalie", "Conversational", 5000, 3, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void returnsAudioFileUrlOnSuccess() {
        server.expect(requestTo(MURF_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andRespond(withSuccess("{\"audioFile\":\"https://cdn.murf.ai/abc.mp3\"}", MediaType.APPLICATION_JSON));

        String result = murfService.generateSpeech("Hello there");
        assertEquals("https://cdn.murf.ai/abc.mp3", result);
        server.verify();
    }

    @Test
    void returnsDataUrlWhenOnlyEncodedAudioPresent() {
        server.expect(requestTo(MURF_URL))
                .andRespond(withSuccess("{\"encodedAudio\":\"QUJD\"}", MediaType.APPLICATION_JSON));

        String result = murfService.generateSpeech("Hi");
        assertNotNull(result);
        assertTrue(result.startsWith("data:audio/mp3;base64,"));
    }

    @Test
    void retriesOn500ThenSucceeds() {
        server.expect(requestTo(MURF_URL)).andRespond(withServerError());
        server.expect(requestTo(MURF_URL))
                .andRespond(withSuccess("{\"audioFile\":\"https://x/y.mp3\"}", MediaType.APPLICATION_JSON));

        String result = murfService.generateSpeech("Retry please");
        assertEquals("https://x/y.mp3", result);
    }

    @Test
    void doesNotRetryOn401() {
        server.expect(requestTo(MURF_URL)).andRespond(withUnauthorizedRequest());

        String result = murfService.generateSpeech("No auth");
        assertNull(result);
        server.verify(); // exactly one call expected (no retries on 401)
    }

    @Test
    void returnsNullWhenApiKeyMissing() {
        MurfService noKey = new MurfService(restTemplate, "  ", "en-US-natalie", "Conversational", 5000, 3, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        assertNull(noKey.generateSpeech("Hi"));
        server.verify(); // no HTTP call made
    }

    @Test
    void returnsNullWhenBlankText() {
        assertNull(murfService.generateSpeech("   "));
        server.verify();
    }

    @Test
    void returnsNullWhenAllRetriesFail() {
        server.expect(requestTo(MURF_URL)).andRespond(withServerError());
        server.expect(requestTo(MURF_URL)).andRespond(withServerError());
        server.expect(requestTo(MURF_URL)).andRespond(withServerError());

        assertNull(murfService.generateSpeech("Fail"));
    }

    @Test
    void passesVoiceOptionsInRequestBody() {
        server.expect(requestTo(MURF_URL))
                .andExpect(jsonPath("$.voiceId").value("en-US-natalie"))
                .andExpect(jsonPath("$.rate").value(50))
                .andExpect(jsonPath("$.format").value("MP3"))
                .andRespond(withSuccess("{\"audioFile\":\"https://x/z.mp3\"}", MediaType.APPLICATION_JSON));

        String result = murfService.generateSpeech(
                "Speedy", MurfVoiceOptions.builder().rate(50).style("Promo").build());
        assertEquals("https://x/z.mp3", result);
    }
}
