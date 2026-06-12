package com.suke.czx.modules.msg.api;

import com.suke.czx.common.base.AbstractController;
import com.suke.czx.common.utils.R;
import com.suke.czx.modules.application.annotation.ApplicationAuth;
import com.suke.czx.modules.application.annotation.ApplicationAuthAspect;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.msg.entity.SmsTaskCreateRequest;
import com.suke.czx.modules.msg.service.XMessageServiceTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 短信任务中心 - 第三方应用API
 *
 * @author czx
 * @email object_czx@163.com
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/msg/task")
@Tag(name = "ApiSmsTaskController", description = "短信任务中心")
public class ApiSmsTaskController extends AbstractController {

    private final XMessageServiceTaskService xMessageServiceTaskService;

    @ApplicationAuth
    @PostMapping("/create")
    @Operation(summary = "创建短信发送任务")
    public R create(@Validated @RequestBody SmsTaskCreateRequest request) {
        XApplication app = ApplicationAuthAspect.CURRENT_APP.get();
        return R.ok().setData(xMessageServiceTaskService.createTask(request, app));
    }

    @ApplicationAuth
    @GetMapping("/status")
    @Operation(summary = "查询任务状态")
    public R status(@RequestParam String requestNo) {
        XApplication app = ApplicationAuthAspect.CURRENT_APP.get();
        return R.ok().setData(xMessageServiceTaskService.queryTask(requestNo, app));
    }

    @ApplicationAuth
    @PostMapping("/cancel")
    @Operation(summary = "取消待发送任务")
    public R cancel(@RequestParam String requestNo) {
        XApplication app = ApplicationAuthAspect.CURRENT_APP.get();
        return R.ok().setData(xMessageServiceTaskService.cancelTask(requestNo, app));
    }

}
