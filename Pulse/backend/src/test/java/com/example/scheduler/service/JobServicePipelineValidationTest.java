package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.model.dto.JobRequest;
import com.example.scheduler.model.dto.JobStage;
import com.example.scheduler.model.enums.ExecutionMode;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class JobServicePipelineValidationTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private JobRequest pipeline(List<JobStage> stages) {
        JobRequest r = new JobRequest();
        r.setName("p");
        r.setType(JobType.RECURRING);
        r.setCronExpression("0 */5 * * * *");
        r.setExecutionMode(ExecutionMode.PIPELINE);
        r.setStages(stages);
        return r;
    }

    private JobStage stage(String name) {
        return JobStage.builder().name(name).webhookUrl("https://example.com").build();
    }

    @Test
    void pipelineWithoutStagesIsRejected() {
        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(pipeline(List.of())));
    }

    @Test
    void duplicateStageNamesAreRejected() {
        assertThrows(InvalidJobConfigException.class,
                () -> jobService.createJob(pipeline(List.of(stage("a"), stage("a")))));
    }

    @Test
    void stageNameWithDotIsRejected() {
        assertThrows(InvalidJobConfigException.class,
                () -> jobService.createJob(pipeline(List.of(stage("a.b")))));
    }

    @Test
    void callbackStageNeedsTimeout() {
        JobStage s = stage("wait");
        s.setAwaitCallback(true);
        assertThrows(InvalidJobConfigException.class, () -> jobService.createJob(pipeline(List.of(s))));
    }
}
