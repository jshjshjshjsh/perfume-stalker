package com.grove.perfumestalker.crawling;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class CrawlingService {

    private static final String SELECTOR_NOTE_LABEL = ".pyramid-note-label";
    private static final String SELECTOR_IMAGE = "img[itemprop='image']";
    private static final int TIMEOUT_MS = 25000;

    // 브라우저 엔진을 재사용하기 위한 전역 변수
    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void init() {
        log.info("🚀 Playwright 브라우저 엔진 초기화 시작...");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        log.info("✅ Playwright 브라우저 엔진 준비 완료!");
    }

    @PreDestroy
    public void destroy() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("🛑 Playwright 브라우저 엔진 종료 완료.");
    }

    /**
     * 프레그런티카 URL을 받아 이미지와 입체적 노트 정보를 크롤링하여 반환
     */
    public Mono<Map<String, Object>> crawl(String url) {
        return Mono.fromCallable(() -> {
                    log.info("🌐 백그라운드 스레드에서 새 탭(Context) 열고 크롤링 시작: {}", url);

                    try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 1080))) {

                        Page page = context.newPage();
                        page.setDefaultNavigationTimeout(TIMEOUT_MS);

                        try {
                            page.navigate(url);
                            // 💡 빠른 실패(Fail Fast): 15초 안에 못 찾으면 바로 던짐!
                            page.waitForSelector(SELECTOR_NOTE_LABEL, new Page.WaitForSelectorOptions().setTimeout(15000));
                        } catch (Exception e) {
                            log.warn("⚠️ [디버그] 방어막 걸림! 리액터에게 재시도를 요청합니다. URL: {}", url);
                            // 빈 맵을 리턴하는 대신 강제로 예외를 던져야 retryWhen이 발동함!
                            throw new RuntimeException("CRAWL_TIMEOUT_FOR_RETRY");
                        }

                        String imageUrl = extractImageUrl(page);
                        Map<String, Object> notesData = extractNotesData(page);

                        log.info("✅ [디버그] 크롤링 결과 - 이미지: [{}], 노트 추출 완료", !imageUrl.isEmpty() ? "성공" : "실패");

                        return Map.<String, Object>of(
                                "imageUrl", imageUrl,
                                "notes", notesData
                        );
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                // =====================================================================
                // 재시도(Retry) 오케스트레이션 구역
                // =====================================================================
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2)) // 2초 쉬고 최대 3번까지 재시도
                        .filter(throwable -> "CRAWL_TIMEOUT_FOR_RETRY".equals(throwable.getMessage())) // 이 예외일 때만 재시도
                        .doBeforeRetry(retrySignal ->
                                log.info("🔄 [디버그] 클라우드플레어 우회 재시도 ({}회차): {}", retrySignal.totalRetries() + 1, url)
                        )
                )
                .onErrorResume(e -> {
                    // 3번 다 실패했거나, 아예 다른 치명적 에러가 났을 때 최종적으로 방어막 발동
                    log.error("❌ [디버그] 최종 크롤링 실패 (재시도 3회 초과 또는 치명적 에러): {}", e.getMessage());
                    return Mono.just(Map.of("imageUrl", "", "notes", Map.of()));
                });
    }

    private String extractImageUrl(Page page) {
        try {
            String url = page.getAttribute(SELECTOR_IMAGE, "src");
            return url != null ? url : "";
        } catch (Exception e) {
            log.warn("⚠️ [디버그] 이미지 태그 추출 실패");
            return "";
        }
    }

    // 탑, 미들, 베이스, 일반(선형)을 분류해오는 지능형 스크립트
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractNotesData(Page page) {
        try {
            return (Map<String, Object>) page.evaluate("() => { " +
                    "let result = { top: [], middle: [], base: [], general: [] };" +
                    "let containers = document.querySelectorAll('.pyramid-level-container');" +

                    "if (containers.length === 0) {" +
                    "    let labels = document.querySelectorAll('.pyramid-note-label');" +
                    "    if (labels.length > 0) result.general = Array.from(labels).map(el => el.innerText.trim());" +
                    "    return result;" +
                    "}" +

                    "containers.forEach(container => {" +
                    "    let wrapper = container.closest('div[class*=\"mx-auto\"]') || container.parentElement.parentElement;" +
                    "    let h4 = wrapper ? wrapper.querySelector('h4') : null;" +
                    "    let headerText = h4 ? h4.innerText.toLowerCase() : '';" +
                    "    let notes = Array.from(container.querySelectorAll('.pyramid-note-label')).map(el => el.innerText.trim());" +

                    "    if (headerText.includes('top')) {" +
                    "        result.top = notes;" +
                    "    } else if (headerText.includes('middle') || headerText.includes('heart')) {" +
                    "        result.middle = notes;" +
                    "    } else if (headerText.includes('base')) {" +
                    "        result.base = notes;" +
                    "    } else {" +
                    "        result.general = result.general.concat(notes);" +
                    "    }" +
                    "});" +
                    "return result;" +
                    "}");
        } catch (Exception e) {
            log.warn("⚠️ [디버그] 노트 분류 추출 실패");
            return Map.of("top", List.of(), "middle", List.of(), "base", List.of(), "general", List.of());
        }
    }
}