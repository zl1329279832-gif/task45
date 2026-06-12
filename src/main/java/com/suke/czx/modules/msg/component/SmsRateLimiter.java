package com.suke.czx.modules.msg.component;

import com.suke.czx.common.exception.RRException;
import com.suke.czx.common.utils.Constant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * 短信发送频率限制器（基于 Redis ZSET 滑动窗口）
 * <p>
 * 每个租户在 60 秒窗口内最多允许发送 100 条短信。
 * 采用 fail-open 策略：Redis 异常时放行请求（仅记录日志）。
 *
 * @author czx
 * @email object_czx@163.com
 */
@Slf4j
@Component
public class SmsRateLimiter {

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS = 100;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 检查租户短信发送频率是否超限
     *
     * @param tenancyId  租户ID
     * @param smsCount   本次请求的短信数量
     */
    public void checkRateLimit(String tenancyId, int smsCount) {
        String key = Constant.SMS_RATE_LIMIT + tenancyId;
        try {
            long now = Instant.now().toEpochMilli();
            long windowStart = now - WINDOW_SECONDS * 1000L;

            ZSetOperations<String, Object> ops = redisTemplate.opsForZSet();
            // 移除窗口外的记录
            ops.removeRangeByScore(key, 0, windowStart);

            // 查询当前窗口内的记录数
            Long currentCount = ops.zCard(key);
            if (currentCount != null && currentCount + smsCount > MAX_REQUESTS) {
                throw new RRException("发送频率超限，请稍后重试");
            }

            // 为本次请求的每条短信添加一条记录（用时间戳作为member保证唯一性）
            for (int i = 0; i < smsCount; i++) {
                String member = now + "_" + i + "_" + Thread.currentThread().getId();
                ops.add(key, member, now);
            }

            // 设置key过期时间，防止泄漏
            redisTemplate.expire(key, WINDOW_SECONDS * 2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (RRException e) {
            throw e;
        } catch (Exception e) {
            // fail-open: Redis 异常时放行
            log.warn("[SmsRateLimiter] Redis异常，放行请求: tenancyId={}, error={}", tenancyId, e.getMessage());
        }
    }

}
