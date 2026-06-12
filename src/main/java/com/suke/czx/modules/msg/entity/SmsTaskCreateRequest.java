package com.suke.czx.modules.msg.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 短信任务创建请求
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
@Schema(description = "短信任务创建请求")
public class SmsTaskCreateRequest {

    @NotBlank(message = "业务请求号不能为空")
    @Schema(description = "业务请求号（幂等键）")
    public String requestNo;

    @NotBlank(message = "模板编码不能为空")
    @Schema(description = "模板编码")
    public String templateCode;

    @NotEmpty(message = "手机号列表不能为空")
    @Schema(description = "手机号列表")
    public List<String> mobileList;

    @Schema(description = "模板变量")
    public Map<String, String> templateParams;

}
