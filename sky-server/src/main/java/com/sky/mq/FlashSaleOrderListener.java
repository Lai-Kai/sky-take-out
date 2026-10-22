package com.sky.mq;

import com.rabbitmq.client.Channel;
import com.sky.constant.FlashSaleConstant;
import com.sky.dto.FlashSaleOrderMessage;
import com.sky.service.FlashSaleOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class FlashSaleOrderListener {

    @Autowired
    private FlashSaleOrderService flashSaleOrderService;

    @RabbitListener(queues = FlashSaleConstant.ORDER_QUEUE)
    public void listen(FlashSaleOrderMessage orderMessage, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // Only ack after the database transaction finishes successfully.
            flashSaleOrderService.createOrder(orderMessage);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("flash sale order create failed: {}", orderMessage, e);
            // Do not requeue failed messages here, otherwise a bad message may loop forever.
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
