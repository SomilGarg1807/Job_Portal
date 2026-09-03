package com.somil.jobportal.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.somil.jobportal.entity.IRecruiterJobs;
import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.RecruiterJobsDto;
import com.somil.jobportal.repository.JobPostActivityRepository;

@Service
public class JobPostActivityService {

    private final JobPostActivityRepository jobPostActivityRepository;

    public JobPostActivityService(JobPostActivityRepository jobPostActivityRepository) {
        this.jobPostActivityRepository = jobPostActivityRepository;
    }

    public JobPostActivity addNew(JobPostActivity jobPostActivity) {
        return jobPostActivityRepository.save(jobPostActivity);
    }

    public List<RecruiterJobsDto> getRecruiterJobs(int recruiter) {

        List<IRecruiterJobs> recruiterJobsDtos = jobPostActivityRepository.getRecruiterJobs(recruiter);

        List<RecruiterJobsDto> recruiterJobsDtoList = new ArrayList<>();

        for (IRecruiterJobs rec : recruiterJobsDtos) {
            JobLocation loc = new JobLocation(rec.getLocationId(), rec.getCity(), rec.getState(), rec.getCountry());
            JobCompany comp = new JobCompany(rec.getCompanyId(), rec.getName(), "");
            recruiterJobsDtoList.add(new RecruiterJobsDto(rec.getTotalCandidates(), rec.getJob_post_id(),
                    rec.getJob_title(), loc, comp));
        }
        return recruiterJobsDtoList;

    }

    public JobPostActivity getOne(int id) {
        return jobPostActivityRepository.findById(id).orElseThrow(()->new RuntimeException("Job not found"));
    }

    public List<JobPostActivity> getAll() {
        return jobPostActivityRepository.findAll();
    }

    public List<JobPostActivity> search(String job, String location, List<String> type, List<String> remote,
                                        LocalDate searchDate, boolean typeFilter, boolean remoteFilter) {
        String normalizedJob = Objects.toString(job, "").trim();
        String normalizedLocation = Objects.toString(location, "").trim();
        List<String> normalizedType = normalizeFilters(type);
        List<String> normalizedRemote = normalizeFilters(remote);
        int typeFilterFlag = typeFilter ? 1 : 0;
        int remoteFilterFlag = remoteFilter ? 1 : 0;
        return Objects.isNull(searchDate) ? jobPostActivityRepository.searchWithoutDate(normalizedJob, normalizedLocation, normalizedRemote, normalizedType, remoteFilterFlag, typeFilterFlag) :
                jobPostActivityRepository.search(normalizedJob, normalizedLocation, normalizedRemote, normalizedType, searchDate, remoteFilterFlag, typeFilterFlag);
    }

    private List<String> normalizeFilters(List<String> filters) {
        return filters.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase())
                .collect(Collectors.toList());
    }
}
