package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.domain.entity.AdminRegion;
import com.gcsj.disaster.domain.entity.River;
import com.gcsj.disaster.domain.entity.Settlement;
import com.gcsj.disaster.repository.AdminRegionRepository;
import com.gcsj.disaster.repository.RiverRepository;
import com.gcsj.disaster.repository.SettlementRepository;
import com.gcsj.disaster.service.IGisDataService;
import com.gcsj.disaster.utils.GeometryUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GisDataServiceImpl implements IGisDataService {

    private final AdminRegionRepository regionRepository;
    private final RiverRepository riverRepository;
    private final SettlementRepository settlementRepository;

    @Override
    public Map<String, Object> regionsAsGeoJson(Short level, String parent, String adcode) {
        List<AdminRegion> regions;
        if (adcode != null && !adcode.isBlank()) {
            regions = regionRepository.findByAdcode(adcode).map(List::of).orElse(List.of());
        } else if (level != null && parent != null && !parent.isBlank()) {
            regions = regionRepository.findAllByLevelAndParentCodeOrderByAdcode(level, parent);
        } else if (level != null) {
            regions = regionRepository.findAllByLevelOrderByAdcode(level);
        } else {
            regions = regionRepository.findAll();
        }
        List<Map<String, Object>> features = regions.stream().map(r -> {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("adcode", r.getAdcode());
            props.put("name", r.getName());
            props.put("level", r.getLevel());
            props.put("parentCode", r.getParentCode());
            props.put("areaKm2", r.getAreaKm2());
            if (r.getCenter() != null) {
                props.put("center", List.of(r.getCenter().getX(), r.getCenter().getY()));
            }
            return GeometryUtil.toFeature(r.getId(), r.getBoundary(), props);
        }).toList();
        return GeometryUtil.toFeatureCollection(features);
    }

    @Override
    public Map<String, Object> riversAsGeoJson() {
        List<River> rivers = riverRepository.findAllByOrderByGradeAscIdAsc();
        List<Map<String, Object>> features = rivers.stream().map(r -> {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", r.getName());
            props.put("grade", r.getGrade());
            props.put("lengthKm", r.getLengthKm());
            return GeometryUtil.toFeature(r.getId(), r.getGeom(), props);
        }).toList();
        return GeometryUtil.toFeatureCollection(features);
    }

    @Override
    public Map<String, Object> settlementsAsGeoJson(String type) {
        List<Settlement> rows = (type == null || type.isBlank())
                ? settlementRepository.findAllByOrderByIdAsc()
                : settlementRepository.findAllByTypeOrderByIdAsc(type);
        List<Map<String, Object>> features = rows.stream().map(s -> {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", s.getName());
            props.put("type", s.getType());
            props.put("population", s.getPopulation());
            return GeometryUtil.toFeature(s.getId(), s.getGeom(), props);
        }).toList();
        return GeometryUtil.toFeatureCollection(features);
    }
}
