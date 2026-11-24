package com.example.scheduler.kafka;

/**
 * Worker tags ("default", "heavy", "eu-region", ...) route a job to a dedicated
 * agent pool the way a Jenkins node label does - except there is no agent to
 * install, just a Kafka topic per tag. A replica serves the tags listed in
 * app.worker.tags, so adding capacity for one pool means running more replicas
 * with that tag.
 */
final class WorkerTagUtil {

    private WorkerTagUtil() {
    }

    static String topicFor(String baseTopic, String tag) {
        String safeTag = (tag == null || tag.isBlank() ? "default" : tag.trim())
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]", "-");
        return baseTopic + "." + safeTag;
    }
}
