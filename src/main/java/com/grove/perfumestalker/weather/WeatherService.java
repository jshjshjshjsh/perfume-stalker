package com.grove.perfumestalker.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class WeatherService {

    private final WebClient weatherWebClient;

    @Value("${openweathermap.api-key}")
    private String apiKey;

    public WeatherService() {
        this.weatherWebClient = WebClient.builder()
                .baseUrl("https://api.openweathermap.org/data/2.5")
                .build();
    }

    // 💡 최저/최고 기온이 추가된 응답 DTO
    public record WeatherData(String cityName, String weather, double temperature, double humidity, double tempMin, double tempMax) {}

    private record OpenWeatherResponse(List<Weather> weather, Main main, String name) {
        private record Weather(String main) {}
        private record Main(double temp, double humidity, @JsonProperty("temp_min") double tempMin, @JsonProperty("temp_max") double tempMax) {}
    }

    private record ForecastResponse(List<ForecastItem> list) {
        private record ForecastItem(long dt, OpenWeatherResponse.Main main) {} // dt(Unix Time) 파싱 추가
    }

    public Mono<WeatherData> getWeatherByCity(String city) {
        Mono<OpenWeatherResponse> currentMono = weatherWebClient.get()
                .uri(b -> b.path("/weather").queryParam("q", city).queryParam("appid", apiKey).queryParam("units", "metric").build())
                .retrieve().bodyToMono(OpenWeatherResponse.class);

        Mono<ForecastResponse> forecastMono = weatherWebClient.get()
                .uri(b -> b.path("/forecast").queryParam("q", city).queryParam("appid", apiKey).queryParam("units", "metric").build())
                .retrieve().bodyToMono(ForecastResponse.class);

        return Mono.zip(currentMono, forecastMono)
                .map(tuple -> mergeToDto(tuple.getT1(), tuple.getT2()))
                .doOnError(e -> log.error("❌ 날씨 API (City) 호출 실패: {}", city, e));
    }

    public Mono<WeatherData> getWeatherByCoordinates(double lat, double lon) {
        Mono<OpenWeatherResponse> currentMono = weatherWebClient.get()
                .uri(b -> b.path("/weather").queryParam("lat", lat).queryParam("lon", lon).queryParam("appid", apiKey).queryParam("units", "metric").build())
                .retrieve().bodyToMono(OpenWeatherResponse.class);

        Mono<ForecastResponse> forecastMono = weatherWebClient.get()
                .uri(b -> b.path("/forecast").queryParam("lat", lat).queryParam("lon", lon).queryParam("appid", apiKey).queryParam("units", "metric").build())
                .retrieve().bodyToMono(ForecastResponse.class);

        return Mono.zip(currentMono, forecastMono)
                .map(tuple -> mergeToDto(tuple.getT1(), tuple.getT2()))
                .doOnError(e -> log.error("❌ 날씨 API (GPS) 호출 실패", e));
    }

    // 💡 한국 시간(KST) 기준 '오늘'의 진짜 최저/최고 온도만 추출
    private WeatherData mergeToDto(OpenWeatherResponse current, ForecastResponse forecast) {
        String mainWeather = current.weather().isEmpty() ? "Unknown" : current.weather().get(0).main();

        LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<ForecastResponse.ForecastItem> todaysForecasts = forecast.list().stream()
                .filter(item -> Instant.ofEpochSecond(item.dt())
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toLocalDate().equals(todayKst))
                .toList();

        // 오늘 예보가 남아있으면 그 중 최저/최고를 구하고, 밤 11시라 예보가 없으면 현재 기온을 씀
        double trueMin = todaysForecasts.stream().mapToDouble(i -> i.main().tempMin()).min().orElse(current.main().tempMin());
        double trueMax = todaysForecasts.stream().mapToDouble(i -> i.main().tempMax()).max().orElse(current.main().tempMax());

        return new WeatherData(
                current.name(),
                mainWeather,
                current.main().temp(),
                current.main().humidity(),
                Math.round(trueMin * 10) / 10.0,
                Math.round(trueMax * 10) / 10.0
        );
    }
}