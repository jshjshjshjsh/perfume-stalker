package com.grove.perfumestalker.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PerfumeRegisterRequest {
    private String uid;
    private String name;
    private String brand;
    private Map<String, List<String>> notes; // 다중 선택
    private String url;         // 프레그런티카 링크
    private String imageUrl;    // 프레그런티카 이미지 링크
    private Double lat;
    private Double lon;
}