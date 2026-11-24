package com.example.scheduler.redis;

import com.example.scheduler.model.dto.JobExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listens on the shared Redis channel and re-broadcasts each update over
 * this instance's own WebSocket/STOMP connections. Every backend replica
 * runs one of these, so a job executed by instance A is still visible in
 * real time to a browser connected to instance B.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JobExecutionResponse execution = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), JobExecutionResponse.class);
            messagingTemplate.convertAndSend("/topic/jobs", execution);
        } catch (Exception e) {
            log.warn("Failed to relay job status update to WebSocket clients: {}", e.getMessage());
        }
    }
}
