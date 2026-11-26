package com.knowledge.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.kb.entity.KbMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbMemberMapper extends BaseMapper<KbMember> {
}