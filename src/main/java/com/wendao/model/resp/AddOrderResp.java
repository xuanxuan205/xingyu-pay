package com.wendao.model.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
@Data
public class AddOrderResp {

    /**
     * 唯一标识
     */
    @ApiModelProperty(value = "id")
    private String id;

    @ApiModelProperty(value = "金额")
    private BigDecimal money;

    @ApiModelProperty(value = "支付方式")
    private String payType;

    @ApiModelProperty(value = "支付随机码")
    private String payNum;

    /**
     * 是否自定义输入。固定金额模式下恒为 false，保留字段兼容旧前端。
     */
    @ApiModelProperty(value = "是否自定义输入，固定金额模式下恒为 false")
    private Boolean custom;

    /**
     * 支付二维码标识
     */
    private Integer payQrNum;

    /**
     * 是否使用本地金额二维码（当前仅微信为 true）
     */
    @ApiModelProperty(value = "是否使用本地金额二维码")
    private Boolean localQr;

    /**
     * 实际二维码地址，例如 /assets/qr/wechat/5.00/1.png
     */
    @ApiModelProperty(value = "实际二维码地址")
    private String qrUrl;

    /**
     * 匹配模式: REMARK=备注匹配, DECREMENT=减额匹配
     */
    @ApiModelProperty(value = "匹配模式")
    private String matchMode;

    /**
     * 实际支付金额
     */
    @ApiModelProperty(value = "实际支付金额")
    private BigDecimal actualAmount;

    /**
     * 是否槽位已满(回退到备注模式)
     */
    @ApiModelProperty(value = "是否槽位已满(回退到备注模式)")
    private Boolean fallbackToRemark;
}
