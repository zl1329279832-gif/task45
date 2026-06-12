package com.suke.czx.modules.msg.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 短信任务状态响应
 *
 * @author czx
 * @email object_czx@163.com
 */
@Data
@Schema(description = "短信任务状态响应")
public class SmsTaskVO {

    @Schema(description = "业务请求号")
    public String requestNo;

    @Schema(description = "手机号")
    public String mobile;

    @Schema(description = "状态:0待发送,1发送中,2成功,3失败,4已取消")
    public Integer status;

    @Schema(description = "状态描述")
    public String statusDesc;

    @Schema(description = "失败原因")
    public String failReason;

    @Schema(description = "已重试次数")
    public Integer retryCount;

    @Schema(description = "实际发送内容")
    public String sendContent;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public Date createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public Date updateTime;

    /**
     * 根据状态码返回中文描述
     */
    public static String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待发送";
            case 1 -> "发送中";
            case 2 -> "成功";
            case 3 -> "失败";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

}
