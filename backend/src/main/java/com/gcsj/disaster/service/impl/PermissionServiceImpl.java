package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.domain.entity.Permission;
import com.gcsj.disaster.domain.entity.UserRole;
import com.gcsj.disaster.domain.vo.PermissionVO;
import com.gcsj.disaster.repository.PermissionRepository;
import com.gcsj.disaster.repository.UserRoleRepository;
import com.gcsj.disaster.service.IPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements IPermissionService {

    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public List<PermissionVO> listAllAsTree() {
        List<Permission> all = permissionRepository.findAllByDeletedFalseOrderBySortAsc();
        Map<Long, PermissionVO> byId = new HashMap<>();
        List<PermissionVO> roots = new ArrayList<>();
        for (Permission p : all) {
            byId.put(p.getId(), toVO(p));
        }
        for (Permission p : all) {
            PermissionVO vo = byId.get(p.getId());
            if (p.getParentId() == null) {
                roots.add(vo);
            } else {
                PermissionVO parent = byId.get(p.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    @Override
    public Set<String> codesByUserId(Long userId) {
        Set<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) return Set.of();
        return permissionRepository.findByRoleIds(roleIds).stream()
                .map(Permission::getCode).collect(Collectors.toSet());
    }

    private PermissionVO toVO(Permission p) {
        PermissionVO vo = new PermissionVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setCode(p.getCode());
        vo.setType(p.getType());
        vo.setParentId(p.getParentId());
        vo.setPath(p.getPath());
        vo.setIcon(p.getIcon());
        vo.setSort(p.getSort());
        return vo;
    }
}
