package com.suke.czx.modules.msg.controller;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.suke.czx.common.annotation.ResourceAuth;
import com.suke.czx.common.base.AbstractController;
import com.suke.czx.common.utils.R;
import com.suke.czx.modules.msg.entity.XMessageServiceTask;
import com.suke.czx.modules.msg.service.XMessageServiceTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


/**
 * 短信任务管理（后台管理）
 *
 * @author czx
 * @email object_czx@163.com
 */
@RestController
@AllArgsConstructor
@RequestMapping("/msg/task")
@Tag(name = "XMessageServiceTaskController", description = "短信任务管理")
public class XMessageServiceTaskController extends AbstractController {

    private final XMessageServiceTaskService xMessageServiceTaskService;

    @ResourceAuth(value = "短信任务列表", module = "短信服务")
    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        QueryWrapper<XMessageServiceTask> queryWrapper = new QueryWrapper<>();
        final String keyword = mpPageConvert.getKeyword(params);

        String tenancyId = MapUtil.getStr(params, "tenancyId");
        String status = MapUtil.getStr(params, "status");
        String requestNo = MapUtil.getStr(params, "requestNo");
        String mobile = MapUtil.getStr(params, "mobile");

        if (StrUtil.isNotEmpty(tenancyId)) {
            queryWrapper.lambda().eq(XMessageServiceTask::getTenancyId, tenancyId);
        }
        if (StrUtil.isNotEmpty(status)) {
            queryWrapper.lambda().eq(XMessageServiceTask::getStatus, status);
        }
        if (StrUtil.isNotEmpty(requestNo)) {
            queryWrapper.lambda().eq(XMessageServiceTask::getRequestNo, requestNo);
        }
        if (StrUtil.isNotEmpty(mobile)) {
            queryWrapper.lambda().like(XMessageServiceTask::getMobile, mobile);
        }
        if (StrUtil.isNotEmpty(keyword)) {
            queryWrapper.lambda().and(w -> w
                    .like(XMessageServiceTask::getRequestNo, keyword)
                    .or().like(XMessageServiceTask::getMobile, keyword)
                    .or().like(XMessageServiceTask::getSendContent, keyword));
        }

        queryWrapper.orderByDesc("create_time");

        IPage<XMessageServiceTask> listPage = xMessageServiceTaskService.page(
                mpPageConvert.<XMessageServiceTask>pageParamConvert(params), queryWrapper);

        return R.ok().setData(listPage);
    }

}
