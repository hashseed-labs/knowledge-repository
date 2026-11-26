package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_tenant")
public class SysUserTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tenantId;
    private Integer isDefault;
    private LocalDateTime createdAt;
}
