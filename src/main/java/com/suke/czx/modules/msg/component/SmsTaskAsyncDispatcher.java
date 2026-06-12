package com.suke.czx.modules.msg.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.suke.czx.common.utils.SpringContextUtils;
import com.suke.czx.modules.msg.component.entity.MessageBody;
import com.suke.czx.modules.msg.entity.XMessageService;
import com.suke.czx.modules.msg.entity.XMessageServiceSendRecord;
import com.suke.czx.modules.msg.entity.XMessageServiceTask;
import com.suke.czx.modules.msg.mapper.XMessageServiceMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceSendRecordMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceTaskMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 短信任务异步分发器
 * <p>
 * 使用 @Async 在虚拟线程上异步执行短信发送，
 * 复用现有的 SendMessage 策略模式和发送记录保存逻辑。
 *
 * @author czx
 * @email object_czx@163.com
 */
@Slf4j
@Component
@AllArgsConstructor
public class SmsTaskAsyncDispatcher {

    private final XMessageServiceMapper xMessageServiceMapper;
    private final XMessageServiceTaskMapper xMessageServiceTaskMapper;
    private final XMessageServiceSendRecordMapper xMessageServiceSendRecordMapper;

    /**
     * 异步批量发送短信任务
     */
    @Async
    public void dispatchAsync(List<XMessageServiceTask> tasks) {
        for (XMessageServiceTask task : tasks) {
            processTask(task);
        }
    }

    private void processTask(XMessageServiceTask task) {
        try {
            // 1. CAS: PENDING(0) → SENDING(1)；若已被取消(4)则 CAS 失败，直接跳过
            int casRows = xMessageServiceTaskMapper.update(null, Wrappers.<XMessageServiceTask>lambdaUpdate()
                    .eq(XMessageServiceTask::getTaskId, task.getTaskId())
                    .eq(XMessageServiceTask::getStatus, 0)    // 仅 PENDING 才能转 SENDING
                    .set(XMessageServiceTask::getStatus, 1)
                    .set(XMessageServiceTask::getUpdateTime, new Date()));
            if (casRows == 0) {
                log.info("[SmsTaskAsyncDispatcher] 任务已被取消或状态已变更，跳过发送: taskId={}", task.getTaskId());
                return;
            }

            // 2. 加载短信服务配置
            XMessageService service = xMessageServiceMapper.selectById(task.getServiceId());
            if (service == null) {
                throw new RuntimeException("短信服务配置不存在: serviceId=" + task.getServiceId());
            }
            String serviceClass = service.getServiceClass();
            if (StrUtil.isEmpty(serviceClass)) {
                throw new RuntimeException("短信服务实现类未配置");
            }

            // 3. 通过策略模式获取发送组件并发送
            SendMessage sendMessage = (SendMessage) SpringContextUtils.getBean(Class.forName(serviceClass));
            MessageBody messageBody = new MessageBody();
            BeanUtil.copyProperties(service, messageBody);
            messageBody.setMobile(task.getMobile());
            messageBody.setCode("");
            messageBody.setTemplate(task.getSendContent());

            boolean success = sendMessage.sendMessage(messageBody);

            // 4. 发送后重新读取任务状态，检查是否在发送期间被取消
            XMessageServiceTask current = xMessageServiceTaskMapper.selectById(task.getTaskId());
            if (current != null && current.getStatus() == 4) {
                // 任务在发送期间被取消，保留 CANCELLED 状态，不覆盖
                log.info("[SmsTaskAsyncDispatcher] 任务在发送期间被取消，保留取消状态: taskId={}", task.getTaskId());
                return;
            }

            // 5. 更新任务状态（只更新 status/failReason/updateTime，不覆盖 retryCount/nextRetryTime）
            xMessageServiceTaskMapper.update(null, Wrappers.<XMessageServiceTask>lambdaUpdate()
                    .eq(XMessageServiceTask::getTaskId, task.getTaskId())
                    .set(XMessageServiceTask::getStatus, success ? 2 : 3)
                    .set(XMessageServiceTask::getFailReason, success ? null : "短信发送失败")
                    .set(XMessageServiceTask::getUpdateTime, new Date()));

            // 6. 保存发送记录（复用现有逻辑）
            saveMessageRecord(task, service, success);

        } catch (Exception e) {
            log.error("[SmsTaskAsyncDispatcher] 任务发送异常: taskId={}, error={}", task.getTaskId(), e.getMessage(), e);
            xMessageServiceTaskMapper.update(null, Wrappers.<XMessageServiceTask>lambdaUpdate()
                    .eq(XMessageServiceTask::getTaskId, task.getTaskId())
                    .set(XMessageServiceTask::getStatus, 3)
                    .set(XMessageServiceTask::getFailReason, e.getMessage())
                    .set(XMessageServiceTask::getUpdateTime, new Date()));
        }
    }

    /**
     * 保存短信发送记录到分表
     */
    private void saveMessageRecord(XMessageServiceTask task, XMessageService service, boolean success) {
        try {
            XMessageServiceSendRecord record = new XMessageServiceSendRecord();
            record.setSendContent(task.getSendContent());
            record.setSendMobile(task.getMobile());
            record.setSendType(service.getDefaultTemplate() != null ? "任务中心" : "任务中心");
            Date date = new Date();
            record.setCreateTime(date);
            record.setRecordYear(DateUtil.year(date));
            record.setRecordMonth(DateUtil.month(date) + 1);
            record.setRecordDay(DateUtil.dayOfMonth(date));
            record.setSendStatus(success ? 1 : 0);
            record.setServiceId(task.getServiceId());
            record.setAppId(task.getAppId());
            // tenancyId 在 TenancyBase 中是 Integer 类型，需要做转换
            try {
                record.setTenancyId(Integer.valueOf(task.getTenancyId()));
            } catch (NumberFormatException ignored) {
            }
            xMessageServiceSendRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("[SmsTaskAsyncDispatcher] 保存发送记录失败: taskId={}, error={}", task.getTaskId(), e.getMessage());
        }
    }

}
