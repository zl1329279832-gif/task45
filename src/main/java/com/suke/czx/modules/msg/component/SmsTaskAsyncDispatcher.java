package com.suke.czx.modules.msg.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
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
            // 1. 更新状态为"发送中"
            task.setStatus(1);
            task.setUpdateTime(new Date());
            xMessageServiceTaskMapper.updateById(task);

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

            // 4. 更新任务状态
            task.setStatus(success ? 2 : 3);
            task.setFailReason(success ? null : "短信发送失败");
            task.setUpdateTime(new Date());
            xMessageServiceTaskMapper.updateById(task);

            // 5. 保存发送记录（复用现有逻辑）
            saveMessageRecord(task, service, success);

        } catch (Exception e) {
            log.error("[SmsTaskAsyncDispatcher] 任务发送异常: taskId={}, error={}", task.getTaskId(), e.getMessage(), e);
            task.setStatus(3);
            task.setFailReason(e.getMessage());
            task.setUpdateTime(new Date());
            xMessageServiceTaskMapper.updateById(task);
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
