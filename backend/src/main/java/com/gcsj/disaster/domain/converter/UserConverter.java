package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.domain.entity.User;
import com.gcsj.disaster.domain.vo.UserVO;
import org.springframework.stereotype.Component;

@Component
public class UserConverter {

    public UserVO toVO(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setOrgId(user.getOrgId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
