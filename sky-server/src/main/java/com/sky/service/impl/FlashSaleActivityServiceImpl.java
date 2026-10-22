package com.sky.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sky.constant.FlashSaleConstant;
import com.sky.context.BaseContext;
import com.sky.dto.FlashSaleCreateDTO;
import com.sky.dto.FlashSaleOrderMessage;
import com.sky.dto.FlashSaleSubmitDTO;
import com.sky.entity.FlashSaleActivity;
import com.sky.exception.BaseException;
import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.service.FlashSaleActivityService;
import com.sky.vo.FlashSaleSubmitVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
public class FlashSaleActivityServiceImpl
        extends ServiceImpl<FlashSaleActivityMapper, FlashSaleActivity>
        implements FlashSaleActivityService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private DefaultRedisScript<Long> flashSaleScript;

    @PostConstruct
    public void initScript() {
        // Load the Lua script once when Spring starts. Later requests reuse this script object.
        flashSaleScript = new DefaultRedisScript<>();
        flashSaleScript.setResultType(Long.class);
        flashSaleScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/flash_sale.lua")));
    }

    @Override
    public Long createActivity(FlashSaleCreateDTO dto) {
        validateCreateDTO(dto);
        FlashSaleActivity activity = new FlashSaleActivity();
        BeanUtils.copyProperties(dto, activity);
        if (activity.getStatus() == null) {
            activity.setStatus(FlashSaleActivity.ENABLE);
        }
        save(activity);
        warmUpStock(activity.getId());
        return activity.getId();
    }

    @Override
    public void warmUpStock(Long activityId) {
        FlashSaleActivity activity = getById(activityId);
        if (activity == null) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_NOT_FOUND);
        }
        // Warm-up means putting stock into Redis before traffic arrives, so users do not hit MySQL first.
        String stockKey = stockKey(activityId);
        stringRedisTemplate.opsForValue().set(stockKey, activity.getStock().toString());
        stringRedisTemplate.delete(usersKey(activityId));
    }

    @Override
    public FlashSaleSubmitVO submit(FlashSaleSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (dto == null || dto.getActivityId() == null) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_NOT_FOUND);
        }
        if (userId == null) {
            throw new BaseException("user not login");
        }

        FlashSaleActivity activity = getById(dto.getActivityId());
        validateActivity(activity);

        // This lock blocks repeated clicks from the same user for the same activity.
        RLock lock = redissonClient.getLock(FlashSaleConstant.LOCK_KEY_PREFIX + dto.getActivityId() + ":" + userId);
        boolean locked = false;
        boolean reserved = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (!locked) {
                throw new BaseException(FlashSaleConstant.REQUEST_BUSY);
            }

            // Lua runs inside Redis as one atomic operation: check duplicate, check stock, deduct stock.
            Long result = stringRedisTemplate.execute(
                    flashSaleScript,
                    Arrays.asList(stockKey(dto.getActivityId()), usersKey(dto.getActivityId())),
                    userId.toString());

            if (result == null) {
                throw new BaseException(FlashSaleConstant.REQUEST_BUSY);
            }
            if (result == 1) {
                throw new BaseException(FlashSaleConstant.OUT_OF_STOCK);
            }
            if (result == 2) {
                throw new BaseException(FlashSaleConstant.REPEAT_ORDER);
            }
            reserved = true;

            FlashSaleOrderMessage message = FlashSaleOrderMessage.builder()
                    .activityId(dto.getActivityId())
                    .userId(userId)
                    .addressBookId(dto.getAddressBookId())
                    .requestTimeMillis(System.currentTimeMillis())
                    .build();
            // The HTTP request stops here. Real order creation is moved to RabbitMQ consumer.
            rabbitTemplate.convertAndSend(
                    FlashSaleConstant.EXCHANGE,
                    FlashSaleConstant.ORDER_ROUTING_KEY,
                    message);

            return FlashSaleSubmitVO.builder()
                    .activityId(dto.getActivityId())
                    .userId(userId)
                    .status(FlashSaleConstant.SUBMIT_SUCCESS)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(FlashSaleConstant.REQUEST_BUSY);
        } catch (RuntimeException e) {
            if (reserved) {
                rollbackRedis(dto.getActivityId(), userId);
            }
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateCreateDTO(FlashSaleCreateDTO dto) {
        if (dto == null || dto.getSetmealId() == null || dto.getStock() == null || dto.getStock() <= 0) {
            throw new BaseException("invalid flash sale activity params");
        }
        if (dto.getBeginTime() == null || dto.getEndTime() == null || !dto.getBeginTime().isBefore(dto.getEndTime())) {
            throw new BaseException("invalid flash sale activity time");
        }
    }

    private void validateActivity(FlashSaleActivity activity) {
        if (activity == null) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_NOT_FOUND);
        }
        if (!FlashSaleActivity.ENABLE.equals(activity.getStatus())) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_DISABLED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getBeginTime() != null && now.isBefore(activity.getBeginTime())) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_NOT_STARTED);
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BaseException(FlashSaleConstant.ACTIVITY_ENDED);
        }
        if (stringRedisTemplate.opsForValue().get(stockKey(activity.getId())) == null) {
            warmUpStock(activity.getId());
        }
    }

    private void rollbackRedis(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            return;
        }
        // If MQ send fails after Redis pre-deducts stock, return stock and remove the user mark.
        stringRedisTemplate.opsForValue().increment(stockKey(activityId));
        stringRedisTemplate.opsForSet().remove(usersKey(activityId), userId.toString());
    }

    private String stockKey(Long activityId) {
        return FlashSaleConstant.STOCK_KEY_PREFIX + activityId;
    }

    private String usersKey(Long activityId) {
        return FlashSaleConstant.USERS_KEY_PREFIX + activityId;
    }
}
