package com.grove.perfumestalker.dto;

import lombok.Data;
import java.util.List;

@Data
public class PerfumeRegisterRequest {
    private String uid;
    private String name;
    private String brand;
    private List<String> notes; // 다중 선택
    private String url;         // 프레그런티카 링크
    private String imageUrl;    // 프레그런티카 이미지 링크
}