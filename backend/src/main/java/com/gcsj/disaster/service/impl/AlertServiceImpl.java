package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.AlertConverter;
import com.gcsj.disaster.domain.dto.CreateAlertDTO;
import com.gcsj.disaster.domain.entity.Alert;
import com.gcsj.disaster.domain.entity.DisasterEvent;
import com.gcsj.disaster.domain.event.AlertTriggeredEvent;
import com.gcsj.disaster.domain.vo.AlertVO;
import com.gcsj.disaster.repository.AlertRepository;
import com.gcsj.disaster.repository.DisasterEventRepository;
import com.gcsj.disaster.service.IAlertService;
import com.gcsj.disaster.utils.GeometryUtil;
import com.gcsj.disaster.utils.SecurityContextUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.gcsj.disaster.common.AlertLevel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements IAlertService {

    private final AlertRepository alertRepository;
    private final DisasterEventRepository disasterEventRepository;
    private final AlertConverter alertConverter;
    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter EVAL_CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 风险研判 comp 等级列 -> 分项灾种列, 用于取主导灾种名 */
    private static final Map<String, String> EVAL_TYPE_NAME = Map.of(
            "landslide_level", "滑坡",
            "mudslide_level", "泥石流",
            "freezethaw_level", "冻融滑坡",
            "collapse_level", "坡面崩塌");

    @Override
    @Transactional
    public AlertVO create(CreateAlertDTO dto) {
        Alert a = new Alert();
        a.setCode(genCode());
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setLevel(dto.getLevel());
        a.setEventId(dto.getEventId());
        a.setSource(dto.getSource() != null ? dto.getSource() : "manual");
        if (dto.getLongitude() != null && dto.getLatitude() != null) {
            a.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        }
        List<String> channels = dto.getChannels() == null || dto.getChannels().isEmpty()
                ? List.of("in_site") : dto.getChannels();
        a.setChannels(String.join(",", channels));
        a.setStatus((short) 1);
        a.setTriggeredAt(OffsetDateTime.now());
        alertRepository.save(a);
        publisher.publishEvent(new AlertTriggeredEvent(this, a, channels));
        return alertConverter.toVO(a);
    }

    @Override
    @Transactional
    public AlertVO confirm(Long id) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        a.setStatus((short) 3);
        a.setConfirmedAt(OffsetDateTime.now());
        SecurityContextUtil.AuthUser u = SecurityContextUtil.currentOrNull();
        if (u != null) a.setConfirmedById(u.userId());
        alertRepository.save(a);
        return alertConverter.toVO(a);
    }

    @Override
    @Transactional
    public AlertVO close(Long id) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        a.setStatus((short) 4);
        alertRepository.save(a);
        return alertConverter.toVO(a);
    }

    @Override
    public AlertVO getById(Long id) {
        return alertConverter.toVO(alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND)));
    }

    @Override
    public Page<AlertVO> page(String keyword, Short level, Short status, Long eventId, Pageable pageable) {
        Specification<Alert> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (level != null) ps.add(cb.equal(root.get("level"), level));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (eventId != null) ps.add(cb.equal(root.get("eventId"), eventId));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(cb.like(root.get("title"), like), cb.like(root.get("code"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return alertRepository.findAll(spec, pageable).map(alertConverter::toVO);
    }

    @Override
    public List<AlertVO> latest(int limit) {
        return alertRepository.findTop100ByDeletedFalseOrderByTriggeredAtDesc().stream()
                .limit(limit).map(alertConverter::toVO).toList();
    }

    @Override
    public Map<String, Object> asGeoJson() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Alert a : alertRepository.findTop100ByDeletedFalseOrderByTriggeredAtDesc()) {
            if (a.getLocation() == null) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", a.getCode());
            props.put("title", a.getTitle());
            props.put("level", a.getLevel());
            props.put("status", a.getStatus());
            props.put("source", a.getSource());
            props.put("triggeredAt", a.getTriggeredAt());
            features.add(GeometryUtil.toFeature(a.getId(), a.getLocation(), props));
        }
        return GeometryUtil.toFeatureCollection(features);
    }

    @Override
    @Transactional
    public AlertVO createFromEvent(Long eventId, Short level, String title, String content, List<String> channels) {
        DisasterEvent e = disasterEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISASTER_EVENT_NOT_FOUND));
        Alert a = new Alert();
        a.setCode(genCode());
        a.setTitle(title != null && !title.isBlank() ? title : e.getTitle() + " 预警");
        a.setContent(content != null && !content.isBlank() ? content : e.getDescription());
        a.setLevel(level != null ? level : e.getLevel());
        a.setEventId(e.getId());
        a.setSource("event");
        a.setLocation(e.getLocation());
        List<String> ch = channels == null || channels.isEmpty() ? List.of("in_site") : channels;
        a.setChannels(String.join(",", ch));
        a.setStatus((short) 1);
        a.setTriggeredAt(OffsetDateTime.now());
        alertRepository.save(a);
        publisher.publishEvent(new AlertTriggeredEvent(this, a, ch));
        return alertConverter.toVO(a);
    }

    @Override
    @Transactional
    public Map<String, Object> generateFromEval(Integer year, int minLevel) {
        // 1. 扫描风险日 (comp_level>=minLevel), JOIN 站点取名称/经纬度
        StringBuilder sql = new StringBuilder("""
            SELECT e.station_code, s.name AS station_name, e.obs_date,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   e.comp_level, e.r_eff,
                   e.landslide_level, e.mudslide_level, e.freezethaw_level, e.collapse_level
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN gis.gis_weather_station s ON s.code = e.station_code
            WHERE  e.comp_level >= ?
        """);
        List<Object> args = new ArrayList<>();
        args.add(minLevel);
        if (year != null) {
            sql.append(" AND EXTRACT(YEAR FROM e.obs_date) = ? ");
            args.add(year);
        }
        sql.append(" ORDER BY e.obs_date, e.station_code");
        List<Map<String, Object>> risks = jdbc.queryForList(sql.toString(), args.toArray());

        int scanned = risks.size();
        if (scanned == 0) {
            return summary(0, 0, 0);
        }

        // 2. 为每条风险日构造确定性 code, 一次性查出已存在的 code 用于去重
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (Map<String, Object> r : risks) {
            String stationCode = (String) r.get("station_code");
            LocalDate d = ((Date) r.get("obs_date")).toLocalDate();
            String code = "E" + stationCode + d.format(EVAL_CODE_FMT);
            byCode.putIfAbsent(code, r);
        }
        // 分批 IN 查询已存在 code (避免占位符过多超出驱动上限)
        List<String> codeList = new ArrayList<>(byCode.keySet());
        Set<String> existing = new HashSet<>();
        final int CHUNK = 1000;
        for (int i = 0; i < codeList.size(); i += CHUNK) {
            List<String> sub = codeList.subList(i, Math.min(i + CHUNK, codeList.size()));
            String inClause = String.join(",", Collections.nCopies(sub.size(), "?"));
            existing.addAll(jdbc.queryForList(
                    "SELECT code FROM biz.biz_alert WHERE code IN (" + inClause + ")",
                    String.class, sub.toArray()));
        }

        // 3. 只落库缺失的
        int created = 0;
        for (Map.Entry<String, Map<String, Object>> en : byCode.entrySet()) {
            String code = en.getKey();
            if (existing.contains(code)) continue;
            Map<String, Object> r = en.getValue();

            String stationCode = (String) r.get("station_code");
            String stationName = (String) r.get("station_name");
            LocalDate d = ((Date) r.get("obs_date")).toLocalDate();
            short level = ((Number) r.get("comp_level")).shortValue();
            String levelLabel = AlertLevel.of(level).getLabel();
            String dominant = dominantType(r);
            String where = stationName != null && !stationName.isBlank() ? stationName : stationCode;

            Alert a = new Alert();
            a.setCode(code);
            a.setTitle(where + " " + d + " " + levelLabel + dominant + "风险");
            a.setContent(String.format("%s (%s) 于 %s 综合风险达%s级, 主导灾种: %s, R_eff=%s mm。",
                    where, stationCode, d, levelLabel, dominant, fmtNum(r.get("r_eff"))));
            a.setLevel(level);
            a.setSource("eval");
            Object lon = r.get("lon"), lat = r.get("lat");
            if (lon != null && lat != null) {
                a.setLocation(GeometryUtil.point(((Number) lon).doubleValue(), ((Number) lat).doubleValue()));
            }
            a.setChannels("in_site");
            a.setStatus((short) 1);
            // 触发时间取风险日当天 (UTC 零点), 与回放时间轴一致
            a.setTriggeredAt(d.atStartOfDay().atOffset(ZoneOffset.UTC));
            alertRepository.save(a);
            // 批量历史生成不发 AlertTriggeredEvent, 避免实时通知刷屏
            created++;
        }

        return summary(scanned, created, scanned - created);
    }

    /** 取分项等级最高的灾种作为主导灾种名 */
    private static String dominantType(Map<String, Object> r) {
        String best = null;
        int bestLv = 0;
        for (String col : EVAL_TYPE_NAME.keySet()) {
            Object v = r.get(col);
            int lv = v == null ? 0 : ((Number) v).intValue();
            if (lv > bestLv) { bestLv = lv; best = col; }
        }
        return best == null ? "综合" : EVAL_TYPE_NAME.get(best);
    }

    private static String fmtNum(Object o) {
        if (o == null) return "0";
        return String.valueOf(Math.round(((Number) o).doubleValue()));
    }

    private static Map<String, Object> summary(int scanned, int created, int skipped) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", scanned);
        out.put("created", created);
        out.put("skipped", skipped);
        return out;
    }

    private String genCode() {
        return "A" + OffsetDateTime.now().format(CODE_FMT)
                + String.format("%04d", new Random().nextInt(10000));
    }
}