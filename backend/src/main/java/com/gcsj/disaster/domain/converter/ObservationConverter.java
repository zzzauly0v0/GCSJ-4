package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.domain.entity.Observation;
import com.gcsj.disaster.domain.vo.ObservationVO;
import org.springframework.stereotype.Component;

@Component
public class ObservationConverter {

    public ObservationVO toVO(Observation o) {
        if (o == null) return null;
        ObservationVO vo = new ObservationVO();
        vo.setId(o.getId());
        vo.setSensorId(o.getSensorId());
        vo.setIndicator(o.getIndicator());
        vo.setValue(o.getValue());
        vo.setUnit(o.getUnit());
        vo.setObservedAt(o.getObservedAt());
        vo.setAbnormal(o.getAbnormal());
        return vo;
    }
}
