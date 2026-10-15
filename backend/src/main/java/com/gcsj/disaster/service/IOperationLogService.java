package com.gcsj.disaster.service;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.domain.vo.OperationLogVO;

import java.time.OffsetDateTime;

public interface IOperationLogService {

    PageResult<OperationLogVO> page(String username,
                                    String method,
                                    String uri,
                                    OffsetDateTime from,
                                    OffsetDateTime to,
                                    int page,
                                    int size);
}
