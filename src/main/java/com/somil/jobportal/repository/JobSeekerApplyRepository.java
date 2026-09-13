package com.somil.jobportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerApply;
import com.somil.jobportal.entity.JobSeekerProfile;

@Repository
public interface JobSeekerApplyRepository extends JpaRepository<JobSeekerApply, Integer>{

	List<JobSeekerApply> findByUserId(JobSeekerProfile userId);
	
	List<JobSeekerApply> findByJob(JobPostActivity job);

    java.util.Optional<JobSeekerApply> findByUserIdAndJob(JobSeekerProfile user, JobPostActivity job);
	

    org.springframework.data.domain.Page<JobSeekerApply> findByUserIdOrderByApplyDateDescIdDesc(
            JobSeekerProfile user, org.springframework.data.domain.Pageable pageable);

    boolean existsByUserIdUserAccountIdAndJobPostedByIdUserId(Integer candidateId, Integer recruiterId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from JobSeekerApply a where a.id = :id")
    java.util.Optional<JobSeekerApply> findForUpdate(Integer id);

    @org.springframework.data.jpa.repository.Query("""
        select a from JobSeekerApply a where a.job.postedById.userId = :owner
        and (:jobId = 0 or a.job.jobPostId = :jobId)
        and (:status is null or a.status = :status or
            (:status = com.somil.jobportal.entity.ApplicationStatus.APPLIED and a.status is null))
        and (:minimum is null or a.userId.totalExperienceYears >= :minimum)
        and (:maximum is null or a.userId.totalExperienceYears <= :maximum)
        and (:keyword = '' or lower(concat(coalesce(a.userId.firstName, ''), ' ', coalesce(a.userId.lastName, ''))) like :pattern
            or exists (select s.id from Skills s where s.jobSeekerProfile = a.userId and lower(s.name) like :pattern))
        order by a.applyDate desc, a.id desc
        """)
    org.springframework.data.domain.Page<JobSeekerApply> searchBoard(Integer owner, int jobId,
        com.somil.jobportal.entity.ApplicationStatus status, java.math.BigDecimal minimum,
        java.math.BigDecimal maximum, String keyword, String pattern, org.springframework.data.domain.Pageable pageable);
}
