package com.example.scheduler.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * The Kafka topics THIS replica consumes, derived from app.worker.tags.
 * Referenced from JobExecutionConsumer via SpEL: "#{@workerTopics.topics()}".
 */
@Component("workerTopics")
public class WorkerTopics {

    private final String[] topics;

    public WorkerTopics(@Value("${app.kafka.topic.job-execution}") String baseTopic,
                        @Value("${app.worker.tags:default}") String tags) {
        String[] resolved = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(t -> WorkerTagUtil.topicFor(baseTopic, t))
                .distinct()
                .toArray(String[]::new);
        this.topics = resolved.length == 0 ? new String[]{WorkerTagUtil.topicFor(baseTopic, "default")} : resolved;
    }

    public String[] topics() {
        return topics.clone();
    }
}
