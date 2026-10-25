package com.wendao.model.req;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * @description:
 **/
@Data
public class GetOrderListReq {
    /**
     * 当前页码
     */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageIndex = 1;

    /**
     * 每页显示条数
     */
    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方式 ASC/DESC
     */
    private String order = "DESC";

    @Size(max = 100, message = "搜索关键字过长")
    private String keyword;

    private List<Integer> states;
}
