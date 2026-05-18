package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateObservationDTO;
import com.gcsj.disaster.domain.vo.ObservationVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

public interface IObservationService {
    /** 写入并发出 ObservationReceivedEvent */
    ObservationVO ingest(CreateObservationDTO dto);
    /** 时间序列, 用于 echarts */
    List<ObservationVO> series(Long sensorId, String indicator, OffsetDateTime from, OffsetDateTime to);
    Page<ObservationVO> page(Long sensorId, Pageable pageable);
    List<ObservationVO> latest(int limit);
}
