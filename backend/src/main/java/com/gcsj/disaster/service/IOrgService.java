package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.vo.OrganizationVO;
import java.util.List;

public interface IOrgService {
    List<OrganizationVO> listAsTree();
}
