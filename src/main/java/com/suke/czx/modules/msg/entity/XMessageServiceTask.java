package com.suke.czx.modules.msg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.suke.czx.modules.tenancy.entity.TenancyBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


/**
 * 短信发送任务
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("x_message_service_task")
public class XMessageServiceTask extends TenancyBase implements Serializable {

    public static final long serialVersionUID = 1L;

    /**
     * 任务状态常量
     */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENDING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_CANCELLED = 4;

    @TableId(type = IdType.AUTO)
    @Schema(description = "任务ID")
    @JsonProperty(value = "taskId")
    public Long taskId;

    @Schema(description = "应用ID")
    @JsonProperty(value = "appId")
    public Integer appId;

    @Schema(description = "业务请求号")
    @JsonProperty(value = "requestNo")
    public String requestNo;

    @Schema(description = "模板编码")
    @JsonProperty(value = "templateCode")
    public String templateCode;

    @Schema(description = "模板ID")
    @JsonProperty(value = "templateId")
    public Integer templateId;

    @Schema(description = "短信服务ID")
    @JsonProperty(value = "serviceId")
    public Integer serviceId;

    @Schema(description = "手机号")
    @JsonProperty(value = "mobile")
    public String mobile;

    @Schema(description = "模板变量JSON")
    @JsonProperty(value = "templateParams")
    public String templateParams;

    @Schema(description = "发送内容")
    @JsonProperty(value = "sendContent")
    public String sendContent;

    @Schema(description = "任务状态：0待发送 1发送中 2成功 3失败 4已取消")
    @JsonProperty(value = "taskStatus")
    public Integer taskStatus;

    @Schema(description = "已重试次数")
    @JsonProperty(value = "retryCount")
    public Integer retryCount;

    @Schema(description = "最大重试次数")
    @JsonProperty(value = "maxRetry")
    public Integer maxRetry;

    @Schema(description = "错误信息")
    @JsonProperty(value = "errorMessage")
    public String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    @JsonProperty(value = "createTime")
    public Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "更新时间")
    @JsonProperty(value = "updateTime")
    public Date updateTime;

    public static String statusName(int status) {
        return switch (status) {
            case STATUS_PENDING -> "PENDING";
            case STATUS_SENDING -> "SENDING";
            case STATUS_SUCCESS -> "SUCCESS";
            case STATUS_FAILED -> "FAILED";
            case STATUS_CANCELLED -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }
}
