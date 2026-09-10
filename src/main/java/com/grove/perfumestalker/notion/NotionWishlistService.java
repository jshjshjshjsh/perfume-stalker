package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.WishlistRequest;
import com.grove.perfumestalker.dto.WishlistResponse;
import com.grove.perfumestalker.enums.NotionWishlist;
import com.grove.perfumestalker.notion.util.NotionParserUtils;
import com.grove.perfumestalker.notion.util.NotionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWishlistService {

    private final WebClient notionWebClient;
    private final NotionTokenUtils notionTokenUtils;

    @Value("${notion.wishlist-db-id}")
    private String wishlistDbId;
    @Value("${notion.wishlist-data-source-id}")
    private String wishlistSourceDbId;

    // 💡 위시리스트 정보 수정
    public Mono<Void> updateWishlist(String pageId, WishlistRequest request) {
        Map<String, Object> properties = new java.util.HashMap<>();
        if (request.name() != null) properties.put(NotionWishlist.NAME.getColumnName(), NotionWishlist.NAME.formatValue(request.name()));
        if (request.brand() != null) properties.put(NotionWishlist.BRAND.getColumnName(), NotionWishlist.BRAND.formatValue(request.brand()));
        if (request.imageUrl() != null) properties.put(NotionWishlist.IMAGE_URL.getColumnName(), NotionWishlist.IMAGE_URL.formatValue(request.imageUrl()));

        return notionWebClient.patch().uri("/pages/{pageId}", pageId)
                .bodyValue(Map.of("properties", properties))
                .retrieve()
                .bodyToMono(Void.class);
    }

    // 💡 1. 내 위시리스트 조회
    public Mono<List<WishlistResponse>> getWishlist(String userPageId) {
        String formattedDbId = notionTokenUtils.formatUuid(wishlistSourceDbId);

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of("property", NotionWishlist.USER.getColumnName(), "relation", Map.of("contains", userPageId)),
                "sorts", List.of(Map.of("property", NotionWishlist.ORDER_INDEX.getColumnName(), "direction", "descending"))
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

                        Map<String, List<String>> notes = new java.util.HashMap<>();
                        notes.put("top", NotionParserUtils.extractMultiSelect(props, NotionWishlist.TOP_NOTES.getColumnName()));
                        notes.put("middle", NotionParserUtils.extractMultiSelect(props, NotionWishlist.MIDDLE_NOTES.getColumnName()));
                        notes.put("base", NotionParserUtils.extractMultiSelect(props, NotionWishlist.BASE_NOTES.getColumnName()));
                        notes.put("general", NotionParserUtils.extractMultiSelect(props, NotionWishlist.NOTES.getColumnName()));
                        List<String> seasons = NotionParserUtils.extractMultiSelect(props, NotionWishlist.SEASONS.getColumnName());

                        String seasonStatsStr = NotionParserUtils.extractRichText(props, NotionWishlist.SEASON_STATS.getColumnName());
                        Map<String, Integer> seasonStatsMap = Map.of();
                        if (seasonStatsStr != null && !seasonStatsStr.isEmpty()) {
                            try {
                                seasonStatsMap = new ObjectMapper().readValue(seasonStatsStr, Map.class);
                            } catch(Exception e) {}
                        }

                        return new WishlistResponse(
                                (String) page.get("id"),
                                NotionParserUtils.extractPerfumeName(props, NotionWishlist.NAME.getColumnName()), // 형님 룰셋 적용
                                NotionParserUtils.extractSelect(props, NotionWishlist.BRAND.getColumnName()),
                                NotionParserUtils.extractUrl(props, NotionWishlist.IMAGE_URL.getColumnName()),
                                NotionParserUtils.extractUrl(props, NotionWishlist.URL.getColumnName()),
                                NotionParserUtils.extractDate(props, NotionWishlist.DATE.getColumnName()),
                                notes,
                                seasons,
                                seasonStatsMap
                        );
                    }).collect(java.util.stream.Collectors.toList());
                });
    }

    // 💡 2. 위시리스트 추가 로직
    public Mono<String> addWishlist(WishlistRequest request, String userPageId) {
        Map<String, Object> properties = new java.util.HashMap<>();
        properties.put(NotionWishlist.NAME.getColumnName(), NotionWishlist.NAME.formatValue(request.name()));

        if (request.brand() != null && !request.brand().isBlank())
            properties.put(NotionWishlist.BRAND.getColumnName(), NotionWishlist.BRAND.formatValue(request.brand()));
        if (request.imageUrl() != null && !request.imageUrl().isBlank())
            properties.put(NotionWishlist.IMAGE_URL.getColumnName(), NotionWishlist.IMAGE_URL.formatValue(request.imageUrl()));
        if (request.url() != null && !request.url().isBlank())
            properties.put(NotionWishlist.URL.getColumnName(), NotionWishlist.URL.formatValue(request.url()));
        if (request.date() != null && !request.date().isBlank())
            properties.put(NotionWishlist.DATE.getColumnName(), NotionWishlist.DATE.formatValue(request.date()));
        if (request.seasons() != null && !request.seasons().isEmpty())
            properties.put(NotionWishlist.SEASONS.getColumnName(), NotionWishlist.SEASONS.formatValue(request.seasons()));
        if (request.seasonStats() != null && !request.seasonStats().isEmpty()) {
            try {
                String statsJson = new ObjectMapper().writeValueAsString(request.seasonStats());
                properties.put(NotionWishlist.SEASON_STATS.getColumnName(), NotionWishlist.SEASON_STATS.formatValue(statsJson));
            } catch(Exception e) {}
        }

        properties.put(NotionWishlist.USER.getColumnName(), NotionWishlist.USER.formatValue(userPageId));
        properties.put(NotionWishlist.ORDER_INDEX.getColumnName(), NotionWishlist.ORDER_INDEX.formatValue(System.currentTimeMillis()));

        if (request.notes() != null) {
            if (request.notes().containsKey("top")) properties.put(NotionWishlist.TOP_NOTES.getColumnName(), NotionWishlist.TOP_NOTES.formatValue(request.notes().get("top")));
            if (request.notes().containsKey("middle")) properties.put(NotionWishlist.MIDDLE_NOTES.getColumnName(), NotionWishlist.MIDDLE_NOTES.formatValue(request.notes().get("middle")));
            if (request.notes().containsKey("base")) properties.put(NotionWishlist.BASE_NOTES.getColumnName(), NotionWishlist.BASE_NOTES.formatValue(request.notes().get("base")));
            if (request.notes().containsKey("general")) properties.put(NotionWishlist.NOTES.getColumnName(), NotionWishlist.NOTES.formatValue(request.notes().get("general")));
        }

        Map<String, Object> body = Map.of(
                "parent", Map.of("database_id", notionTokenUtils.formatUuid(wishlistDbId)),
                "properties", properties
        );

        return notionWebClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("id"));
    }

    // 💡 3. 위시리스트 삭제 (노션 API 특성상 'archived: true'로 변경하여 삭제 처리)
    public Mono<Void> deleteWishlist(String pageId) {
        return notionWebClient.patch()
                .uri("/pages/{pageId}", pageId)
                .bodyValue(Map.of("in_trash", true))
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<Void> reorderWishlist(List<String> pageIds) {
        long baseTime = System.currentTimeMillis();
        return reactor.core.publisher.Flux.range(0, pageIds.size())
                .concatMap(index -> {
                    String pageId = pageIds.get(index);
                    Map<String, Object> body = Map.of(
                            "properties", Map.of(
                                    NotionWishlist.ORDER_INDEX.getColumnName(),
                                    NotionWishlist.ORDER_INDEX.formatValue(baseTime - index)
                            )
                    );
                    return notionWebClient.patch()
                            .uri("/pages/{pageId}", pageId)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Void.class);
                })
                .then();
    }
}