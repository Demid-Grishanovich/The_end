package com.datacrowd.core.service;

import com.datacrowd.core.dto.WorkerStatsResponse;
import com.datacrowd.core.entity.AnswerStatus;
import com.datacrowd.core.entity.WorkerProfileEntity;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.PointsLedgerRepository;
import com.datacrowd.core.repo.WorkerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkerStatsServiceTest {

    @Mock WorkerProfileRepository workerProfileRepository;
    @Mock AnswerRepository        answerRepository;
    @Mock PointsLedgerRepository  pointsLedgerRepository;

    @InjectMocks WorkerStatsService workerStatsService;

    @Test
    void getStats_returnsDefaults_whenWorkerHasNoProfile() {
        UUID workerId = UUID.randomUUID();

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.empty());
        when(answerRepository.countByUserIdAndStatus(eq(workerId), any())).thenReturn(0L);
        when(pointsLedgerRepository.sumPointsByUserId(workerId)).thenReturn(0);

        WorkerStatsResponse stats = workerStatsService.getStats(workerId);

        assertThat(stats.trustScore).isEqualTo(100);
        assertThat(stats.completedTasks).isEqualTo(0);
        assertThat(stats.rejectedTasks).isEqualTo(0);
        assertThat(stats.totalPoints).isEqualTo(0);
        assertThat(stats.trustLevel).isEqualTo("HIGH");
    }

    @Test
    void getStats_returnsCorrectValues_whenWorkerExists() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(75);

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));
        when(answerRepository.countByUserIdAndStatus(workerId, AnswerStatus.APPROVED)).thenReturn(42L);
        when(answerRepository.countByUserIdAndStatus(workerId, AnswerStatus.REJECTED)).thenReturn(3L);
        when(pointsLedgerRepository.sumPointsByUserId(workerId)).thenReturn(420);

        WorkerStatsResponse stats = workerStatsService.getStats(workerId);

        assertThat(stats.trustScore).isEqualTo(75);
        assertThat(stats.completedTasks).isEqualTo(42);
        assertThat(stats.rejectedTasks).isEqualTo(3);
        assertThat(stats.totalPoints).isEqualTo(420);
        assertThat(stats.trustLevel).isEqualTo("MEDIUM");
    }

    @Test
    void getStats_trustLevel_isHIGH_whenScoreAbove80() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(90);

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));
        when(answerRepository.countByUserIdAndStatus(eq(workerId), any())).thenReturn(0L);
        when(pointsLedgerRepository.sumPointsByUserId(workerId)).thenReturn(0);

        WorkerStatsResponse stats = workerStatsService.getStats(workerId);

        assertThat(stats.trustLevel).isEqualTo("HIGH");
    }

    @Test
    void getStats_trustLevel_isBLOCKED_whenScoreBelow30() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(20);

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));
        when(answerRepository.countByUserIdAndStatus(eq(workerId), any())).thenReturn(0L);
        when(pointsLedgerRepository.sumPointsByUserId(workerId)).thenReturn(0);

        WorkerStatsResponse stats = workerStatsService.getStats(workerId);

        assertThat(stats.trustLevel).isEqualTo("BLOCKED");
    }

    @Test
    void getStats_trustLevel_isLOW_whenScoreBetween30and60() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(45);

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));
        when(answerRepository.countByUserIdAndStatus(eq(workerId), any())).thenReturn(0L);
        when(pointsLedgerRepository.sumPointsByUserId(workerId)).thenReturn(0);

        WorkerStatsResponse stats = workerStatsService.getStats(workerId);

        assertThat(stats.trustLevel).isEqualTo("LOW");
    }
}