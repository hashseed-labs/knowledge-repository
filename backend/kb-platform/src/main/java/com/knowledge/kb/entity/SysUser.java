package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String status;
    /** 当日连续密码错误次数；跨日后自动从 0 重新计 */
    private Integer failedLoginCount;
    /** 失败计数所属自然日（服务器本地时区） */
    private LocalDate failedLoginDay;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
