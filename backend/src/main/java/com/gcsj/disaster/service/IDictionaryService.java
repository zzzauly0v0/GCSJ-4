package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateDictionaryDTO;
import com.gcsj.disaster.domain.vo.DictionaryVO;

import java.util.List;

public interface IDictionaryService {
    List<DictionaryVO> findByType(String type);

    List<DictionaryVO> listAll();

    List<String> listTypes();

    DictionaryVO add(CreateDictionaryDTO dto);

    DictionaryVO update(Long id, CreateDictionaryDTO dto);

    void deleteById(Long id);
}
