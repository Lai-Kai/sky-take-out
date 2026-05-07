package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.entity.User;
import com.sky.service.DishService;
import com.sky.service.OrderService;
import com.sky.service.SetMealService;
import com.sky.service.UserService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderService orderService;
    @Autowired
    private UserService userService;
    @Autowired
    private DishService dishService;
    @Autowired
    private SetMealService setmealService;

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {


        //查询总订单数
        LambdaQueryWrapper<Orders> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.ge(Orders::getOrderTime, begin).lt(Orders::getOrderTime, end);
        Integer totalOrderCount = Math.toIntExact(orderService.count(orderWrapper));

        //营业额
        LambdaQueryWrapper<Orders> turnoverWrapper = new LambdaQueryWrapper<>();
        turnoverWrapper.ge(Orders::getOrderTime, begin).lt(Orders::getOrderTime, end).eq(Orders::getStatus, Orders.COMPLETED);
        Double turnover = orderService.list(turnoverWrapper).stream().mapToDouble(Orders::getAmount).sum();
        turnover = turnover == null? 0.0 : turnover;

        //有效订单数
        LambdaQueryWrapper<Orders> validWrapper = new LambdaQueryWrapper<>();
        validWrapper.ge(Orders::getOrderTime, begin).lt(Orders::getOrderTime, end).eq(Orders::getStatus, Orders.COMPLETED);
        Integer validOrderCount = Math.toIntExact(orderService.count(validWrapper));

        Double unitPrice = 0.0;

        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0 && validOrderCount != 0){
            //订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            //平均客单价
            unitPrice = turnover / validOrderCount;
        }

        //新增用户数
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.ge(User::getCreateTime, begin).le(User::getCreateTime, end);
        Integer newUsers = Math.toIntExact(userService.count(userWrapper));

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     *
     * @return
     */
    public OrderOverViewVO getOrderOverView() {
        LocalDateTime todayBegin = LocalDateTime.now().with(LocalTime.MIN);

        //待接单
        LambdaQueryWrapper<Orders> waitingWrapper = new LambdaQueryWrapper<>();
        waitingWrapper.ge(Orders::getOrderTime, todayBegin).eq(Orders::getStatus, Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = Math.toIntExact(orderService.count(waitingWrapper));

        //待派送
        LambdaQueryWrapper<Orders> deliveredWrapper = new LambdaQueryWrapper<>();
        deliveredWrapper.ge(Orders::getOrderTime, todayBegin).eq(Orders::getStatus, Orders.CONFIRMED);
        Integer deliveredOrders = Math.toIntExact(orderService.count(deliveredWrapper));

        //已完成
        LambdaQueryWrapper<Orders> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.ge(Orders::getOrderTime, todayBegin).eq(Orders::getStatus, Orders.COMPLETED);
        Integer completedOrders = Math.toIntExact(orderService.count(completedWrapper));

        //已取消
        LambdaQueryWrapper<Orders> cancelledWrapper = new LambdaQueryWrapper<>();
        cancelledWrapper.ge(Orders::getOrderTime, todayBegin).eq(Orders::getStatus, Orders.CANCELLED);
        Integer cancelledOrders = Math.toIntExact(orderService.count(cancelledWrapper));

        //全部订单
        LambdaQueryWrapper<Orders> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.ge(Orders::getOrderTime, todayBegin);
        Integer allOrders = Math.toIntExact(orderService.count(allWrapper));

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    public DishOverViewVO getDishOverView() {
        LambdaQueryWrapper<Dish> enabledWrapper = new LambdaQueryWrapper<>();
        enabledWrapper.eq(Dish::getStatus, StatusConstant.ENABLE);
        Integer sold = Math.toIntExact(dishService.count(enabledWrapper));

        LambdaQueryWrapper<Dish> disabledWrapper = new LambdaQueryWrapper<>();
        disabledWrapper.eq(Dish::getStatus, StatusConstant.DISABLE);
        Integer discontinued = Math.toIntExact(dishService.count(disabledWrapper));

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览
     *
     * @return
     */
    public SetmealOverViewVO getSetmealOverView() {
        LambdaQueryWrapper<Setmeal> enabledWrapper = new LambdaQueryWrapper<>();
        enabledWrapper.eq(Setmeal::getStatus, StatusConstant.ENABLE);
        Integer sold = Math.toIntExact(setmealService.count(enabledWrapper));

        LambdaQueryWrapper<Setmeal> disabledWrapper = new LambdaQueryWrapper<>();
        disabledWrapper.eq(Setmeal::getStatus, StatusConstant.DISABLE);
        Integer discontinued = Math.toIntExact(setmealService.count(disabledWrapper));

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
