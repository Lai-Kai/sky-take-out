package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sky.constant.FlashSaleConstant;
import com.sky.dto.FlashSaleOrderMessage;
import com.sky.entity.AddressBook;
import com.sky.entity.FlashSaleActivity;
import com.sky.entity.FlashSaleOrder;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.mapper.FlashSaleOrderMapper;
import com.sky.service.AddressBookService;
import com.sky.service.FlashSaleActivityService;
import com.sky.service.FlashSaleOrderService;
import com.sky.service.OrderDetailService;
import com.sky.service.OrderService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class FlashSaleOrderServiceImpl
        extends ServiceImpl<FlashSaleOrderMapper, FlashSaleOrder>
        implements FlashSaleOrderService {

    @Autowired
    private FlashSaleActivityService flashSaleActivityService;

    @Autowired
    private FlashSaleActivityMapper flashSaleActivityMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDetailService orderDetailService;

    @Autowired
    private AddressBookService addressBookService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(FlashSaleOrderMessage message) {
        // Consumer-side lock protects the database write path from duplicate MQ delivery.
        RLock lock = redissonClient.getLock(FlashSaleConstant.LOCK_KEY_PREFIX
                + "create:" + message.getActivityId() + ":" + message.getUserId());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BaseException(FlashSaleConstant.REQUEST_BUSY);
            }

            FlashSaleActivity activity = flashSaleActivityService.getById(message.getActivityId());
            if (activity == null) {
                throw new BaseException(FlashSaleConstant.ACTIVITY_NOT_FOUND);
            }

            FlashSaleOrder existed = getOne(new LambdaQueryWrapper<FlashSaleOrder>()
                    .eq(FlashSaleOrder::getActivityId, message.getActivityId())
                    .eq(FlashSaleOrder::getUserId, message.getUserId())
                    .last("limit 1"));
            if (existed != null) {
                return;
            }

            // Insert the flash sale order first. The unique index is the final one-user-one-order guard.
            FlashSaleOrder flashSaleOrder = FlashSaleOrder.builder()
                    .activityId(activity.getId())
                    .setmealId(activity.getSetmealId())
                    .userId(message.getUserId())
                    .status(FlashSaleOrder.PROCESSING)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            save(flashSaleOrder);

            // MySQL also deducts stock with "stock > 0"; this is a second safety net after Redis.
            int deducted = flashSaleActivityMapper.deductStock(activity.getId());
            if (deducted == 0) {
                throw new BaseException(FlashSaleConstant.OUT_OF_STOCK);
            }

            // Create the normal order only after the flash sale guards pass.
            String orderNumber = "FS" + System.currentTimeMillis() + message.getUserId();
            Orders order = Orders.builder()
                    .number(orderNumber)
                    .status(Orders.PENDING_PAYMENT)
                    .userId(message.getUserId())
                    .addressBookId(message.getAddressBookId())
                    .orderTime(LocalDateTime.now())
                    .payStatus(Orders.UN_PAID)
                    .amount(activity.getPrice())
                    .remark("flash sale activity " + message.getActivityId())
                    .build();
            fillAddress(order, message.getAddressBookId());
            orderService.save(order);

            OrderDetail orderDetail = OrderDetail.builder()
                    .name(activity.getTitle())
                    .orderId(order.getId())
                    .setmealId(activity.getSetmealId())
                    .number(1)
                    .amount(activity.getPrice())
                    .build();
            orderDetailService.save(orderDetail);

            flashSaleOrder.setOrderId(order.getId());
            flashSaleOrder.setOrderNumber(orderNumber);
            flashSaleOrder.setStatus(FlashSaleOrder.CREATED);
            flashSaleOrder.setUpdateTime(LocalDateTime.now());
            updateById(flashSaleOrder);
        } catch (DuplicateKeyException e) {
            // Unique index uk_flash_sale_user guarantees one user can create only one flash sale order.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rollbackRedis(message);
            throw new BaseException(FlashSaleConstant.REQUEST_BUSY);
        } catch (RuntimeException e) {
            rollbackRedis(message);
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void fillAddress(Orders order, Long addressBookId) {
        if (addressBookId == null) {
            return;
        }
        AddressBook addressBook = addressBookService.getById(addressBookId);
        if (addressBook == null) {
            return;
        }
        order.setPhone(addressBook.getPhone());
        order.setConsignee(addressBook.getConsignee());
        order.setAddress(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
    }

    private void rollbackRedis(FlashSaleOrderMessage message) {
        // If database order creation fails, return the Redis reservation so another user can buy.
        stringRedisTemplate.opsForValue().increment(FlashSaleConstant.STOCK_KEY_PREFIX + message.getActivityId());
        stringRedisTemplate.opsForSet().remove(
                FlashSaleConstant.USERS_KEY_PREFIX + message.getActivityId(),
                message.getUserId().toString());
    }
}
