package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private Integer success;
    private String ip;
    private String userAgent;
    private String detail;
    private LocalDateTime createdAt;
}
