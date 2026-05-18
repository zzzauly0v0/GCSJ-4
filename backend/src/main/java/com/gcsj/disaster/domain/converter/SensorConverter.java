package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.domain.dto.CreateSensorDTO;
import com.gcsj.disaster.domain.entity.Sensor;
import com.gcsj.disaster.domain.vo.SensorVO;
import com.gcsj.disaster.utils.GeometryUtil;
import org.springframework.stereotype.Component;

@Component
public class SensorConverter {

    public SensorVO toVO(Sensor s) {
        if (s == null) return null;
        SensorVO vo = new SensorVO();
        vo.setId(s.getId());
        vo.setCode(s.getCode());
        vo.setName(s.getName());
        vo.setType(s.getType());
        vo.setOrgId(s.getOrgId());
        if (s.getLocation() != null) {
            vo.setLongitude(s.getLocation().getX());
            vo.setLatitude(s.getLocation().getY());
        }
        vo.setElevation(s.getElevation());
        vo.setUnit(s.getUnit());
        vo.setInstallAt(s.getInstallAt());
        vo.setStatus(s.getStatus());
        vo.setDescription(s.getDescription());
        return vo;
    }

    public Sensor fromCreate(CreateSensorDTO dto) {
        Sensor s = new Sensor();
        s.setCode(dto.getCode());
        s.setName(dto.getName());
        s.setType(dto.getType());
        s.setOrgId(dto.getOrgId());
        s.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        s.setElevation(dto.getElevation());
        s.setUnit(dto.getUnit());
        s.setDescription(dto.getDescription());
        s.setStatus((short) 1);
        return s;
    }
}
