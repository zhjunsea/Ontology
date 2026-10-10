package com.ocean.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

public class RabbitMqHandler {

    private final CachingConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitMqHandler() {
        connectionFactory = new CachingConnectionFactory("localhost");
        connectionFactory.setPort(5672);
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        objectMapper = new ObjectMapper();
    }


    public RabbitTemplate getRabbitTemplate() {
        return rabbitTemplate;
    }

    /**
     * 发送消息到指定交换机和路由键
     * 内部自动将 payload 序列化为 JSON，并设置正确的 content_type
     */
    public void send(String exchange, String routingKey, Object payload) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());
            Message message = new Message(jsonBytes, props);
            rabbitTemplate.send(exchange, routingKey, message);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 消息序列化或发送失败", e);
        }
    }

    /**
     * 获取共享的 ObjectMapper（供测试断言时复用，保证序列化行为一致）
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
}
