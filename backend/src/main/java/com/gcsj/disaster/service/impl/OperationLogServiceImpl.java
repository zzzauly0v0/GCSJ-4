package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.domain.entity.OperationLog;
import com.gcsj.disaster.domain.vo.OperationLogVO;
import com.gcsj.disaster.repository.OperationLogRepository;
import com.gcsj.disaster.service.IOperationLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements IOperationLogService {

    private final OperationLogRepository repository;

    @Override
    public PageResult<OperationLogVO> page(String username,
                                           String method,
                                           String uri,
                                           OffsetDateTime from,
                                           OffsetDateTime to,
                                           int page,
                                           int size) {
        Specification<OperationLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                ps.add(cb.like(root.get("username"), "%" + username + "%"));
            }
            if (method != null && !method.isBlank()) {
                ps.add(cb.equal(root.get("method"), method.toUpperCase()));
            }
            if (uri != null && !uri.isBlank()) {
                ps.add(cb.like(root.get("uri"), "%" + uri + "%"));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        int p = Math.max(1, page);
        int s = size <= 0 ? 20 : Math.min(size, 200);
        PageRequest pr = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OperationLog> result = repository.findAll(spec, pr);
        return PageResult.from(result, this::toVO);
    }

    private OperationLogVO toVO(OperationLog e) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setUsername(e.getUsername());
        vo.setModule(e.getModule());
        vo.setAction(e.getAction());
        vo.setMethod(e.getMethod());
        vo.setUri(e.getUri());
        vo.setIp(e.getIp());
        vo.setResultCode(e.getResultCode());
        vo.setCostMs(e.getCostMs());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
