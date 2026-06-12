package com.suke.czx.modules.application.annotation;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.suke.czx.common.exception.RRException;
import com.suke.czx.common.utils.HttpContextUtils;
import com.suke.czx.modules.application.entity.XApplication;
import com.suke.czx.modules.application.service.XApplicationService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 验证app的appKey和appSecret
 */
@Slf4j
@Aspect
@Component
public class ApplicationAuthAspect {

    /**
     * 存放当前线程已认证的应用信息，供下游Controller/Service获取appId和tenancyId
     */
    public static final ThreadLocal<XApplication> CURRENT_APP = new ThreadLocal<>();

    @Resource
    public XApplicationService xApplicationService;

    @Pointcut("@annotation(com.suke.czx.modules.application.annotation.ApplicationAuth)")
    public void queryMethod() {
    }


    @Before("queryMethod()")
    public void beforeAdvice(JoinPoint joinPoint) {
        // 获取方法名称和参数
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        String appKey = request.getHeader("appKey");
        String appSecret = request.getHeader("appSecret");
        if (StrUtil.isEmpty(appKey) || StrUtil.isEmpty(appSecret)) {
            throw new RRException("认证错误");
        }
        XApplication application = xApplicationService.getOne(Wrappers.<XApplication>lambdaQuery().eq(XApplication::getAppKey, appKey).eq(XApplication::getAppSecret, appSecret).last("limit 1"));
        if (application == null) {
            throw new RRException("认证错误");
        }
        // 将认证后的应用信息存入ThreadLocal，供下游使用
        CURRENT_APP.set(application);
    }


    @After("queryMethod()")
    public void afterAdvice(JoinPoint joinPoint) {
        // 清除ThreadLocal，防止虚拟线程复用时数据泄漏
        CURRENT_APP.remove();
    }
}