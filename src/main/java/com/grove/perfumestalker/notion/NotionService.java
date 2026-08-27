package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import com.grove.perfumestalker.enums.NotionPerfumeMaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collections;
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

        if (req.getImageUrl() != null && !req.getImageUrl().isEmpty()) {
            properties.put(NotionPerfumeMaster.IMAGE.name(), Map.of(
                    "files", List.of(Map.of(
                            "name", req.getName() + "_image.jpg",
                            "type", "external",
                            "external", Map.of("url", req.getImageUrl())
                    ))
            ));
        }

        Map<String, List<String>> notes = req.getNotes();
        if (notes != null) {
            properties.put(NotionPerfumeMaster.TOP_NOTES.name(), buildMultiSelect(notes.get("top")));
            properties.put(NotionPerfumeMaster.MIDDLE_NOTES.name(), buildMultiSelect(notes.get("middle")));
            properties.put(NotionPerfumeMaster.BASE_NOTES.name(), buildMultiSelect(notes.get("base")));
            properties.put(NotionPerfumeMaster.NOTES.name(), buildMultiSelect(notes.get("general")));
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
     * 마스터 DB의 스키마를 조회해서 'BRAND' 컬럼에 등록된 옵션 목록을 가져옴
     */
    public Mono<List<String>> getBrandOptions() {
        // 스캔 로직과 똑같이 UUID 포맷팅 적용
        String formattedDataSourceId = notionModule.formatUuid(masterDataSourceId);

        // 조건 없이 전체 향수를 긁어오기 위한 빈 쿼리 조립
        Map<String, Object> queryBody = Map.of("page_size", 100);

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDataSourceId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null || results.isEmpty()) return List.<String>of();

                    String brandKey = NotionPerfumeMaster.BRAND.name();

                    return results.stream()
                            .map(page -> {
                                Map<String, Object> props = (Map<String, Object>) page.get("properties");
                                if (props == null) return null;

                                Map<String, Object> brandProp = (Map<String, Object>) props.get(brandKey);
                                if (brandProp == null) return null;

                                // 향수 데이터에서 브랜드명(select.name)만 쏙쏙 빼오기
                                if ("select".equals(brandProp.get("type")) && brandProp.get("select") != null) {
                                    return (String) ((Map<String, Object>) brandProp.get("select")).get("name");
                                }
                                return null;
                            })
                            .filter(brand -> brand != null && !brand.trim().isEmpty())
                            .distinct() // 중복 브랜드명 제거
                            .toList();
                })
                .doOnError(e -> log.error("❌ 노션 브랜드 데이터 조회 실패: ", e))
                .onErrorReturn(List.of());
    }

    /**
     * 크롤링 데이터를 노션 저장용 Properties 객체로 변환
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildPerfumeProperties(String uid, String brand, String name, Map<String, Object> crawledData) {
        Map<String, Object> properties = new java.util.HashMap<>();

        // 1. 기본 텍스트 속성 (예시)
        properties.put(NotionPerfumeMaster.UID.name(), Map.of("title", List.of(Map.of("text", Map.of("content", uid)))));
        properties.put(NotionPerfumeMaster.NAME.name(), Map.of("rich_text", List.of(Map.of("text", Map.of("content", name)))));
        properties.put(NotionPerfumeMaster.BRAND.name(), Map.of("select", Map.of("name", brand)));

        // 💡 2. IMAGE 처리: 서버에 다운받지 않고 'external' URL 통째로 꽂아 넣기
        String imageUrl = (String) crawledData.getOrDefault("imageUrl", "");
        if (!imageUrl.isEmpty()) {
            properties.put(NotionPerfumeMaster.IMAGE.name(), Map.of(
                    "files", List.of(
                            Map.of(
                                    "name", name + "_image.jpg",
                                    "type", "external",
                                    "external", Map.of("url", imageUrl)
                            )
                    )
            ));
        }

        // 💡 3. 입체적 노트 파싱 처리 (Top, Middle, Base, General)
        Map<String, List<String>> notesMap = (Map<String, List<String>>) crawledData.get("notes");
        if (notesMap != null) {
            properties.put(NotionPerfumeMaster.TOP_NOTES.name(), buildMultiSelect(notesMap.get("top")));
            properties.put(NotionPerfumeMaster.MIDDLE_NOTES.name(), buildMultiSelect(notesMap.get("middle")));
            properties.put(NotionPerfumeMaster.BASE_NOTES.name(), buildMultiSelect(notesMap.get("base")));
            properties.put(NotionPerfumeMaster.NOTES.name(), buildMultiSelect(notesMap.get("general"))); // 선형 향수 방어
        }

        return properties;
    }


    /**
     * List<String>을 노션의 multi_select 포맷으로 변환하는 헬퍼 메서드
     */
    private Map<String, Object> buildMultiSelect(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of("multi_select", List.of());
        }
        List<Map<String, String>> selectOptions = tags.stream()
                .map(tag -> Map.of("name", tag))
                .toList();
        return Map.of("multi_select", selectOptions);
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