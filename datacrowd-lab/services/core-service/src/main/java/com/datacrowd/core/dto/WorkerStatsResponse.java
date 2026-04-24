package com.datacrowd.core.dto;

public class WorkerStatsResponse {

    public Integer trustScore;       // текущий рейтинг (0-100)
    public Integer completedTasks;   // задачи со статусом APPROVED
    public Integer rejectedTasks;    // задачи со статусом REJECTED
    public Integer totalPoints;      // сумма всех начисленных очков
    public String  trustLevel;       // текстовый уровень: HIGH / MEDIUM / LOW / BLOCKED

    public WorkerStatsResponse(Integer trustScore,
                               Integer completedTasks,
                               Integer rejectedTasks,
                               Integer totalPoints) {
        this.trustScore     = trustScore;
        this.completedTasks = completedTasks;
        this.rejectedTasks  = rejectedTasks;
        this.totalPoints    = totalPoints;


        if (trustScore == null || trustScore < 30) {
            this.trustLevel = "BLOCKED";  // красный
        } else if (trustScore < 60) {
            this.trustLevel = "LOW";      // оранжевый
        } else if (trustScore < 80) {
            this.trustLevel = "MEDIUM";   // жёлтый
        } else {
            this.trustLevel = "HIGH";     // зелёный
        }
    }
}