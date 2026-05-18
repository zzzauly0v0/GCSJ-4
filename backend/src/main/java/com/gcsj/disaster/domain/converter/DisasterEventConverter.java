package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.common.AlertLevel;
import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.dto.CreateDisasterEventDTO;
import com.gcsj.disaster.domain.entity.DisasterEvent;
import com.gcsj.disaster.domain.vo.DisasterEventVO;
import com.gcsj.disaster.utils.GeometryUtil;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
public class DisasterEventConverter {

    public DisasterEventVO toVO(DisasterEvent d) {
        if (d == null) return null;
        DisasterEventVO vo = new DisasterEventVO();
        vo.setId(d.getId());
        vo.setCode(d.getCode());
        vo.setTitle(d.getTitle());
        vo.setType(d.getType());
        vo.setLevel(d.getLevel());
        if (d.getLevel() != null) {
            AlertLevel lv = AlertLevel.of(d.getLevel());
            vo.setLevelLabel(lv.getLabel());
            vo.setLevelColor(lv.getColor());
        }
        if (d.getLocation() != null) {
            vo.setLongitude(d.getLocation().getX());
            vo.setLatitude(d.getLocation().getY());
        }
        if (d.getAffectedArea() != null) {
            vo.setAffectedAreaGeoJson(GeometryUtil.toGeoJson(d.getAffectedArea()));
        }
        vo.setOccurredAt(d.getOccurredAt());
        vo.setEndAt(d.getEndAt());
        vo.setStatus(d.getStatus());
        vo.setDescription(d.getDescription());
        vo.setReporterId(d.getReporterId());
        vo.setCreatedAt(d.getCreatedAt());
        return vo;
    }

    public DisasterEvent fromCreate(CreateDisasterEventDTO dto) {
        DisasterEvent d = new DisasterEvent();
        d.setTitle(dto.getTitle());
        d.setType(dto.getType());
        d.setLevel(dto.getLevel());
        d.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        if (dto.getAffectedAreaGeoJson() != null && !dto.getAffectedAreaGeoJson().isBlank()) {
            Geometry g = GeometryUtil.fromGeoJson(dto.getAffectedAreaGeoJson());
            if (!(g instanceof Polygon p)) {
                throw new BusinessException(ErrorCode.GEOMETRY_INVALID, "affectedArea 必须是 Polygon");
            }
            d.setAffectedArea(p);
        }
        d.setOccurredAt(dto.getOccurredAt());
        d.setStatus((short) 1);
        d.setDescription(dto.getDescription());
        return d;
    }
}
