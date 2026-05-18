package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateAlertDTO {
    @NotBlank
    private String title;
    private String content;
    @NotNull
    @Min(1) @Max(4)
    private Short level;
    private Long ruleId;
    private Long sensorId;
    private Long eventId;
    private Double longitude;
    private Double latitude;
    /** 推送通道: in_site / sms / email */
    private List<String> channels;
}
