package com.suke.czx.modules.msg.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.entity.SmsTaskCreateRequest;
import com.suke.czx.modules.msg.entity.SmsTaskVO;
import com.suke.czx.modules.msg.entity.XMessageServiceTask;

import java.util.List;

/**
 * 短信发送任务服务
 *
 * @author czx
 * @email object_czx@163.com
 */
public interface XMessageServiceTaskService extends IService<XMessageServiceTask> {

    /**
     * 创建短信发送任务（同步校验+落库，异步发送）
     *
     * @param request 创建请求
     * @param app     已认证的应用信息
     * @return 创建的任务列表
     */
    List<SmsTaskVO> createTask(SmsTaskCreateRequest request, XApplication app);

    /**
     * 按请求号查询任务状态
     *
     * @param requestNo 业务请求号
     * @param app       已认证的应用信息
     * @return 任务列表
     */
    List<SmsTaskVO> queryTask(String requestNo, XApplication app);

    /**
     * 取消待发送的任务
     *
     * @param requestNo 业务请求号
     * @param app       已认证的应用信息
     * @return 是否取消成功
     */
    boolean cancelTask(String requestNo, XApplication app);

}
