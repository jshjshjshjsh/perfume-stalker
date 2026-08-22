package com.grove.perfumestalker.notion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {

    private final WebClient notionWebClient;

    @Value("${notion.db.master-id}")
    private String masterDbId;

    /**
     * NFC UID로 노션 향수 마스터 DB를 검색하여 향수 페이지 ID를 반환
     */
    public Mono<String> findPerfumePageIdByUid(String uid) {
        // 노션 API 쿼리용 Filter 페이로드 (UID 컬럼이 전달받은 uid와 일치하는가?)
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "UID",
                        "rich_text", Map.of("equals", uid)
                )
        );

        return notionWebClient.post()
                .uri("/databases/{dbId}/query", masterDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // 결과 리스트(results) 파싱 로직
                    var results = (java.util.List<Map<String, Object>>) response.get("results");
                    if (results.isEmpty()) {
                        log.warn("등록되지 않은 NFC UID 스캔됨: {}", uid);
                        throw new IllegalArgumentException("Unregistered NFC UID");
                    }
                    // 검색된 향수 데이터의 Notion Page ID 반환 (착향 로그 연결용)
                    return (String) results.get(0).get("id");
                })
                .doOnError(e -> log.error("Notion API 연동 에러: ", e));
    }
}