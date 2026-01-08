package com.datacrowd.payments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CheckoutRequest {

    @NotNull
    private UUID projectId;

    /**
     * Если не передать — возьмём значение из PAYMENTS_AMOUNT_CENTS.
     */
    @Min(1)
    private Integer amountCents;

    /**
     * Сколько квоты выдать за оплату.
     * Если не передать — возьмём значение из PAYMENTS_TASK_QUOTA.
     */
    @Min(1)
    private Integer taskQuota;

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public Integer getTaskQuota() { return taskQuota; }
    public void setTaskQuota(Integer taskQuota) { this.taskQuota = taskQuota; }
}
