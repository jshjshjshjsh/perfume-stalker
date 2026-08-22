package com.pond.perfumestalker.notion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class NotionConfig {

    @Value("${notion.api.url}")
    private String baseUrl;

    @Value("${notion.api.version}")
    private String notionVersion;

    @Value("${notion.api.token}")
    private String notionToken;

    @Bean
    public WebClient notionWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .defaultHeader("Notion-Version", notionVersion) // 노션 API 필수 헤더
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}