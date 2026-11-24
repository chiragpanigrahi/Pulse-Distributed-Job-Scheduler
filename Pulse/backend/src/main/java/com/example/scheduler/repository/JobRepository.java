package com.example.scheduler.repository;

import com.example.scheduler.model.Job;
import com.example.scheduler.model.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByNameContainingIgnoreCase(String name, Pageable pageable);

    long countByStatus(JobStatus status);

    /**
     * Jobs that are due to run right now. Used by the poller. Kept as a plain
     * query (rather than derived method) so it's easy to see exactly what
     * "due" means: SCHEDULED and nextRunAt has passed.
     */
    @Query("SELECT j FROM Job j WHERE j.status = com.example.scheduler.model.enums.JobStatus.SCHEDULED " +
           "AND j.nextRunAt <= :now ORDER BY j.nextRunAt ASC")
    List<Job> findDueJobs(@Param("now") Instant now);
}
