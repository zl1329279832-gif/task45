package com.suke.czx.modules.msg.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.suke.czx.common.lock.RedissonLock;
import com.suke.czx.common.utils.Constant;
import com.suke.czx.modules.msg.component.SmsTaskAsyncDispatcher;
import com.suke.czx.modules.msg.entity.XMessageServiceTask;
import com.suke.czx.modules.msg.mapper.XMessageServiceTaskMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 短信任务重试调度器
 * <p>
 * 每 2 分钟扫描一次失败且未达到最大重试次数的任务，
 * 使用指数退避策略（1分钟/5分钟/30分钟）安排重试。
 * 通过分布式锁防止集群部署时重复执行。
 *
 * @author czx
 * @email object_czx@163.com
 */
@Slf4j
@Component
@AllArgsConstructor
public class SmsTaskRetryScheduler {

    private final XMessageServiceTaskMapper xMessageServiceTaskMapper;
    private final SmsTaskAsyncDispatcher dispatcher;
    private final RedissonLock redissonLock;

    @Scheduled(fixedDelay = 120_000)
    public void retryFailedTasks() {
        // 分布式锁，防止集群重复执行
        if (!redissonLock.lock(Constant.SMS_RETRY_LOCK, 60, TimeUnit.SECONDS)) {
            return;
        }
        try {
            Date now = new Date();
            // 查找失败且可重试的任务
            List<XMessageServiceTask> tasks = xMessageServiceTaskMapper.selectList(
                    Wrappers.<XMessageServiceTask>lambdaQuery()
                            .eq(XMessageServiceTask::getStatus, 3)  // FAILED
                            .apply("retry_count < max_retry")
                            .and(w -> w
                                    .isNull(XMessageServiceTask::getNextRetryTime)
                                    .or()
                                    .le(XMessageServiceTask::getNextRetryTime, now))
                            .last("limit 100"));

            if (tasks.isEmpty()) {
                return;
            }

            log.info("[SmsTaskRetryScheduler] 发现 {} 个待重试任务", tasks.size());

            for (XMessageServiceTask task : tasks) {
                int retryCount = task.getRetryCount() + 1;
                if (retryCount > task.getMaxRetry()) {
                    continue;
                }

                task.setRetryCount(retryCount);
                task.setStatus(1); // SENDING — 防止被重复捞取
                task.setUpdateTime(new Date());
                xMessageServiceTaskMapper.updateById(task);

                // 异步重新发送
                dispatcher.dispatchAsync(List.of(task));
            }

        } catch (Exception e) {
            log.error("[SmsTaskRetryScheduler] 重试调度异常: {}", e.getMessage(), e);
        } finally {
            redissonLock.unlock(Constant.SMS_RETRY_LOCK);
        }
    }

}
