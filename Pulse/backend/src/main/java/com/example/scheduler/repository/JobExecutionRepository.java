package com.example.scheduler.repository;

import com.example.scheduler.model.JobExecution;
import com.example.scheduler.model.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    Page<JobExecution> findByJobIdOrderByCreatedAtDesc(UUID jobId, Pageable pageable);

    List<JobExecution> findTop20ByOrderByCreatedAtDesc();

    long countByStatus(ExecutionStatus status);

    long countByStatusAndCreatedAtAfter(ExecutionStatus status, Instant after);

    long countByCreatedAtAfter(Instant after);

    @Query("SELECT e FROM JobExecution e WHERE e.createdAt >= :since ORDER BY e.createdAt ASC")
    List<JobExecution> findSince(@Param("since") Instant since);

    List<JobExecution> findByPipelineRunIdOrderByStageIndexAsc(UUID pipelineRunId);

    /**
     * Row-level lock used when approving a gate or completing a callback, so two
     * concurrent requests (a retrying external system, a double-click, two
     * sweeper replicas) can never both act on the same execution.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM JobExecution e WHERE e.id = :id")
    java.util.Optional<JobExecution> findByIdForUpdate(@Param("id") UUID id);

    List<JobExecution> findByStatusOrderByCreatedAtDesc(ExecutionStatus status);

    Page<JobExecution> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JobExecution> findByStatusOrderByCreatedAtDesc(ExecutionStatus status, Pageable pageable);

    List<JobExecution> findByStatusAndCallbackDeadlineBefore(ExecutionStatus status, Instant deadline);
}
