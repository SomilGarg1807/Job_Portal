package com.somil.jobportal.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
public class ApplicationStatusEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(optional = false) @JoinColumn(name = "application_id")
    private JobSeekerApply application;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private ApplicationStatus status;
    @Temporal(TemporalType.TIMESTAMP) @Column(nullable = false)
    private Date occurredAt;
    protected ApplicationStatusEvent() {}
    public ApplicationStatusEvent(JobSeekerApply application, ApplicationStatus status, Date occurredAt) {
        this.application = application; this.status = status; this.occurredAt = occurredAt;
    }
    public ApplicationStatus getStatus() { return status; }
    public Date getOccurredAt() { return occurredAt; }
}
