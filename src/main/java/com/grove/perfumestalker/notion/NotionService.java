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

    private String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id; // 이미 비정상이면 원본 반환
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    /**
     * NFC UID로 노션 향수 마스터 DB를 검색하여 향수 페이지 ID를 반환
     */
    public Mono<String> findPerfumePageIdByUid(String uid) {
        String formattedDbId = formatUuid(masterDbId);

        // 노션 API 쿼리용 Filter 페이로드 (UID 컬럼이 전달받은 uid와 일치하는가?)
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "UID",
                        "rich_text", Map.of("equals", uid)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
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
                .doOnError(e -> {
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getResponseBodyAsString();
                        log.error("❌ 노션 API 400 에러 상세 내용: {}", errorBody);
                    } else {
                        log.error("❌ Notion API 연동 에러: ", e);
                    }
                });
    }
}