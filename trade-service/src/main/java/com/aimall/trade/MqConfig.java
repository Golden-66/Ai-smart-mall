package com.aimall.trade;

import com.aimall.common.event.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
        mapper.setTrustedPackages(MqConstants.EVENT_PACKAGE);
        converter.setJavaTypeMapper(mapper);
        return converter;
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(MqConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue stockResultQueue() {
        return new Queue(MqConstants.Q_ORDER_STOCK_RESULT, true);
    }

    @Bean
    public Queue payQueue() {
        return new Queue(MqConstants.Q_ORDER_PAY, true);
    }

    @Bean
    public Queue timeoutQueue() {
        return new Queue(MqConstants.Q_ORDER_TIMEOUT, true);
    }

    /**
     * 延迟队列 (TTL + 死信交换机): 消息在此队列等待 TTL 到期后, 被路由到 timeoutQueue 触发超时关单。
     * 注意: 修改 TTL 后需要先在 RabbitMQ 中删除该队列, 否则声明参数不一致会报错。
     */
    @Bean
    public Queue timeoutDelayQueue(@Value("${mall.order.timeout-seconds:1800}") long timeoutSeconds) {
        return QueueBuilder.durable(MqConstants.Q_ORDER_TIMEOUT_DELAY)
                .ttl((int) (timeoutSeconds * 1000))
                .deadLetterExchange(MqConstants.EXCHANGE)
                .deadLetterRoutingKey(MqConstants.RK_ORDER_TIMEOUT)
                .build();
    }

    @Bean
    public Binding stockResultBinding(Queue stockResultQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(stockResultQueue).to(orderExchange).with(MqConstants.RK_STOCK_RESULT);
    }

    @Bean
    public Binding payBinding(Queue payQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(payQueue).to(orderExchange).with(MqConstants.RK_ORDER_PAY);
    }

    @Bean
    public Binding timeoutBinding(Queue timeoutQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(timeoutQueue).to(orderExchange).with(MqConstants.RK_ORDER_TIMEOUT);
    }
}
