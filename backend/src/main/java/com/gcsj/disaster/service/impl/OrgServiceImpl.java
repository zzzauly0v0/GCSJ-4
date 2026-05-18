package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.domain.entity.Organization;
import com.gcsj.disaster.domain.vo.OrganizationVO;
import com.gcsj.disaster.repository.OrganizationRepository;
import com.gcsj.disaster.service.IOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements IOrgService {

    private final OrganizationRepository repository;

    @Override
    public List<OrganizationVO> listAsTree() {
        List<Organization> all = repository.findAllByDeletedFalseOrderBySortAsc();
        Map<Long, OrganizationVO> map = new HashMap<>();
        List<OrganizationVO> roots = new ArrayList<>();
        all.forEach(o -> map.put(o.getId(), toVO(o)));
        for (Organization o : all) {
            OrganizationVO vo = map.get(o.getId());
            if (o.getParentId() == null) {
                roots.add(vo);
            } else {
                OrganizationVO p = map.get(o.getParentId());
                if (p != null) {
                    if (p.getChildren() == null) p.setChildren(new ArrayList<>());
                    p.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    private OrganizationVO toVO(Organization o) {
        OrganizationVO vo = new OrganizationVO();
        vo.setId(o.getId());
        vo.setName(o.getName());
        vo.setCode(o.getCode());
        vo.setParentId(o.getParentId());
        vo.setSort(o.getSort());
        return vo;
    }
}
