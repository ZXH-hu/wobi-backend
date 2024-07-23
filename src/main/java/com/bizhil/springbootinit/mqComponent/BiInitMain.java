package com.bizhil.springbootinit.mqComponent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * 创建BI direct类型交换机和队列、路由
 */
public class BiInitMain {
    public static void main(String[] args) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("47.115.230.253");
            factory.setPort(5672);
            factory.setUsername("admin");
            factory.setPassword("admin");
            factory.setVirtualHost("/");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            String EXCHANGE_NAME = BiMqConstant.BI_EXCHANGE_NAME;
            channel.exchangeDeclare(EXCHANGE_NAME, "direct",true);
            String queueName = BiMqConstant.BI_QUEUE_NAME;
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY);
        } catch (Exception e){
        }
    }
}
