package com.aimall.product;

import com.aimall.common.event.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
    public Queue stockDeductQueue() {
        return new Queue(MqConstants.Q_STOCK_DEDUCT, true);
    }

    @Bean
    public Queue stockReleaseQueue() {
        return new Queue(MqConstants.Q_STOCK_RELEASE, true);
    }

    @Bean
    public Binding stockDeductBinding(Queue stockDeductQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(stockDeductQueue).to(orderExchange).with(MqConstants.RK_ORDER_CREATED);
    }

    @Bean
    public Binding stockReleaseBinding(Queue stockReleaseQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(stockReleaseQueue).to(orderExchange).with(MqConstants.RK_ORDER_RELEASE);
    }
}
