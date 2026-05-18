package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.PK> {
    List<UserRole> findByUserId(Long userId);
    List<UserRole> findByUserIdIn(Set<Long> userIds);
    void deleteByUserId(Long userId);
}
