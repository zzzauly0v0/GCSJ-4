package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByDeletedFalseOrderBySortAsc();

    @Query("""
            select p from Permission p
            where p.deleted = false and p.id in (
                select rp.permissionId from RolePermission rp where rp.roleId in :roleIds
            )""")
    List<Permission> findByRoleIds(Set<Long> roleIds);
}
