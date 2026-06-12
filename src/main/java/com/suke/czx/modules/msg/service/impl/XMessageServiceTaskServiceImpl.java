package com.suke.czx.modules.msg.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.czx.common.exception.RRException;
import com.suke.czx.common.lock.RedisLock;
import com.suke.czx.common.utils.Constant;
import com.suke.czx.common.utils.SpringContextUtils;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.component.SendMessage;
import com.suke.czx.modules.msg.component.entity.MessageBody;
import com.suke.czx.modules.msg.entity.*;
import com.suke.czx.modules.msg.mapper.XMessageServiceMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceSendRecordMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceTaskMapper;
import com.suke.czx.modules.msg.service.XMessageServiceTaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 短信发送任务
 *
 * @author czx
 * @email object_czx@163.com
 */
@Slf4j
@Service
@AllArgsConstructor
public class XMessageServiceTaskServiceImpl extends ServiceImpl<XMessageServiceTaskMapper, XMessageServiceTask> implements XMessageServiceTaskService {

    public final XMessageServiceMapper xMessageServiceMapper;
    public final XMessageServiceSendRecordMapper xMessageServiceSendRecordMapper;
    public final RedisLock redisLock;
    public final RedisTemplate<String, Object> redisTemplate;

    private static final String TEL_REGEX = "^1" +
            "((3[0-9])|" +
            "(4[014-9])|" +
            "(5[0-35-9])|" +
            "(6[2567])|" +
            "(7[0-8])|" +
            "(8[0-9])|" +
            "(9[0-35-9]))" +
            "[0-9]{8}$";

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("#(\\w+)");

    private static final int RATE_LIMIT_MAX = 10;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    @Override
    public SmsTaskResponse createTask(SmsTaskRequest request, XApplication app) {
        // 参数校验
        if (request == null) {
            throw new RRException("请求参数不能为空");
        }
        if (StrUtil.isEmpty(request.getRequestNo())) {
            throw new RRException("业务请求号不能为空");
        }
        if (StrUtil.isEmpty(request.getMobile())) {
            throw new RRException("手机号不能为空");
        }
        if (!Pattern.matches(TEL_REGEX, request.getMobile())) {
            throw new RRException("手机号格式错误");
        }
        if (StrUtil.isEmpty(request.getTemplateCode())) {
            throw new RRException("模板编码不能为空");
        }

        Integer tenancyId = Integer.parseInt(app.getTenancyId());

        // 频控检查
        checkRateLimit(tenancyId);

        // 幂等检查
        String idempotentKey = Constant.SMS_IDEMPOTENT_KEY + app.getAppId() + ":" + request.getRequestNo();
        boolean lockSuccess = redisLock.lock(idempotentKey, idempotentKey, 24, TimeUnit.HOURS);
        if (!lockSuccess) {
            // 已存在，查询并返回
            XMessageServiceTask existingTask = getOne(Wrappers.<XMessageServiceTask>lambdaQuery()
                    .eq(XMessageServiceTask::getAppId, app.getAppId())
                    .eq(XMessageServiceTask::getRequestNo, request.getRequestNo())
                    .last("limit 1"));
            if (existingTask != null) {
                return buildTaskResponse(existingTask);
            }
        }

        // 构建任务
        XMessageServiceTask task = new XMessageServiceTask();
        task.setAppId(app.getAppId());
        task.setRequestNo(request.getRequestNo());
        task.setTemplateCode(request.getTemplateCode());
        task.setMobile(request.getMobile());
        if (request.getTemplateParams() != null && !request.getTemplateParams().isEmpty()) {
            task.setTemplateParams(JSONUtil.toJsonStr(request.getTemplateParams()));
        }
        task.setTaskStatus(XMessageServiceTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setCreateTime(new Date());
        task.setTenancyId(tenancyId);

        try {
            baseMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // DB唯一索引兜底
            XMessageServiceTask existingTask = getOne(Wrappers.<XMessageServiceTask>lambdaQuery()
                    .eq(XMessageServiceTask::getAppId, app.getAppId())
                    .eq(XMessageServiceTask::getRequestNo, request.getRequestNo())
                    .last("limit 1"));
            if (existingTask != null) {
                return buildTaskResponse(existingTask);
            }
        }

        // 异步执行发送（通过代理调用以确保@Async生效）
        SpringContextUtils.getBean(XMessageServiceTaskService.class).executeTaskAsync(task);

        return buildTaskResponse(task);
    }

    @Async
    @Override
    public void executeTaskAsync(XMessageServiceTask task) {
        try {
            // 更新状态为发送中
            updateTaskStatus(task, XMessageServiceTask.STATUS_SENDING, null);

            // 查询模板
            XMessageServiceTemplate template = findTemplate(task.getTemplateCode(), task.getTenancyId());
            if (template == null) {
                updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, "模板未配置或已禁用");
                return;
            }
            task.setTemplateId(template.getTemplateId());

            // 解析模板变量
            Map<String, String> params = parseTemplateParams(task.getTemplateParams());
            String templateContent = template.getTemplateContent();

            // 校验模板变量
            List<String> missingVars = findMissingVars(templateContent, params);
            if (!missingVars.isEmpty()) {
                updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, "模板变量缺失: " + String.join(", ", missingVars));
                return;
            }

            // 变量替换
            String sendContent = substituteTemplate(templateContent, params);
            task.setSendContent(sendContent);

            // 查询短信服务渠道
            XMessageService messageService = xMessageServiceMapper.selectOne(Wrappers
                    .<XMessageService>lambdaQuery()
                    .eq(XMessageService::getTenancyId, task.getTenancyId())
                    .eq(XMessageService::getIsEnable, 1)
                    .eq(XMessageService::getIsDefault, 1)
                    .last("limit 1"));
            if (messageService == null) {
                updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, "短信服务未配置或不可用");
                return;
            }
            task.setServiceId(messageService.getServiceId());

            // 发送（含重试）
            sendWithRetry(task, messageService, sendContent);

        } catch (Exception e) {
            log.error("短信任务执行异常, taskId={}, error={}", task.getTaskId(), e.getMessage(), e);
            updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, "系统异常: " + e.getMessage());
        }
    }

    @Override
    public SmsTaskResponse queryTask(String requestNo, XApplication app) {
        if (StrUtil.isEmpty(requestNo)) {
            throw new RRException("业务请求号不能为空");
        }
        XMessageServiceTask task = getOne(Wrappers.<XMessageServiceTask>lambdaQuery()
                .eq(XMessageServiceTask::getAppId, app.getAppId())
                .eq(XMessageServiceTask::getRequestNo, requestNo)
                .last("limit 1"));
        if (task == null) {
            throw new RRException("任务不存在");
        }
        return buildTaskResponse(task);
    }

    /**
     * 频控检查：同租户每分钟限制发送次数
     */
    private void checkRateLimit(Integer tenancyId) {
        String rateKey = Constant.SMS_RATE_LIMIT_KEY + tenancyId;
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            byte[] key = rateKey.getBytes(StandardCharsets.UTF_8);
            Long result = connection.incr(key);
            if (result != null && result == 1L) {
                connection.expire(key, RATE_LIMIT_WINDOW_SECONDS);
            }
            return result;
        });
        if (count != null && count > RATE_LIMIT_MAX) {
            throw new RRException("发送频率超限，每分钟最多" + RATE_LIMIT_MAX + "次");
        }
    }

    /**
     * 查找模板：按模板编码和租户查询已启用的模板
     */
    private XMessageServiceTemplate findTemplate(String templateCode, Integer tenancyId) {
        return SpringContextUtils.getBean(com.suke.czx.modules.msg.mapper.XMessageServiceTemplateMapper.class)
                .selectOne(Wrappers.<XMessageServiceTemplate>lambdaQuery()
                        .eq(XMessageServiceTemplate::getTemplateType, templateCode)
                        .eq(XMessageServiceTemplate::getTenancyId, tenancyId)
                        .eq(XMessageServiceTemplate::getIsEnable, 1)
                        .last("limit 1"));
    }

    /**
     * 解析模板变量JSON
     */
    private Map<String, String> parseTemplateParams(String templateParamsJson) {
        if (StrUtil.isEmpty(templateParamsJson)) {
            return Collections.emptyMap();
        }
        return JSONUtil.toBean(templateParamsJson, new cn.hutool.core.lang.TypeReference<Map<String, String>>() {}, false);
    }

    /**
     * 查找模板中缺失的变量
     */
    private List<String> findMissingVars(String templateContent, Map<String, String> params) {
        List<String> missing = new ArrayList<>();
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(templateContent);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!params.containsKey(varName)) {
                missing.add("#" + varName);
            }
        }
        return missing;
    }

    /**
     * 模板变量替换
     */
    private String substituteTemplate(String templateContent, Map<String, String> params) {
        String result = templateContent;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("#" + entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 发送并重试
     */
    private void sendWithRetry(XMessageServiceTask task, XMessageService messageService, String sendContent) {
        String lastError = null;
        for (int attempt = 0; attempt <= task.getMaxRetry(); attempt++) {
            try {
                String serviceClass = messageService.getServiceClass();
                if (StrUtil.isEmpty(serviceClass)) {
                    updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, "短信服务配置错误，缺少serviceClass");
                    return;
                }

                SendMessage sendMessage = (SendMessage) SpringContextUtils.getBean(Class.forName(serviceClass));
                MessageBody messageBody = new MessageBody();
                BeanUtil.copyProperties(messageService, messageBody);
                messageBody.setMobile(task.getMobile());
                messageBody.setTemplate(sendContent);

                boolean success = sendMessage.sendMessage(messageBody);
                if (success) {
                    // 成功
                    task.setSendContent(sendContent);
                    updateTaskStatus(task, XMessageServiceTask.STATUS_SUCCESS, null);
                    // 写入发送记录（兼容已有记录系统）
                    saveSendRecord(task, messageService, sendContent, true);
                    return;
                } else {
                    lastError = "短信服务返回发送失败";
                    task.setRetryCount(attempt + 1);
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                task.setRetryCount(attempt + 1);
                log.error("短信发送失败, taskId={}, attempt={}, error={}", task.getTaskId(), attempt, e.getMessage());
            }

            // 未达到最大重试次数则等待后重试
            if (attempt < task.getMaxRetry()) {
                try {
                    Thread.sleep((long) (attempt + 1) * 2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有重试耗尽
        task.setSendContent(sendContent);
        updateTaskStatus(task, XMessageServiceTask.STATUS_FAILED, lastError);
        saveSendRecord(task, messageService, sendContent, false);
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(XMessageServiceTask task, int status, String errorMessage) {
        task.setTaskStatus(status);
        task.setUpdateTime(new Date());
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }
        updateById(task);
    }

    /**
     * 保存发送记录（兼容已有月分表系统）
     */
    private void saveSendRecord(XMessageServiceTask task, XMessageService messageService, String content, boolean success) {
        try {
            XMessageServiceSendRecord record = new XMessageServiceSendRecord();
            record.setSendContent(content);
            record.setSendMobile(task.getMobile());
            record.setSendType(task.getTemplateCode());
            record.setSendSource("TASK:" + task.getRequestNo());
            record.setServiceId(messageService.getServiceId());
            record.setTemplateId(task.getTemplateId());
            record.setAppId(task.getAppId());
            Date date = new Date();
            record.setCreateTime(date);
            record.setRecordYear(DateUtil.year(date));
            record.setRecordMonth(DateUtil.month(date) + 1);
            record.setRecordDay(DateUtil.dayOfMonth(date));
            record.setSendStatus(success ? 1 : 0);
            record.setTenancyId(task.getTenancyId());
            xMessageServiceSendRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存发送记录失败, taskId={}, error={}", task.getTaskId(), e.getMessage());
        }
    }

    /**
     * 构建响应
     */
    private SmsTaskResponse buildTaskResponse(XMessageServiceTask task) {
        SmsTaskResponse response = new SmsTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setRequestNo(task.getRequestNo());
        response.setTaskStatus(task.getTaskStatus());
        response.setTaskStatusName(XMessageServiceTask.statusName(task.getTaskStatus()));
        response.setSendContent(task.getSendContent());
        response.setErrorMessage(task.getErrorMessage());
        response.setRetryCount(task.getRetryCount());
        response.setCreateTime(task.getCreateTime());
        response.setUpdateTime(task.getUpdateTime());
        return response;
    }
}
