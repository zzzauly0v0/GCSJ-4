package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateRoleDTO;
import com.gcsj.disaster.domain.vo.RoleVO;
import java.util.List;

public interface IRoleService {
    List<RoleVO> listAll();
    RoleVO create(CreateRoleDTO dto);
    RoleVO update(Long id, CreateRoleDTO dto);
    void deleteById(Long id);
}
