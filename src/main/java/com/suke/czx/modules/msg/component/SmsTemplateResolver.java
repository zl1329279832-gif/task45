package com.suke.czx.modules.msg.component;

import com.suke.czx.common.exception.RRException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 短信模板变量替换工具
 *
 * @author czx
 * @email object_czx@163.com
 */
public class SmsTemplateResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    /**
     * 将模板中的 ${varName} 占位符替换为 params 中对应的值。
     * 如果模板中存在变量但 params 中未提供对应值，抛出 RRException。
     *
     * @param template 模板内容，如 "您的验证码是${code}，有效期${expire}分钟"
     * @param params   模板变量键值对
     * @return 替换后的完整内容
     */
    public static String resolve(String template, Map<String, String> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (params == null) {
            params = Collections.emptyMap();
        }

        // 提取模板中所有 ${xxx} 变量名
        Set<String> requiredVars = extractVariableNames(template);

        // 检查缺失的变量
        Set<String> missing = requiredVars.stream()
                .filter(v -> !params.containsKey(v))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new RRException("模板变量缺失: " + missing);
        }

        // 逐个替换
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 提取模板中所有 ${varName} 的变量名
     */
    public static Set<String> extractVariableNames(String template) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        return matcher.results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
    }

}
