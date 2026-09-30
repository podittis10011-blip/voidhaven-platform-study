package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 手动触发事务同步回调；不代表真实事务已回滚或 MinIO 对象已删除。 */
class MediaRollbackDebugProbeTest {
    final ContentItem item = new ContentItem("01JCONTENT0000000000000000", ContentKind.COMPETITION,
            WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE);
    final ContentItemRepository items = mock(ContentItemRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final MediaStorage storage = mock(MediaStorage.class);
    final MediaValidator validator = mock(MediaValidator.class);
    final AuditService audit = mock(AuditService.class);
    final MediaService service = new MediaService(items, mock(PartnerProfileRepository.class), jdbc, storage, validator, audit);

    MediaRollbackDebugProbeTest() {
        item.setCurrentRevision(new ContentRevision("01JREVISION000000000000000", item, 1, "合成草稿", "导读", "{}",
                "zh-CN", null, "https://contest.example.edu", "来源", "已授权", "合成作者", "NOT_ALLOWED", "NOT_ALLOWED", "{}", "synthetic-admin"));
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.queryForObject(startsWith("select count"), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbc.queryForObject(startsWith("select coalesce"), eq(Long.class), anyString())).thenReturn(0L);
        when(validator.validate(any())).thenReturn(new MediaValidator.ValidatedImage(new byte[]{1,2,3}, "image/png", 2, 3, "png"));
    }
    void upload() { service.upload(item.getId(), "合成图片", new byte[]{9}, "synthetic-admin", "ADMIN", true, "r"); }

    @Test void jdbc_failure_without_synchronization_deletes_immediately() {
        when(jdbc.update(startsWith("insert into media_assets"), any(Object[].class)))
                .thenThrow(new IllegalStateException("synthetic-jdbc-failure"));
        assertThatThrownBy(this::upload).hasMessage("synthetic-jdbc-failure");
        var order = inOrder(storage);
        order.verify(storage).put(anyString(), eq("image/png"), any(byte[].class));
        order.verify(storage).delete(anyString());
        verifyNoInteractions(audit);
    }
    @Test void rollback_callback_deletes_uploaded_object() {
        complete(TransactionSynchronization.STATUS_ROLLED_BACK, true);
    }
    @Test void commit_callback_keeps_uploaded_object() {
        complete(TransactionSynchronization.STATUS_COMMITTED, false);
    }
    private void complete(int status, boolean deleted) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            upload();
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            verify(storage, never()).delete(anyString());
            callbacks.get(0).afterCompletion(status); // BP: F7 进入 MediaService 的 afterCompletion
            verify(storage, times(deleted ? 1 : 0)).delete(anyString());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
