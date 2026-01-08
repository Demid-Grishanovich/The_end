package com.datacrowd.payments.dto;

public class BillingGrantRequest {
    private int taskQuotaDelta;
    private String billingStatus;

    public BillingGrantRequest() {}

    public BillingGrantRequest(int taskQuotaDelta, String billingStatus) {
        this.taskQuotaDelta = taskQuotaDelta;
        this.billingStatus = billingStatus;
    }

    public int getTaskQuotaDelta() { return taskQuotaDelta; }
    public void setTaskQuotaDelta(int taskQuotaDelta) { this.taskQuotaDelta = taskQuotaDelta; }

    public String getBillingStatus() { return billingStatus; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
}
