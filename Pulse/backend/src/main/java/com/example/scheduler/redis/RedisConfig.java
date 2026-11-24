package com.example.scheduler.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis is used with plain strings everywhere (lock tokens, JSON text for the
 * status cache and pub/sub), via Spring Boot's auto-configured StringRedisTemplate.
 * JSON is produced by the application's own ObjectMapper so java.time values are
 * written as ISO-8601 - the same format the REST API and the frontend use.
 */
@Configuration
public class RedisConfig {

    public static final String JOB_STATUS_CHANNEL = "job-status-updates";

    @Bean
    public ChannelTopic jobStatusTopic() {
        return new ChannelTopic(JOB_STATUS_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            JobStatusRedisSubscriber subscriber,
            ChannelTopic jobStatusTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, jobStatusTopic);
        return container;
    }
}
