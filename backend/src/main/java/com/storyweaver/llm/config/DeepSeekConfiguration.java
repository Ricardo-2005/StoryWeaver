package com.storyweaver.llm.config;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DeepSeekProperties.class, EmbeddingProperties.class, RetrievalProperties.class})
public class DeepSeekConfiguration {

    @Bean
    HttpClient deepSeekJdkHttpClient(DeepSeekProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Bean(destroyMethod = "close")
    ExecutorService aiTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "close")
    ScheduledExecutorService aiHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("ai-heartbeat-", 0).factory());
    }
}
