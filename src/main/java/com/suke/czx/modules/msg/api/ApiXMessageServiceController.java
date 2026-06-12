package com.suke.czx.modules.msg.api;

import com.suke.czx.common.base.AbstractController;
import com.suke.czx.common.utils.HttpContextUtils;
import com.suke.czx.common.utils.R;
import com.suke.czx.modules.application.annotation.ApplicationAuth;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.entity.SendMsg;
import com.suke.czx.modules.msg.entity.SmsTaskRequest;
import com.suke.czx.modules.msg.entity.SmsTaskResponse;
import com.suke.czx.modules.msg.service.XMessageServiceSendRecordService;
import com.suke.czx.modules.msg.service.XMessageServiceTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.suke.czx.common.utils.Constant.X_AUTHENTICATED_APP;


/**
 * 短信服务
 *
 * @author czx
 * @email object_czx@163.com
 * @date 2025-08-15 17:51:27
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/msg/service")
@Tag(name = "XMessageServiceController", description = "短信服务")
public class ApiXMessageServiceController extends AbstractController {

    private final XMessageServiceSendRecordService xMessageServiceSendRecordService;
    private final XMessageServiceTaskService xMessageServiceTaskService;

    @ApplicationAuth
    @PostMapping("/send")
    public R list(@RequestBody SendMsg params) {
        boolean bool = xMessageServiceSendRecordService.autoServiceSendMessage(params);
        return R.ok().setData(bool);
    }

    @ApplicationAuth
    @PostMapping("/task/create")
    public R createTask(@RequestBody SmsTaskRequest request) {
        XApplication app = (XApplication) HttpContextUtils.getHttpServletRequest().getAttribute(X_AUTHENTICATED_APP);
        SmsTaskResponse response = xMessageServiceTaskService.createTask(request, app);
        return R.ok().setData(response);
    }

    @ApplicationAuth
    @GetMapping("/task/query")
    public R queryTask(@RequestParam("requestNo") String requestNo) {
        XApplication app = (XApplication) HttpContextUtils.getHttpServletRequest().getAttribute(X_AUTHENTICATED_APP);
        SmsTaskResponse response = xMessageServiceTaskService.queryTask(requestNo, app);
        return R.ok().setData(response);
    }

}