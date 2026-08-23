package com.grove.perfumestalker.user;

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
public class UserService {

    private final WebClient notionWebClient;

    @Value("${notion.db.user-data-source-id}")
    private String userDataSourceId;

    private String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id;
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    /**
     * 특정 유저의 DEFAULT_LOCATION을 조회 (없으면 기본값 "Busan" 반환)
     */
    public Mono<String> getDefaultLocation(String userId) {
        String formattedDataSourceId = formatUuid(userDataSourceId);

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "property", "USER_ID",
                        "title", Map.of("equals", userId)
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDataSourceId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    var results = (java.util.List<Map<String, Object>>) response.get("results");
                    if (results.isEmpty()) return "Busan"; // fallback

                    var properties = (Map<String, Object>) results.get(0).get("properties");
                    var locationProp = (Map<String, Object>) properties.get("DEFAULT_LOCATION");
                    var richText = (java.util.List<Map<String, Object>>) locationProp.get("rich_text");

                    if (richText == null || richText.isEmpty()) return "Busan";
                    return (String) ((Map<String, Object>) richText.get(0).get("text")).get("content");
                })
                .doOnError(e -> log.error("❌ 계정 DB 조회 에러", e))
                .onErrorReturn("Busan"); // 에러 나도 멈추지 않고 기본값 리턴
    }
}