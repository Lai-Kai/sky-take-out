package com.sky.config;

import com.sky.constant.FlashSaleConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    // Exchange is the "message router". Flash sale requests are sent here first.
    @Bean
    public DirectExchange flashSaleExchange() {
        return new DirectExchange(FlashSaleConstant.EXCHANGE, true, false);
    }

    // Queue is the "waiting room". Consumers take messages from this queue and create orders slowly.
    @Bean
    public Queue flashSaleOrderQueue() {
        return new Queue(FlashSaleConstant.ORDER_QUEUE, true);
    }

    // Binding connects the router and the waiting room through a routing key.
    @Bean
    public Binding flashSaleOrderBinding(Queue flashSaleOrderQueue, DirectExchange flashSaleExchange) {
        return BindingBuilder
                .bind(flashSaleOrderQueue)
                .to(flashSaleExchange)
                .with(FlashSaleConstant.ORDER_ROUTING_KEY);
    }

    // JSON messages are easier to inspect than Java binary serialization.
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        // Pull only a small batch at a time so the database is not hit by too many writes at once.
        factory.setPrefetchCount(20);
        // Manual ack means: acknowledge the message only after the order is really created.
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        return factory;
    }
}
