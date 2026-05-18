package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.vo.PermissionVO;

import java.util.List;
import java.util.Set;

public interface IPermissionService {
    List<PermissionVO> listAllAsTree();
    Set<String> codesByUserId(Long userId);
}
