package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.vo.DictionaryVO;
import java.util.List;

public interface IDictionaryService {
    List<DictionaryVO> findByType(String type);
}
