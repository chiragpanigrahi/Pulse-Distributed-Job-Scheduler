package com.example.scheduler.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.job-execution}")
    private String jobExecutionTopic;

    @Value("${app.kafka.topic.job-execution-dlt}")
    private String jobExecutionDlt;

    // Every tag that should have a topic provisioned up front. A job using a tag
    // not listed here still works if the broker allows auto topic creation (the
    // docker-compose default); listing it just avoids relying on that.
    @Value("${app.worker.known-tags:default}")
    private String knownTags;

    /**
     * One topic per worker tag. Multiple partitions is what actually lets several
     * worker instances (same consumer group, same tag) execute jobs in parallel
     * instead of serially.
     */
    @Bean
    public KafkaAdmin.NewTopics workerTagTopicProvisioner() {
        Set<String> tags = new LinkedHashSet<>(Arrays.asList(knownTags.split(",")));
        tags = tags.stream()
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        tags.removeIf(String::isBlank);
        if (tags.isEmpty()) {
            tags.add("default");
        }
        NewTopic[] topics = tags.stream()
                .map(tag -> TopicBuilder.name(WorkerTagUtil.topicFor(jobExecutionTopic, tag))
                        .partitions(6)
                        .replicas(1)
                        .build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }

    @Bean
    public NewTopic jobExecutionDeadLetterTopic() {
        return TopicBuilder.name(jobExecutionDlt)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
