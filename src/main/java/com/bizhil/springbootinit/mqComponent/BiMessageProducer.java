package com.bizhil.springbootinit.mqComponent;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * rabbitmq发送消息组件
 */

@Component
public class BiMessageProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     * @param message
     */
    public void sendMessage(String message){
        rabbitTemplate.convertAndSend(BiMqConstant.BI_EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY, message);
    }
}
