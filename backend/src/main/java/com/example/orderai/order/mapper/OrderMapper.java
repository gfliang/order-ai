package com.example.orderai.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.orderai.order.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    /** 统计当前用户未取消订单的累计金额 */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM orders "
            + "WHERE owner_user_id = #{ownerUserId} AND status <> 'CANCELLED'")
    BigDecimal sumActiveAmount(@Param("ownerUserId") String ownerUserId);
}
