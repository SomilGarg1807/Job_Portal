package com.somil.jobportal.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "job_seeker_profile")
public class JobSeekerProfile extends AuditableEntity {

    @Id
    private Integer userAccountId;

    @OneToOne
    @JoinColumn(name = "user_account_id")
    @MapsId
    private Users userId;

    private String firstName;
    private String lastName;
    private String city;
    private String state;
    private String country;
    private String workAuthorization;
    private String employmentType;
    private String desiredJobTitle;
    private String currentCompany;
    private String remotePreference;
    private String preferredJobCity;
    private String preferredJobState;
    private String preferredJobCountry;
    private Boolean willingToRelocate;

    @Column(length = 1000)
    private String preferredLocations;

    @Column(precision = 4, scale = 1)
    private BigDecimal totalExperienceYears;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentCtc;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedCtc;

    private String compensationCurrency;
    private Integer noticePeriodDays;
    private String phoneNumber;
    private String linkedInUrl;
    private String portfolioUrl;

    @Column(length = 300)
    private String professionalHeadline;

    private String resume;

    @Column(nullable = true, length = 64)
    private String profilePhoto;

    @OneToMany(targetEntity = Skills.class, cascade = CascadeType.ALL, mappedBy = "jobSeekerProfile", orphanRemoval = true)
    private List<Skills> skills;

    public JobSeekerProfile() {
    }

    public JobSeekerProfile(Users userId) {
        this.userId = userId;
    }

    public JobSeekerProfile(Integer userAccountId, Users userId, String firstName, String lastName, String city, String state, String country, String workAuthorization, String employmentType, String resume, String profilePhoto, List<Skills> skills) {
        this.userAccountId = userAccountId;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.workAuthorization = workAuthorization;
        this.employmentType = employmentType;
        this.resume = resume;
        this.profilePhoto = profilePhoto;
        this.skills = skills;
    }

    public Integer getUserAccountId() {
        return userAccountId;
    }

    public void setUserAccountId(Integer userAccountId) {
        this.userAccountId = userAccountId;
    }

    public Users getUserId() {
        return userId;
    }

    public void setUserId(Users userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getWorkAuthorization() {
        return workAuthorization;
    }

    public void setWorkAuthorization(String workAuthorization) {
        this.workAuthorization = workAuthorization;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getDesiredJobTitle() {
        return desiredJobTitle;
    }

    public void setDesiredJobTitle(String desiredJobTitle) {
        this.desiredJobTitle = desiredJobTitle;
    }

    public String getCurrentCompany() {
        return currentCompany;
    }

    public void setCurrentCompany(String currentCompany) {
        this.currentCompany = currentCompany;
    }

    public String getRemotePreference() {
        return remotePreference;
    }

    public void setRemotePreference(String remotePreference) {
        this.remotePreference = remotePreference;
    }

    public String getPreferredJobCity() {
        return preferredJobCity;
    }

    public void setPreferredJobCity(String preferredJobCity) {
        this.preferredJobCity = preferredJobCity;
    }

    public String getPreferredJobState() {
        return preferredJobState;
    }

    public void setPreferredJobState(String preferredJobState) {
        this.preferredJobState = preferredJobState;
    }

    public String getPreferredJobCountry() {
        return preferredJobCountry;
    }

    public void setPreferredJobCountry(String preferredJobCountry) {
        this.preferredJobCountry = preferredJobCountry;
    }

    public Boolean getWillingToRelocate() {
        return willingToRelocate;
    }

    public void setWillingToRelocate(Boolean willingToRelocate) {
        this.willingToRelocate = willingToRelocate;
    }

    public String getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(String preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public BigDecimal getTotalExperienceYears() {
        return totalExperienceYears;
    }

    public void setTotalExperienceYears(BigDecimal totalExperienceYears) {
        this.totalExperienceYears = totalExperienceYears;
    }

    public BigDecimal getCurrentCtc() {
        return currentCtc;
    }

    public void setCurrentCtc(BigDecimal currentCtc) {
        this.currentCtc = currentCtc;
    }

    public BigDecimal getExpectedCtc() {
        return expectedCtc;
    }

    public void setExpectedCtc(BigDecimal expectedCtc) {
        this.expectedCtc = expectedCtc;
    }

    public String getCompensationCurrency() {
        return compensationCurrency;
    }

    public void setCompensationCurrency(String compensationCurrency) {
        this.compensationCurrency = compensationCurrency;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getLinkedInUrl() {
        return linkedInUrl;
    }

    public void setLinkedInUrl(String linkedInUrl) {
        this.linkedInUrl = linkedInUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getProfessionalHeadline() {
        return professionalHeadline;
    }

    public void setProfessionalHeadline(String professionalHeadline) {
        this.professionalHeadline = professionalHeadline;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    public List<Skills> getSkills() {
        return skills;
    }

    public void setSkills(List<Skills> skills) {
        this.skills = skills;
    }

    @Transient
    public String getPhotosImagePath() {
        if (profilePhoto == null || userAccountId == null) return null;
        return "photos/candidate/" + userAccountId + "/" + profilePhoto;
    }

    @Override
    public String toString() {
        return "JobSeekerProfile{" +
                "userAccountId=" + userAccountId +
                ", userId=" + userId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", country='" + country + '\'' +
                ", workAuthorization='" + workAuthorization + '\'' +
                ", employmentType='" + employmentType + '\'' +
                ", resume='" + resume + '\'' +
                ", profilePhoto='" + profilePhoto + '\'' +
                '}';
    }
}
