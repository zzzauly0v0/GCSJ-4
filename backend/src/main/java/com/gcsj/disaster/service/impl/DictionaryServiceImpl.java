package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.dto.CreateDictionaryDTO;
import com.gcsj.disaster.domain.entity.Dictionary;
import com.gcsj.disaster.domain.vo.DictionaryVO;
import com.gcsj.disaster.repository.DictionaryRepository;
import com.gcsj.disaster.service.IDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements IDictionaryService {

    private final DictionaryRepository repository;

    @Override
    public List<DictionaryVO> findByType(String type) {
        return repository.findByTypeCodeOrderBySortAsc(type).stream().map(this::toVO).toList();
    }

    @Override
    public List<DictionaryVO> listAll() {
        return repository.findAllByOrderByTypeCodeAscSortAsc().stream().map(this::toVO).toList();
    }

    @Override
    public List<String> listTypes() {
        return repository.findDistinctTypeCodes();
    }

    @Override
    @Transactional
    public DictionaryVO add(CreateDictionaryDTO dto) {
        repository.findByTypeCodeAndItemCode(dto.getTypeCode(), dto.getItemCode())
                .ifPresent(d -> { throw new BusinessException(ErrorCode.DICTIONARY_DUPLICATE); });
        Dictionary d = new Dictionary();
        d.setTypeCode(dto.getTypeCode());
        d.setItemCode(dto.getItemCode());
        d.setItemValue(dto.getItemValue());
        d.setSort(dto.getSort() == null ? 0 : dto.getSort());
        d.setDescription(dto.getDescription());
        return toVO(repository.save(d));
    }

    @Override
    @Transactional
    public DictionaryVO update(Long id, CreateDictionaryDTO dto) {
        Dictionary d = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DICTIONARY_NOT_FOUND));
        boolean changedKey = !d.getTypeCode().equals(dto.getTypeCode())
                || !d.getItemCode().equals(dto.getItemCode());
        if (changedKey) {
            repository.findByTypeCodeAndItemCode(dto.getTypeCode(), dto.getItemCode())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new BusinessException(ErrorCode.DICTIONARY_DUPLICATE);
                        }
                    });
        }
        d.setTypeCode(dto.getTypeCode());
        d.setItemCode(dto.getItemCode());
        d.setItemValue(dto.getItemValue());
        d.setSort(dto.getSort() == null ? 0 : dto.getSort());
        d.setDescription(dto.getDescription());
        return toVO(repository.save(d));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorCode.DICTIONARY_NOT_FOUND);
        }
        repository.deleteById(id);
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
