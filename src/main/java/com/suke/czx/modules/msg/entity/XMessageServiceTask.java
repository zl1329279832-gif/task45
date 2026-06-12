package com.suke.czx.modules.msg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * 短信发送任务
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
@TableName("x_message_service_task")
@Schema(description = "短信发送任务")
public class XMessageServiceTask implements Serializable {

    public static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "任务ID")
    @JsonProperty(value = "taskId")
    public Integer taskId;

    @Schema(description = "业务请求号")
    @JsonProperty(value = "requestNo")
    public String requestNo;

    @Schema(description = "应用ID")
    @JsonProperty(value = "appId")
    public Integer appId;

    @Schema(description = "租户ID")
    @JsonProperty(value = "tenancyId")
    public String tenancyId;

    @Schema(description = "模板编码")
    @JsonProperty(value = "templateCode")
    public String templateCode;

    @Schema(description = "模板变量JSON")
    @JsonProperty(value = "templateParams")
    public String templateParams;

    @Schema(description = "手机号")
    @JsonProperty(value = "mobile")
    public String mobile;

    @Schema(description = "状态:0待发送,1发送中,2成功,3失败,4已取消")
    @JsonProperty(value = "status")
    public Integer status;

    @Schema(description = "失败原因")
    @JsonProperty(value = "failReason")
    public String failReason;

    @Schema(description = "已重试次数")
    @JsonProperty(value = "retryCount")
    public Integer retryCount;

    @Schema(description = "最大重试次数")
    @JsonProperty(value = "maxRetry")
    public Integer maxRetry;

    @Schema(description = "下次重试时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty(value = "nextRetryTime")
    public Date nextRetryTime;

    @Schema(description = "使用的短信服务ID")
    @JsonProperty(value = "serviceId")
    public Integer serviceId;

    @Schema(description = "实际发送内容")
    @JsonProperty(value = "sendContent")
    public String sendContent;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty(value = "createTime")
    public Date createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty(value = "updateTime")
    public Date updateTime;

}
