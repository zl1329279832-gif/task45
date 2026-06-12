package com.suke.czx.modules.msg.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.entity.SmsTaskRequest;
import com.suke.czx.modules.msg.entity.SmsTaskResponse;
import com.suke.czx.modules.msg.entity.XMessageServiceTask;

/**
 * 短信发送任务
 *
 * @author czx
 * @email object_czx@163.com
 */
public interface XMessageServiceTaskService extends IService<XMessageServiceTask> {

    SmsTaskResponse createTask(SmsTaskRequest request, XApplication app);

    void executeTaskAsync(XMessageServiceTask task);

    SmsTaskResponse queryTask(String requestNo, XApplication app);
}
