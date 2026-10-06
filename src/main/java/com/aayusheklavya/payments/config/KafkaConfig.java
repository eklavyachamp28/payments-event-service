package com.aayusheklavya.payments.config;

import com.aayusheklavya.payments.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentsInitiatedTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_INITIATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsSettledTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_SETTLED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsInitiatedDltTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_INITIATED_DLT).partitions(3).replicas(1).build();
    }

    /**
     * Retry with exponential backoff (200ms, 400ms, 800ms, ...) up to ~5s, then park the record on
     * the dead-letter topic so a poison message never blocks the partition.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(200L, 2.0);
        backOff.setMaxElapsedTime(5_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(IllegalStateException.class);
        return handler;
    }
}
