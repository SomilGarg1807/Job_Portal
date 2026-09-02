package com.somil.jobportal.services;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.JobSeekerSave;
import com.somil.jobportal.repository.JobSeekerSaveRepository;

@Service
public class JobSeekerSaveService {
	
	@Autowired
    private final JobSeekerSaveRepository jobSeekerSaveRepository;

    public JobSeekerSaveService(JobSeekerSaveRepository jobSeekerSaveRepository) {
        this.jobSeekerSaveRepository = jobSeekerSaveRepository;
    }

    public List<JobSeekerSave> getCandidatesJob(JobSeekerProfile userAccountId) {
        return jobSeekerSaveRepository.findByUserId(userAccountId);
    }

    public List<JobSeekerSave> getJobCandidates(JobPostActivity job) {
        return jobSeekerSaveRepository.findByJob(job);
    }
    
    public void addNew(JobSeekerSave jobSeekerSave) {
		
			jobSeekerSaveRepository.save(jobSeekerSave);
    	
	}
}
