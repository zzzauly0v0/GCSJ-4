package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.domain.entity.Dictionary;
import com.gcsj.disaster.domain.vo.DictionaryVO;
import com.gcsj.disaster.repository.DictionaryRepository;
import com.gcsj.disaster.service.IDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements IDictionaryService {

    private final DictionaryRepository repository;

    @Override
    public List<DictionaryVO> findByType(String type) {
        return repository.findByTypeCodeOrderBySortAsc(type).stream().map(this::toVO).toList();
    }

    private DictionaryVO toVO(Dictionary d) {
        DictionaryVO vo = new DictionaryVO();
        vo.setId(d.getId());
        vo.setTypeCode(d.getTypeCode());
        vo.setItemCode(d.getItemCode());
        vo.setItemValue(d.getItemValue());
        vo.setSort(d.getSort());
        vo.setDescription(d.getDescription());
        return vo;
    }
}
