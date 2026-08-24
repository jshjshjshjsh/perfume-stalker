package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import com.grove.perfumestalker.enums.NotionPerfumeMaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {

    private final WebClient notionWebClient;
    private final NotionModule notionModule;

    @Value("${notion.db.master-id}")
    private String masterDbId;

    @Value("${notion.db.master-data-source-id}")
    private String masterDataSourceId;

    /**
     * 1. NFC UID로 마스터 DB 조회
     */
    public Mono<String> findPerfumePageIdByUid(String uid) {
        String formattedDataSourceId = notionModule.formatUuid(masterDataSourceId);
        NotionPerfumeMaster uidCol = NotionPerfumeMaster.UID;

        // Enum을 활용한 필터 쿼리 조립
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", uidCol.getColumnName(),
                        uidCol.getPropertyType(), Map.of("equals", uid)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDataSourceId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(NotionQueryResponse.class)
                .map(response -> {
                    if (response.results().isEmpty()) {
                        log.warn("등록되지 않은 NFC UID 스캔됨: {}", uid);
                        throw new IllegalArgumentException("Unregistered NFC UID");
                    }
                    return response.results().get(0).id();
                })
                .doOnError(this::handleNotionError);
    }

    /**
     * 2. 새로운 향수 마스터 데이터 생성
     */
    public Mono<String> createPerfumeMaster(PerfumeRegisterRequest req) {
        String formattedDbId = notionModule.formatUuid(masterDbId);
        Map<String, Object> properties = new HashMap<>();

        // 필수 값 세팅 (Enum 전략 패턴 적용)
        properties.put(NotionPerfumeMaster.NAME.getColumnName(), NotionPerfumeMaster.NAME.formatValue(req.getName()));
        properties.put(NotionPerfumeMaster.UID.getColumnName(), NotionPerfumeMaster.UID.formatValue(req.getUid()));

        // 선택적 값 세팅
        if (req.getBrand() != null && !req.getBrand().isBlank()) {
            properties.put(NotionPerfumeMaster.BRAND.getColumnName(), NotionPerfumeMaster.BRAND.formatValue(req.getBrand()));
        }
        if (req.getNotes() != null && !req.getNotes().isEmpty()) {
            properties.put(NotionPerfumeMaster.NOTES.getColumnName(), NotionPerfumeMaster.NOTES.formatValue(req.getNotes()));
        }
        if (req.getUrl() != null && !req.getUrl().isBlank()) {
            properties.put(NotionPerfumeMaster.URL.getColumnName(), NotionPerfumeMaster.URL.formatValue(req.getUrl()));
        }
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            properties.put(NotionPerfumeMaster.IMAGE.getColumnName(), NotionPerfumeMaster.IMAGE.formatValue(req.getImageUrl()));
        }

        Map<String, Object> body = Map.of(
                "parent", Map.of("type", "database_id", "database_id", formattedDbId),
                "properties", properties
        );

        return notionWebClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(NotionPageResponse.class)
                .map(NotionPageResponse::id)
                .doOnSuccess(id -> log.info("✅ 새 향수 등록 완료: {}", req.getName()))
                .doOnError(this::handleNotionError);
    }

    /**
     * 중복되는 에러 처리 로직을 메서드로 분리 (가독성 향상)
     */
    private void handleNotionError(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("❌ 노션 API 40x/50x 에러 상세: {}", errorBody);
        } else {
            log.error("❌ 노션 통신 중 예외 발생: ", e);
        }
    }

    // --- 내부 전용 DTO ---
    private record NotionQueryResponse(List<NotionPage> results) {
        private record NotionPage(String id) {}
    }
    private record NotionPageResponse(String id) {}
}