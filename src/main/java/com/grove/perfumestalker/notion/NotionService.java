package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {

    private final WebClient notionWebClient;

    @Value("${notion.db.master-id}")
    private String masterDbId;

    @Value("${notion.db.master-data-source-id}")
    private String masterDataSourceId;

    private String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id;
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    /**
     * NFC UID로 마스터 DB 조회 (데이터 소스 ID 사용)
     */
    public Mono<String> findPerfumePageIdByUid(String uid) {
        // 여기는 Query 전용인 masterDataSourceId 사용
        String formattedDataSourceId = formatUuid(masterDataSourceId);

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "UID",
                        "rich_text", Map.of("equals", uid)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDataSourceId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    var results = (java.util.List<Map<String, Object>>) response.get("results");
                    if (results.isEmpty()) {
                        log.warn("등록되지 않은 NFC UID 스캔됨: {}", uid);
                        throw new IllegalArgumentException("Unregistered NFC UID");
                    }
                    return (String) results.get(0).get("id");
                })
                .doOnError(e -> {
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getResponseBodyAsString();
                        log.error("❌ 마스터 DB 조회 400 에러 상세: {}", errorBody);
                    } else {
                        log.error("❌ Notion API 조회 연동 에러: ", e);
                    }
                });
    }

    /**
     * 새로운 향수 마스터 데이터 생성 (오리지널 DB ID 사용)
     */
    public Mono<String> createPerfumeMaster(PerfumeRegisterRequest req) {
        // 여기는 Insert 전용인 오리지널 masterDbId 사용
        String formattedDbId = formatUuid(masterDbId);

        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        properties.put("NAME", Map.of("title", List.of(Map.of("text", Map.of("content", req.getName())))));
        properties.put("UID", Map.of("rich_text", List.of(Map.of("text", Map.of("content", req.getUid())))));

        if (req.getBrand() != null && !req.getBrand().isBlank()) {
            properties.put("BRAND", Map.of("select", Map.of("name", req.getBrand())));
        }
        if (req.getNotes() != null && !req.getNotes().isEmpty()) {
            List<Map<String, String>> multiSelect = req.getNotes().stream()
                    .map(note -> Map.of("name", note))
                    .toList();
            properties.put("NOTES", Map.of("multi_select", multiSelect));
        }
        if (req.getUrl() != null && !req.getUrl().isBlank()) {
            properties.put("URL", Map.of("url", req.getUrl()));
        }
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            properties.put("IMAGE", Map.of("files", List.of(
                    Map.of("name", "향수 썸네일", "type", "external", "external", Map.of("url", req.getImageUrl()))
            )));
        }

        Map<String, Object> body = Map.of(
                "parent", Map.of("type", "database_id", "database_id", formattedDbId),
                "properties", properties
        );

        return notionWebClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> (String) res.get("id"))
                .doOnSuccess(id -> log.info("✅ 새 향수 등록 완료: {}", req.getName()))
                .doOnError(e -> {
                    // 💡 스키마 에러를 잡기 위한 상세 로깅 추가
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getResponseBodyAsString();
                        log.error("❌ 노션 향수 등록 에러 상세: {}", errorBody);
                    } else {
                        log.error("❌ 향수 등록 중 에러: ", e);
                    }
                });
    }
}