package com.grove.perfumestalker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualLogRequest {
    public String perfumeId;
    public String date; // YYYY-MM-DD
    public Double lat;
    public Double lon;
}
