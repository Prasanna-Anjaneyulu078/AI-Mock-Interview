package com.mockinterview.config;

import com.mockinterview.config.properties.AssemblyAiProperties;
import com.mockinterview.config.properties.GroqProperties;
import com.mockinterview.config.properties.Judge0Properties;
import com.mockinterview.config.properties.MurfProperties;
import com.mockinterview.config.properties.OllamaProperties;
import com.mockinterview.config.properties.OpenRouterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Provides one {@link RestTemplate} per external AI provider, each with an explicit
 * connect/read timeout sourced from that provider's {@code timeout-ms} config. This
 * guarantees no outbound call (Gemini, AssemblyAI, Murf, Judge0) can hang
 * indefinitely, and keeps each provider's timeout centrally configured.
 *
 * <p>Also configures a virtual-thread {@link Executor} (Java 21) named
 * {@code taskExecutor} so {@code @Async} generation tasks (follow-ups, feedback)
 * don't block platform threads.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({
        OpenRouterProperties.class,
        GroqProperties.class,
        OllamaProperties.class,
        AssemblyAiProperties.class,
        MurfProperties.class,
        Judge0Properties.class
})
public class RestTemplateConfiguration {

    @Bean("assemblyAiRestTemplate")
    public RestTemplate assemblyAiRestTemplate(AssemblyAiProperties props) {
        return build(props.getTimeoutMs());
    }

    @Bean("murfRestTemplate")
    public RestTemplate murfRestTemplate(MurfProperties props) {
        return build(props.getTimeoutMs());
    }

    @Bean("judge0RestTemplate")
    public RestTemplate judge0RestTemplate(Judge0Properties props) {
        return build(props.getTimeoutMs());
    }

    /** Build a RestTemplate whose socket timeouts match the given provider timeout (ms). */
    private RestTemplate build(int timeoutMs) {
        int t = timeoutMs > 0 ? timeoutMs : 10_000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);
        return new RestTemplate(factory);
    }

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        // Java 21 virtual threads: one lightweight thread per async task.
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
