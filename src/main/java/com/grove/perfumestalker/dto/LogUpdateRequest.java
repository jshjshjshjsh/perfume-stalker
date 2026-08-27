package com.grove.perfumestalker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogUpdateRequest {
    private String weather;
    private Double temp;
    private Double humidity;
}