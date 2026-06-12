package com.suke.czx.modules.msg.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;


/**
 * 短信任务创建请求
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
public class SmsTaskRequest implements Serializable {

    public static final long serialVersionUID = 1L;

    @Schema(description = "模板编码")
    @JsonProperty(value = "templateCode")
    public String templateCode;

    @Schema(description = "手机号")
    @JsonProperty(value = "mobile")
    public String mobile;

    @Schema(description = "模板变量")
    @JsonProperty(value = "templateParams")
    public Map<String, String> templateParams;

    @Schema(description = "业务请求号（幂等键）")
    @JsonProperty(value = "requestNo")
    public String requestNo;
}
