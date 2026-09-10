package com.somil.jobportal.services;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerApply;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.repository.JobSeekerApplyRepository;

@Service
public class JobSeekerApplyService {

    private final JobSeekerApplyRepository jobSeekerApplyRepository;

    @Autowired
    public JobSeekerApplyService(JobSeekerApplyRepository jobSeekerApplyRepository) {
        this.jobSeekerApplyRepository = jobSeekerApplyRepository;
    }

    public List<JobSeekerApply> getCandidatesJobs(JobSeekerProfile userAccountId) {
        return jobSeekerApplyRepository.findByUserId(userAccountId);
    }

    public List<JobSeekerApply> getJobCandidates(JobPostActivity job) {
        return jobSeekerApplyRepository.findByJob(job);
    }

    public void addNew(JobSeekerApply entry) {
        if (jobSeekerApplyRepository.findByUserIdAndJob(entry.getUserId(), entry.getJob()).isPresent()) return;
        try {
            jobSeekerApplyRepository.saveAndFlush(entry);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            // A concurrent request may already have inserted this user's record.
            if (jobSeekerApplyRepository.findByUserIdAndJob(entry.getUserId(), entry.getJob()).isEmpty()) throw exception;
        }
    }
}
