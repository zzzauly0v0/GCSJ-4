package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.UserConverter;
import com.gcsj.disaster.domain.dto.CreateUserDTO;
import com.gcsj.disaster.domain.dto.LoginDTO;
import com.gcsj.disaster.domain.dto.UpdateUserDTO;
import com.gcsj.disaster.domain.entity.Permission;
import com.gcsj.disaster.domain.entity.Role;
import com.gcsj.disaster.domain.entity.User;
import com.gcsj.disaster.domain.entity.UserRole;
import com.gcsj.disaster.domain.vo.LoginVO;
import com.gcsj.disaster.domain.vo.UserVO;
import com.gcsj.disaster.repository.*;
import com.gcsj.disaster.service.IUserService;
import com.gcsj.disaster.utils.JwtUtil;
import com.gcsj.disaster.utils.SecurityContextUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserConverter userConverter;

    @Override
    @Transactional
    public LoginVO login(LoginDTO dto) {
        User u = userRepository.findByUsernameAndDeletedFalse(dto.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), u.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        u.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(u);
        String token = jwtUtil.issue(u.getId(), u.getUsername());
        UserVO vo = enrich(u);
        LoginVO out = new LoginVO();
        out.setToken(token);
        out.setUser(vo);
        return out;
    }

    @Override
    public UserVO currentProfile() {
        SecurityContextUtil.AuthUser cu = SecurityContextUtil.current();
        return getById(cu.userId());
    }

    @Override
    @Transactional
    public UserVO create(CreateUserDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        u.setStatus((short) 1);
        userRepository.save(u);
        if (dto.getRoleIds() != null) {
            for (Long rid : dto.getRoleIds()) {
                userRoleRepository.save(new UserRole(u.getId(), rid));
            }
        }
        return enrich(u);
    }

    @Override
    @Transactional
    public UserVO update(Long id, UpdateUserDTO dto) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (dto.getRealName() != null) u.setRealName(dto.getRealName());
        if (dto.getPhone() != null) u.setPhone(dto.getPhone());
        if (dto.getEmail() != null) u.setEmail(dto.getEmail());
        if (dto.getStatus() != null) u.setStatus(dto.getStatus());
        userRepository.save(u);
        if (dto.getRoleIds() != null) {
            userRoleRepository.deleteByUserId(id);
            for (Long rid : dto.getRoleIds()) {
                userRoleRepository.save(new UserRole(id, rid));
            }
        }
        return enrich(u);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        u.setDeleted(true);
        userRepository.save(u);
    }

    @Override
    public UserVO getById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return enrich(u);
    }

    @Override
    public Page<UserVO> page(String keyword, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(
                        cb.like(root.get("username"), like),
                        cb.like(root.get("realName"), like),
                        cb.like(root.get("phone"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(this::enrich);
    }

    @Override
    public Set<String> loadPermissionsByUserId(Long userId) {
        Set<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) return Set.of();
        return permissionRepository.findByRoleIds(roleIds).stream()
                .map(Permission::getCode).collect(Collectors.toSet());
    }

    private UserVO enrich(User u) {
        UserVO vo = userConverter.toVO(u);
        Set<Long> roleIds = userRoleRepository.findByUserId(u.getId()).stream()
                .map(UserRole::getRoleId).collect(Collectors.toSet());
        if (!roleIds.isEmpty()) {
            List<Role> roles = roleRepository.findAllById(roleIds);
            vo.setRoleCodes(roles.stream().map(Role::getCode).toList());
            vo.setPermissions(permissionRepository.findByRoleIds(roleIds).stream()
                    .map(Permission::getCode).distinct().toList());
        } else {
            vo.setRoleCodes(Collections.emptyList());
            vo.setPermissions(Collections.emptyList());
        }
        return vo;
    }
}
