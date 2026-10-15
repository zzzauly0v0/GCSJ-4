package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class AlertVO {
    private Long id;
    private String code;
    private String title;
    private String content;
    private Short level;
    private String levelLabel;
    private String levelColor;
    private Long eventId;
    /** 关联事件 code (便于前端在表格里直接展示, 不必再请求一次详情) */
    private String eventCode;
    /** 来源: manual / event / external */
    private String source;
    private Double longitude;
    private Double latitude;
    /** 通道列表, 由 entity 的逗号串拆分 */
    private List<String> channels;
    private Short status;
    private OffsetDateTime triggeredAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime confirmedAt;
    private Long confirmedById;
}
