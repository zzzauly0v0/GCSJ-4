package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findAllByDeletedFalseOrderBySortAsc();
}
