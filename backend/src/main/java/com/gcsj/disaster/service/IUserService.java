package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateUserDTO;
import com.gcsj.disaster.domain.dto.LoginDTO;
import com.gcsj.disaster.domain.dto.UpdateUserDTO;
import com.gcsj.disaster.domain.vo.LoginVO;
import com.gcsj.disaster.domain.vo.UserVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface IUserService {
    LoginVO login(LoginDTO dto);
    UserVO currentProfile();
    UserVO create(CreateUserDTO dto);
    UserVO update(Long id, UpdateUserDTO dto);
    void deleteById(Long id);
    UserVO getById(Long id);
    Page<UserVO> page(String keyword, Pageable pageable);
    Set<String> loadPermissionsByUserId(Long userId);
}
