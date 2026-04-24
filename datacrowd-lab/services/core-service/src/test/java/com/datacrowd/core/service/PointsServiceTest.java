package com.datacrowd.core.service;

import com.datacrowd.core.repo.PointsLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.datacrowd.core.entity.PointsLedgerEntity;

@ExtendWith(MockitoExtension.class)
class PointsServiceTest {

    @Mock PointsLedgerRepository pointsLedgerRepository;

    @InjectMocks PointsService pointsService;

    @Test
    void awardTaskApprovedOnce_savesPoints_whenNotAlreadyAwarded() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(pointsLedgerRepository.existsByUserIdAndTaskIdAndReason(
                userId, taskId, PointsService.REASON_TASK_APPROVED))
                .thenReturn(false);

        int awarded = pointsService.awardTaskApprovedOnce(userId, taskId, 10);

        assertThat(awarded).isEqualTo(10);

        ArgumentCaptor<PointsLedgerEntity> captor =
                ArgumentCaptor.forClass(PointsLedgerEntity.class);
        verify(pointsLedgerRepository).save(captor.capture());

        PointsLedgerEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTaskId()).isEqualTo(taskId);
        assertThat(saved.getPoints()).isEqualTo(10);
        assertThat(saved.getReason()).isEqualTo(PointsService.REASON_TASK_APPROVED);
    }

    @Test
    void awardTaskApprovedOnce_returnsZero_whenAlreadyAwarded() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(pointsLedgerRepository.existsByUserIdAndTaskIdAndReason(
                userId, taskId, PointsService.REASON_TASK_APPROVED))
                .thenReturn(true);

        int awarded = pointsService.awardTaskApprovedOnce(userId, taskId, 10);

        assertThat(awarded).isEqualTo(0);
        verify(pointsLedgerRepository, never()).save(any());
    }

    @Test
    void awardTaskApprovedOnce_returnsZero_whenPointsIsZero() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        int awarded = pointsService.awardTaskApprovedOnce(userId, taskId, 0);

        assertThat(awarded).isEqualTo(0);
        verify(pointsLedgerRepository, never()).existsByUserIdAndTaskIdAndReason(any(), any(), any());
        verify(pointsLedgerRepository, never()).save(any());
    }

    @Test
    void awardTaskApprovedOnce_returnsZero_whenPointsIsNegative() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        int awarded = pointsService.awardTaskApprovedOnce(userId, taskId, -5);

        assertThat(awarded).isEqualTo(0);
        verify(pointsLedgerRepository, never()).save(any());
    }
}