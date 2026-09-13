package com.somil.jobportal.entity;

public enum ApplicationStatus {
    APPLIED("Applied"), SHORTLISTED("Shortlisted"), INTERVIEW("Interview"), OFFERED("Offered"), REJECTED("Rejected");
    private final String label;
    ApplicationStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
