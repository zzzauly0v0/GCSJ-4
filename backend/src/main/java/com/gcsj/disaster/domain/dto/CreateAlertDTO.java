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
    /** 关联灾害事件 id (可空, 手动发布时填) */
    private Long eventId;
    /** 来源: manual / event / external (默认 manual) */
    @Pattern(regexp = "manual|event|external")
    private String source;
    private Double longitude;
    private Double latitude;
    /** 推送通道: in_site / sms / email */
    private List<String> channels;
}
