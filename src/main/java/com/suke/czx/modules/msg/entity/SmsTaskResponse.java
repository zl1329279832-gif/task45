package com.suke.czx.modules.msg.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * 短信任务查询响应
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
public class SmsTaskResponse implements Serializable {

    public static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    @JsonProperty(value = "taskId")
    public Long taskId;

    @Schema(description = "业务请求号")
    @JsonProperty(value = "requestNo")
    public String requestNo;

    @Schema(description = "任务状态")
    @JsonProperty(value = "taskStatus")
    public Integer taskStatus;

    @Schema(description = "任务状态名称")
    @JsonProperty(value = "taskStatusName")
    public String taskStatusName;

    @Schema(description = "发送内容")
    @JsonProperty(value = "sendContent")
    public String sendContent;

    @Schema(description = "错误信息")
    @JsonProperty(value = "errorMessage")
    public String errorMessage;

    @Schema(description = "重试次数")
    @JsonProperty(value = "retryCount")
    public Integer retryCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    @JsonProperty(value = "createTime")
    public Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "更新时间")
    @JsonProperty(value = "updateTime")
    public Date updateTime;
}
