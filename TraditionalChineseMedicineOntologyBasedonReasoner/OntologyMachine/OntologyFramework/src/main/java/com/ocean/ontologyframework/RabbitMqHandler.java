package com.ocean.ontologyframework;

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
        this("localhost", 5672, "guest", "guest");
    }

    public RabbitMqHandler(String host, int port, String username, String password) {
        com.rabbitmq.client.ConnectionFactory nativeFactory = new com.rabbitmq.client.ConnectionFactory();
        nativeFactory.setHost(host);
        nativeFactory.setPort(port);
        nativeFactory.setUsername(username);
        nativeFactory.setPassword(password);
        nativeFactory.setAutomaticRecoveryEnabled(true);

        this.connectionFactory = new CachingConnectionFactory(nativeFactory);
        this.rabbitTemplate = new RabbitTemplate(this.connectionFactory);
        // 不再设置任何 MessageConverter，使用默认的 SimpleMessageConverter
        // 我们在 send() 中手动完成 JSON 序列化
        this.objectMapper = new ObjectMapper();
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