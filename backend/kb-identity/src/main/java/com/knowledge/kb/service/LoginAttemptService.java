package com.knowledge.kb.service;

import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 登录失败次数需独立提交：外层 login 抛异常时不能回滚计数。
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final SysUserMapper userMapper;

    public boolean isLockedToday(SysUser user, int maxFailedPerDay) {
        LocalDate today = LocalDate.now();
        int count = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
        return today.equals(user.getFailedLoginDay()) && count >= maxFailedPerDay;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedLogin(Long userId, int maxFailedPerDay) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int count = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
        if (!today.equals(user.getFailedLoginDay())) {
            count = 0;
        }
        count += 1;
        user.setFailedLoginCount(count);
        user.setFailedLoginDay(today);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailedLogin(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if ((user.getFailedLoginCount() == null || user.getFailedLoginCount() == 0)
                && user.getFailedLoginDay() == null) {
            return;
        }
        user.setFailedLoginCount(0);
        user.setFailedLoginDay(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
