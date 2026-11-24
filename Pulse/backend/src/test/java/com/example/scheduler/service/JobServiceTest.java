package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.model.dto.JobRequest;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void recurringJobWithoutCronExpressionIsRejected() {
        JobRequest request = new JobRequest();
        request.setName("no-cron-job");
        request.setType(JobType.RECURRING);
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(request));
    }

    @Test
    void recurringJobWithInvalidCronExpressionIsRejected() {
        JobRequest request = new JobRequest();
        request.setName("bad-cron-job");
        request.setType(JobType.RECURRING);
        request.setCronExpression("not a cron expression");
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(request));
    }

    @Test
    void oneTimeJobWithPastRunAtIsRejected() {
        JobRequest request = new JobRequest();
        request.setName("past-job");
        request.setType(JobType.ONE_TIME);
        request.setRunAt(Instant.now().minus(1, ChronoUnit.HOURS));
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(request));
    }

    @Test
    void oneTimeJobWithoutRunAtIsRejected() {
        JobRequest request = new JobRequest();
        request.setName("no-runAt-job");
        request.setType(JobType.ONE_TIME);
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(request));
    }

    @Test
    void validRecurringJobIsSavedWithComputedNextRun() {
        JobRequest request = new JobRequest();
        request.setName("every-minute-job");
        request.setType(JobType.RECURRING);
        request.setCronExpression("0 * * * * *"); // every minute
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jobService.createJob(request);

        assertNotNull(response.getNextRunAt());
        assertTrue(response.getNextRunAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    void validOneTimeJobUsesRunAtAsNextRun() {
        Instant future = Instant.now().plus(2, ChronoUnit.HOURS);
        JobRequest request = new JobRequest();
        request.setName("future-job");
        request.setType(JobType.ONE_TIME);
        request.setRunAt(future);
        request.setWebhookUrl("https://example.com/hook");
        request.setHttpMethod("POST");

        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jobService.createJob(request);

        assertEquals(future, response.getNextRunAt());
    }
}
