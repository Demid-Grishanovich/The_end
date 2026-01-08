package com.datacrowd.core.dto.billing;

public class BillingGrantRequest {
    private int taskQuotaDelta;
    private String billingStatus;

    public int getTaskQuotaDelta() { return taskQuotaDelta; }
    public void setTaskQuotaDelta(int taskQuotaDelta) { this.taskQuotaDelta = taskQuotaDelta; }

    public String getBillingStatus() { return billingStatus; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
}
