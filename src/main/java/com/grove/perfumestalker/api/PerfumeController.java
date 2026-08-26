package com.grove.perfumestalker.api;

import com.grove.perfumestalker.notion.NotionLogService;
import com.grove.perfumestalker.notion.NotionService;
import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import com.grove.perfumestalker.user.UserService;
import com.grove.perfumestalker.weather.WeatherService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/perfumes")
@RequiredArgsConstructor
public class PerfumeController {

    private final NotionService notionService;
    private final NotionLogService notionLogService;
    private final WeatherService weatherService;
    private final UserService userService;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> registerNewPerfume(@RequestBody PerfumeRegisterRequest request) {
        log.info("📱 [Perfume Stalker] 새 향수 등록 요청: {}", request.getName());

        // 1. 날씨 조회 로직 (GPS 우선, 없으면 계정 기본 위치)
        Mono<WeatherService.WeatherData> weatherMono;
        if (request.getLat() != null && request.getLon() != null) {
            weatherMono = weatherService.getWeatherByCoordinates(request.getLat(), request.getLon());
        } else {
            // 형님의 계정 DB USER_ID
            weatherMono = userService.getDefaultLocation("jsh-admin")
                    .flatMap(weatherService::getWeatherByCity);
        }

        // 2. 향수 마스터 DB Insert
        Mono<String> perfumeMasterMono = notionService.createPerfumeMaster(request);

        // 3. 두 비동기 작업을 묶어서 착향 로그 기록으로
        return Mono.zip(perfumeMasterMono, weatherMono)
                .flatMap(tuple -> {
                    String newPerfumePageId = tuple.getT1();
                    WeatherService.WeatherData weather = tuple.getT2();

                    return notionLogService.createUsageLog(newPerfumePageId, weather);
                })
                .map(v -> ResponseEntity.ok("✅ 향수 등록 및 날씨 정보가 포함된 착향 로그 기록 완료!"))
                .onErrorResume(e -> {
                    log.error("❌ 향수 등록 파이프라인 에러: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body("실패: " + e.getMessage()));
                });
    }

    /**
     * 1. 등록된 브랜드 목록 가져오기 (노션 DB 스키마 조회)
     */
    @GetMapping("/brands")
    public Mono<ResponseEntity<List<String>>> getBrands() {
        return notionService.getBrandOptions()
                .map(ResponseEntity::ok);
    }

    /**
     * 2. 프레그런티카 크롤링 API
     */
    @GetMapping("/crawl")
    public ResponseEntity<java.util.Map<String, String>> crawlFragrantica(@RequestParam String url) {
        log.info("🌐 Playwright 크롤링 시도: {}", url);

        // try-with-resources로 브라우저 메모리 누수 완벽 차단
        try (Playwright playwright = Playwright.create()) {

            // 크로미움 브라우저를 화면 없이(Headless) 실행
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            // URL 접속 후 DOM이 다 그려질 때까지 대기 (봇 체크 우회)
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            String imageUrl = "";
            String notes = "";

            try {
                // 향수 썸네일 이미지 추출
                imageUrl = page.getAttribute("div.cell.small-12 img[itemprop=image]", "src");
            } catch (Exception e) {
                log.warn("⚠️ 이미지 추출 실패");
            }

            try {
                // Playwright의 evaluate를 써서 브라우저 내부 자바스크립트로 노트를 긁어옴!
                notes = (String) page.evaluate("() => { " +
                        "return Array.from(document.querySelectorAll('div#pyramid div.cell div b'))" +
                        ".map(el => el.innerText).join(', '); " +
                        "}");
            } catch (Exception e) {
                log.warn("⚠️ 노트 추출 실패");
            }

            return ResponseEntity.ok(java.util.Map.of(
                    "imageUrl", imageUrl != null ? imageUrl : "",
                    "notes", notes != null ? notes : ""
            ));

        } catch (Exception e) {
            log.error("❌ Playwright 크롤링 에러 (클라우드플레어 차단 또는 타임아웃): ", e);
            // 실패해도 프론트엔드가 수동 입력할 수 있도록 빈 값 리턴
            return ResponseEntity.ok(java.util.Map.of("imageUrl", "", "notes", ""));
        }
    }
}