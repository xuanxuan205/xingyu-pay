package com.wendao.model.req;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OpenApiOrderReq implements Serializable {

    @NotBlank(message = "订单号不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{6,64}", message = "订单号格式不正确")
    private String orderNo;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    @NotBlank(message = "支付方式不能为空")
    @Size(max = 16, message = "支付方式不合法")
    private String payType;

    @Size(max = 255, message = "昵称过长")
    private String nickName;

    @Size(max = 255, message = "描述过长")
    private String description;

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱过长")
    private String email;

    @Size(max = 2048, message = "回调地址过长")
    private String notifyUrl;

    private String userId;

    private Long productId;

    @NotBlank(message = "签名不能为空")
    private String sign;

    private Long timestamp;


    /** 是否是自定义金额 */
    private Boolean custom;

    /** 过期时间（秒） */
    private Integer expireSeconds;
}
