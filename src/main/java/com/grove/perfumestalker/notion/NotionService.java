package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import com.grove.perfumestalker.enums.NotionPerfumeMaster;
import com.grove.perfumestalker.enums.NotionUsageLog;
import com.grove.perfumestalker.notion.util.NotionParserUtils;
import com.grove.perfumestalker.notion.util.NotionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionService {

    private final WebClient notionWebClient;
    private final NotionTokenUtils notionTokenUtils;

    @Value("${notion.db.master-id}")
    private String masterDbId;

    @Value("${notion.db.master-data-source-id}")
    private String masterDataSourceId;

    /**
     * 1. NFC UID로 마스터 DB 조회
     */
    public Mono<String> findPerfumePageIdByUid(String uid) {
        String formattedDataSourceId = notionTokenUtils.formatUuid(masterDataSourceId);
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
    public Mono<String> createPerfumeMaster(PerfumeRegisterRequest req, String userPageId) {

        if (Boolean.TRUE.equals(req.getSkipLog())) {
            return Mono.just(userPageId); // 로그 안 남기고 바로 종료
        } else {
            String formattedDbId = notionTokenUtils.formatUuid(masterDbId);
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

                properties.put(NotionPerfumeMaster.IMAGE_URL.name(), Map.of("url", req.getImageUrl()));
            }
            properties.put(NotionPerfumeMaster.USER.getColumnName(), NotionPerfumeMaster.USER.formatValue(userPageId));

            // 💡 팩폭: 날짜가 비어있으면 백엔드에서 KST 기준 오늘 날짜 강제 주입!
            String targetDate = (req.getDate() != null && !req.getDate().isBlank())
                    ? req.getDate()
                    : LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString();

            properties.put(NotionPerfumeMaster.DATE.getColumnName(), NotionPerfumeMaster.DATE.formatValue(targetDate));

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
    }

    // 💡 옷장 정보 수정
    public Mono<Void> updatePerfume(String pageId, PerfumeRegisterRequest request) {
        Map<String, Object> properties = new java.util.HashMap<>();
        if (request.getName() != null) properties.put(NotionPerfumeMaster.NAME.getColumnName(), NotionPerfumeMaster.NAME.formatValue(request.getName()));
        if (request.getBrand() != null) properties.put(NotionPerfumeMaster.BRAND.getColumnName(), NotionPerfumeMaster.BRAND.formatValue(request.getBrand()));
        if (request.getImageUrl() != null) properties.put(NotionPerfumeMaster.IMAGE_URL.getColumnName(), NotionPerfumeMaster.IMAGE_URL.formatValue(request.getImageUrl()));

        return notionWebClient.patch().uri("/pages/{pageId}", pageId)
                .bodyValue(Map.of("properties", properties)).retrieve().bodyToMono(Void.class);
    }

    /**
     * 마스터 DB의 스키마를 조회해서 'BRAND' 컬럼에 등록된 옵션 목록을 가져옴
     */
    public Mono<List<String>> getBrandOptions(String userPageId) {
        // 스캔 로직과 똑같이 UUID 포맷팅 적용
        String formattedDataSourceId = notionTokenUtils.formatUuid(masterDataSourceId);

        // 조건 없이 전체 향수를 긁어오기 위한 빈 쿼리 조립
        Map<String, Object> queryBody = Map.of(
                "page_size", 100,
                "filter", Map.of(
                        "property", NotionPerfumeMaster.USER.getColumnName(),
                        "relation", Map.of("contains", userPageId)
                )
        );

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

    public Mono<List<Map<String, Object>>> getAllPerfumes(String userPageId) {
        String formattedDbId = notionTokenUtils.formatUuid(masterDataSourceId);

        Map<String, Object> queryBody = Map.of(
                "page_size", 100,
                "filter", Map.of(
                        "and", List.of(
                                Map.of("property", "USER", "relation", Map.of("contains", userPageId)),
                                Map.of("property", NotionPerfumeMaster.DELETED.getColumnName(), "checkbox", Map.of("equals", false))
                        )
                ),
                "sorts", List.of(Map.of("property", NotionPerfumeMaster.ORDER_INDEX.getColumnName(), "direction", "ascending"))
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null) return List.of();

                    return results.stream().map(page -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = (Map<String, Object>) page.get("properties");

                        String id = (String) page.get("id");
                        String name = NotionParserUtils.extractRichText(props, "NAME");
                        String brand = NotionParserUtils.extractSelect(props, "BRAND");
                        String imageUrl = NotionParserUtils.extractUrl(props, "IMAGE_URL");

                        String date = NotionParserUtils.extractDate(props, NotionPerfumeMaster.DATE.getColumnName());

                        List<String> topNotes = NotionParserUtils.extractMultiSelect(props, "TOP_NOTES");
                        List<String> middleNotes = NotionParserUtils.extractMultiSelect(props, "MIDDLE_NOTES");
                        List<String> baseNotes = NotionParserUtils.extractMultiSelect(props, "BASE_NOTES");
                        List<String> generalNotes = NotionParserUtils.extractMultiSelect(props, "NOTES");

                        // 프론트엔드가 편하게 읽도록 Map으로 압축
                        Map<String, Object> notesMap = new java.util.HashMap<>();
                        if (!topNotes.isEmpty()) notesMap.put("top", topNotes);
                        if (!middleNotes.isEmpty()) notesMap.put("middle", middleNotes);
                        if (!baseNotes.isEmpty()) notesMap.put("base", baseNotes);
                        if (!generalNotes.isEmpty()) notesMap.put("general", generalNotes);

                        Map<String, Object> dto = new java.util.HashMap<>();
                        dto.put("id", id);
                        dto.put("name", name);
                        dto.put("brand", brand);
                        dto.put("notes", notesMap);
                        dto.put("imageUrl", imageUrl);
                        dto.put("date", date);
                        return dto;
                    }).collect(Collectors.toList());
                });
    }

    // 💡 소유자 상관없이 마스터 DB 전체에서 UID로 향수 찾기
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getPerfumeByUidGlobal(String uid) {
        String formattedDbId = notionTokenUtils.formatUuid(masterDataSourceId);

        // USER 필터 없이 오직 UID만으로 검색!
        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "UID", // 형님 노션의 태그 UID 컬럼명에 맞출 것!
                        "rich_text", Map.of("equals", uid)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null || results.isEmpty()) return Map.of(); // 빈 Map 리턴 시 '미등록 태그'
                    return results.get(0); // 발견 시 해당 향수 데이터 통째로 리턴
                });
    }

    public Mono<Void> reorderWardrobe(List<String> pageIds) {
        return reactor.core.publisher.Flux.range(0, pageIds.size())
                .concatMap(index -> {
                    String pageId = pageIds.get(index);
                    Map<String, Object> body = Map.of("properties", Map.of(NotionPerfumeMaster.ORDER_INDEX.getColumnName(), NotionPerfumeMaster.ORDER_INDEX.formatValue(index)));
                    return notionWebClient.patch().uri("/pages/{pageId}", pageId).bodyValue(body).retrieve().bodyToMono(Void.class);
                }).then();
    }

    public Mono<Void> softDeletePerfume(String pageId) {
        Map<String, Object> body = Map.of(
                "properties", Map.of(NotionPerfumeMaster.DELETED.getColumnName(), NotionPerfumeMaster.DELETED.formatValue(true))
        );
        return notionWebClient.patch()
                .uri("/pages/{pageId}", pageId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class);
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