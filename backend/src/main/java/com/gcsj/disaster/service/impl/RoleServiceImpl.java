package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.dto.CreateRoleDTO;
import com.gcsj.disaster.domain.entity.Role;
import com.gcsj.disaster.domain.entity.RolePermission;
import com.gcsj.disaster.domain.vo.RoleVO;
import com.gcsj.disaster.repository.RolePermissionRepository;
import com.gcsj.disaster.repository.RoleRepository;
import com.gcsj.disaster.service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements IRoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public List<RoleVO> listAll() {
        return roleRepository.findAllByDeletedFalse().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public RoleVO create(CreateRoleDTO dto) {
        Role r = new Role();
        r.setName(dto.getName());
        r.setCode(dto.getCode());
        r.setDescription(dto.getDescription());
        roleRepository.save(r);
        if (dto.getPermissionIds() != null) {
            for (Long pid : dto.getPermissionIds()) {
                rolePermissionRepository.save(new RolePermission(r.getId(), pid));
            }
        }
        return toVO(r);
    }

    @Override
    @Transactional
    public RoleVO update(Long id, CreateRoleDTO dto) {
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        r.setName(dto.getName());
        r.setDescription(dto.getDescription());
        roleRepository.save(r);
        if (dto.getPermissionIds() != null) {
            rolePermissionRepository.deleteByRoleId(id);
            for (Long pid : dto.getPermissionIds()) {
                rolePermissionRepository.save(new RolePermission(id, pid));
            }
        }
        return toVO(r);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Role r = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        r.setDeleted(true);
        roleRepository.save(r);
    }

    private RoleVO toVO(Role r) {
        RoleVO vo = new RoleVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCode(r.getCode());
        vo.setDescription(r.getDescription());
        vo.setPermissionIds(rolePermissionRepository.findByRoleId(r.getId()).stream()
                .map(RolePermission::getPermissionId).toList());
        return vo;
    }
}
