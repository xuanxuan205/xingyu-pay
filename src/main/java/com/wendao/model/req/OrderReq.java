package com.wendao.model.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class OrderReq implements Serializable {

    private String nickName;

    private Long productId;

    /**
     * 兼容旧客户端保留；固定金额模式下服务端忽略此值，以 productId 对应商品价格为准。
     */
    private BigDecimal money;

    /**
     * 描述
     */
    private String description;

    private Date createTime;

    private Date updateTime;

    /**
     * 用户通知邮箱
     */
    private String email;

    /**
     * 显示状态 0待审核 1确认显示 2驳回 3通过不展示 4已扫码
     */
    private Integer state=0;

    private String payType;

    /**
     * 支付标识
     */
    private String payNum;

    /**
     * 是否自定义输入
     */
    private Boolean custom;

    private String userId;

    /**
     * 支付设备是否为移动端
     */
    private Boolean mobile;

    /**
     * 用户支付设备信息
     */
    private String device;

    private String time;
}
