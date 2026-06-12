package com.suke.czx.modules.msg.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.czx.common.exception.RRException;
import com.suke.czx.common.lock.RedissonLock;
import com.suke.czx.common.utils.Constant;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.component.SmsRateLimiter;
import com.suke.czx.modules.msg.component.SmsTaskAsyncDispatcher;
import com.suke.czx.modules.msg.component.SmsTemplateResolver;
import com.suke.czx.modules.msg.entity.*;
import com.suke.czx.modules.msg.mapper.XMessageServiceMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceTaskMapper;
import com.suke.czx.modules.msg.mapper.XMessageServiceTemplateMapper;
import com.suke.czx.modules.msg.service.XMessageServiceTaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 短信发送任务服务实现
 *
 * @author czx
 * @email object_czx@163.com
 */
@Slf4j
@Service
@AllArgsConstructor
public class XMessageServiceTaskServiceImpl
        extends ServiceImpl<XMessageServiceTaskMapper, XMessageServiceTask>
        implements XMessageServiceTaskService {

    private final XMessageServiceTemplateMapper xMessageServiceTemplateMapper;
    private final XMessageServiceMapper xMessageServiceMapper;
    private final RedissonLock redissonLock;
    private final SmsTaskAsyncDispatcher dispatcher;
    private final SmsRateLimiter rateLimiter;

    private static final String MOBILE_REGEX = "^1" +
            "((3[0-9])|" +
            "(4[014-9])|" +
            "(5[0-35-9])|" +
            "(6[2567])|" +
            "(7[0-8])|" +
            "(8[0-9])|" +
            "(9[0-35-9]))" +
            "[0-9]{8}$";

    @Override
    public List<SmsTaskVO> createTask(SmsTaskCreateRequest req, XApplication app) {
        String tenancyId = app.getTenancyId();
        if (tenancyId == null || tenancyId.isEmpty()) {
            throw new RRException("应用未绑定租户");
        }

        // 1. Redis 分布式锁实现请求号幂等
        String lockKey = Constant.SMS_TASK_LOCK + req.getRequestNo();
        if (!redissonLock.lock(lockKey, 10, TimeUnit.SECONDS)) {
            throw new RRException("请求处理中，请勿重复提交");
        }
        try {
            // 2. 幂等检查：查询是否已有该请求号的任务
            List<XMessageServiceTask> existing = list(Wrappers.<XMessageServiceTask>lambdaQuery()
                    .eq(XMessageServiceTask::getRequestNo, req.getRequestNo())
                    .eq(XMessageServiceTask::getTenancyId, tenancyId));
            if (!existing.isEmpty()) {
                return convertToVO(existing);
            }

            // 3. 通过模板编码 + 租户ID 查找模板
            XMessageServiceTemplate template = xMessageServiceTemplateMapper.queryTemplateByCode(
                    req.getTemplateCode(), tenancyId);
            if (template == null) {
                throw new RRException("模板不存在或已禁用");
            }

            // 4. 校验模板变量并替换
            String resolvedContent = SmsTemplateResolver.resolve(
                    template.getTemplateContent(), req.getTemplateParams());

            // 5. 查找租户下默认启用的短信服务
            XMessageService service = xMessageServiceMapper.selectOne(Wrappers
                    .<XMessageService>lambdaQuery()
                    .eq(XMessageService::getTenancyId, tenancyId)
                    .eq(XMessageService::getIsEnable, 1)
                    .eq(XMessageService::getIsDefault, 1)
                    .last("limit 1"));
            if (service == null) {
                // 尝试查找任意启用的服务
                service = xMessageServiceMapper.selectOne(Wrappers
                        .<XMessageService>lambdaQuery()
                        .eq(XMessageService::getTenancyId, tenancyId)
                        .eq(XMessageService::getIsEnable, 1)
                        .last("limit 1"));
            }
            if (service == null) {
                throw new RRException("短信渠道配置不可用");
            }

            // 6. 频率限制检查
            rateLimiter.checkRateLimit(tenancyId, req.getMobileList().size());

            // 7. 创建任务记录（每个手机号一条）
            List<XMessageServiceTask> tasks = new ArrayList<>();
            Date now = new Date();
            for (String mobile : req.getMobileList()) {
                // 验证手机号格式
                if (!Pattern.matches(MOBILE_REGEX, mobile)) {
                    throw new RRException("手机号格式错误: " + mobile);
                }

                XMessageServiceTask task = new XMessageServiceTask();
                task.setRequestNo(req.getRequestNo());
                task.setAppId(app.getAppId());
                task.setTenancyId(tenancyId);
                task.setTemplateCode(req.getTemplateCode());
                task.setTemplateParams(req.getTemplateParams() != null
                        ? JSONUtil.toJsonStr(req.getTemplateParams()) : null);
                task.setMobile(mobile);
                task.setStatus(0); // PENDING
                task.setRetryCount(0);
                task.setMaxRetry(3);
                task.setServiceId(service.getServiceId());
                task.setSendContent(resolvedContent);
                task.setCreateTime(now);
                task.setUpdateTime(now);
                tasks.add(task);
            }
            saveBatch(tasks);

            // 8. 异步分发发送
            dispatcher.dispatchAsync(tasks);

            return convertToVO(tasks);

        } finally {
            redissonLock.unlock(lockKey);
        }
    }

    @Override
    public List<SmsTaskVO> queryTask(String requestNo, XApplication app) {
        String tenancyId = app.getTenancyId();
        List<XMessageServiceTask> tasks = list(Wrappers.<XMessageServiceTask>lambdaQuery()
                .eq(XMessageServiceTask::getRequestNo, requestNo)
                .eq(XMessageServiceTask::getTenancyId, tenancyId)
                .orderByAsc(XMessageServiceTask::getCreateTime));
        return convertToVO(tasks);
    }

    @Override
    public boolean cancelTask(String requestNo, XApplication app) {
        String tenancyId = app.getTenancyId();
        // 将待发送状态(0)的任务更新为已取消(4)
        XMessageServiceTask updateEntity = new XMessageServiceTask();
        updateEntity.setStatus(4);
        updateEntity.setUpdateTime(new Date());
        int updated = baseMapper.update(updateEntity, Wrappers.<XMessageServiceTask>lambdaUpdate()
                .eq(XMessageServiceTask::getRequestNo, requestNo)
                .eq(XMessageServiceTask::getTenancyId, tenancyId)
                .eq(XMessageServiceTask::getStatus, 0));
        return updated > 0;
    }

    /**
     * 将任务实体转换为 VO
     */
    private List<SmsTaskVO> convertToVO(List<XMessageServiceTask> tasks) {
        return tasks.stream().map(task -> {
            SmsTaskVO vo = new SmsTaskVO();
            vo.setRequestNo(task.getRequestNo());
            vo.setMobile(task.getMobile());
            vo.setStatus(task.getStatus());
            vo.setStatusDesc(SmsTaskVO.getStatusDesc(task.getStatus()));
            vo.setFailReason(task.getFailReason());
            vo.setRetryCount(task.getRetryCount());
            vo.setSendContent(task.getSendContent());
            vo.setCreateTime(task.getCreateTime());
            vo.setUpdateTime(task.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

}
