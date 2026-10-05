package com.planet.filemanagerapi.integration.support;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Declares the export exchange/queue/binding that, in real environments, is provisioned
 * by the sibling file-processor-worker service rather than this application.
 */
@TestConfiguration
public class TestExportRabbitTopologyConfig {

    @Bean
    public TopicExchange exportExchange(@Value("${rabbitmq.exchange.export}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue exportQueue(@Value("${rabbitmq.queue.export}") String queueName) {
        return new Queue(queueName);
    }

    @Bean
    public Binding exportBinding(Queue exportQueue,
                                  TopicExchange exportExchange,
                                  @Value("${rabbitmq.routing-key.export}") String routingKey) {
        return BindingBuilder.bind(exportQueue).to(exportExchange).with(routingKey);
    }
}
