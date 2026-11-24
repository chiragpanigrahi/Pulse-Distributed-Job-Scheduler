package com.example.scheduler.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

/**
 * A short, human-readable identifier for this process, e.g. "job-scheduler-a1b2c3@10.0.1.4".
 * Surfaced on every JobExecution row so the dashboard can show which node in
 * the cluster actually ran a given job - the visible proof this system is
 * genuinely distributed rather than single-node.
 */
@Component
public class InstanceIdProvider {

    private final String instanceId;

    public InstanceIdProvider() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        this.instanceId = host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String get() {
        return instanceId;
    }
}
