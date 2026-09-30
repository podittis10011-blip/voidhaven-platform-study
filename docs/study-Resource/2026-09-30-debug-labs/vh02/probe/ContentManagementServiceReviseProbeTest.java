package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.ContentRevisionRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习用探针 C（不属于产品测试资产）：给 VH-02 的生命周期调试提供"驱动器"。
 *
 * 为什么需要它：旧包 VH-02 的测试地图把 `revise` 标成盲区——
 * "revise 在 PUBLISHED 时新建修订行" 与 "revise 在 DRAFT 时就地覆盖同一修订行"
 * 两条分支都没有自动化断言，只能靠手动复现推断。本探针把这条分叉变成可断点对象。
 *
 * 用法：拷到 apps/api/src/test/java/cn/voidhaven/api/service/ 下即可在 IDEA 里 Debug。
 * 数据边界：全部为合成 ULID、合成标题、合成来源域名。
 *
 * 四个调试入口：
 *   1. revising_a_published_item_creates_a_candidate_and_keeps_the_published_pointer
 *   2. revising_a_draft_updates_the_same_revision_row
 *   3. revise_rejects_a_stale_expected_version
 *   4. revise_rejects_a_non_editable_workflow_status
 */
class ContentManagementServiceReviseProbeTest {
    private static final String ITEM = "01JCONTENT0000000000000001";
    private static final String REV_1 = "01JREVISION0000000000000001";
    private static final String ACTOR = "01JADMIN000000000000000000";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // ---------- 1. 已发布内容：新建候选修订，公开指针不动 ----------

    @Test
    void revising_a_published_item_creates_a_candidate_and_keeps_the_published_pointer() {
        // 断点建议：本方法第一行；revise 入口；createCandidate 判断行；
        //          revisions.saveAndFlush 行；item.setCurrentRevision 行；audit.record 行；断言前。
        ContentItem item = publishedItem();
        ContentRevision published = item.getPublishedRevision();
        Fixture fixture = fixture(item);

        ContentManagementService.Managed managed = fixture.service.revise(
                ITEM, draft(), null, 1L, ACTOR, "ADMIN", "01JREQUEST0000000000000000");

        ArgumentCaptor<ContentRevision> saved = ArgumentCaptor.forClass(ContentRevision.class);
        verify(fixture.revisions).saveAndFlush(saved.capture());
        ContentRevision candidate = saved.getValue();

        assertThat(candidate.getId()).isNotEqualTo(REV_1);
        assertThat(candidate.getRevisionNumber()).isEqualTo(2);
        assertThat(item.getCurrentRevision()).isSameAs(candidate);
        // 关键断言：公开指针仍指向旧修订，候选修订没有直接公开
        assertThat(item.getPublishedRevision()).isSameAs(published);
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
        assertThat(managed.workflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
        verify(fixture.audit).record(eq(ACTOR), eq("ADMIN"), eq("CONTENT_REVISION_CREATED"), eq("CONTENT_ITEM"), eq(ITEM),
                eq("{\"revision\":1}"), eq("{\"revision\":2,\"workflowStatus\":\"DRAFT\"}"), any(), anyString());
    }

    // ---------- 2. 草稿内容：就地覆盖同一条修订行 ----------

    @Test
    void revising_a_draft_updates_the_same_revision_row() {
        ContentItem item = draftItem();
        Fixture fixture = fixture(item);

        fixture.service.revise(ITEM, draft(), null, 1L, ACTOR, "ADMIN", "01JREQUEST0000000000000000");

        ArgumentCaptor<ContentRevision> saved = ArgumentCaptor.forClass(ContentRevision.class);
        verify(fixture.revisions).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isSameAs(item.getCurrentRevision());
        assertThat(saved.getValue().getRevisionNumber()).isEqualTo(1);
        verify(fixture.audit).record(eq(ACTOR), eq("ADMIN"), eq("CONTENT_DRAFT_SAVED"), eq("CONTENT_ITEM"), eq(ITEM),
                eq("{\"revision\":1}"), eq("{\"revision\":1,\"workflowStatus\":\"DRAFT\"}"), any(), anyString());
        // 草稿没有公开指针
        assertThat(item.getPublishedRevision()).isNull();
    }

    // ---------- 3. 过期版本：在写任何东西之前就拒绝 ----------

    @Test
    void revise_rejects_a_stale_expected_version() {
        ContentItem item = draftItem();
        setVersion(item, 9L);
        Fixture fixture = fixture(item);

        assertThatThrownBy(() -> fixture.service.revise(
                ITEM, draft(), null, 8L, ACTOR, "ADMIN", "01JREQUEST0000000000000000"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_VERSION_CONFLICT");
        verify(fixture.revisions, never()).saveAndFlush(any());
        verify(fixture.items, never()).saveAndFlush(any());
    }

    // ---------- 4. 不可编辑状态：审核中的内容不能直接改 ----------

    @Test
    void revise_rejects_a_non_editable_workflow_status() {
        ContentItem item = draftItem();
        item.submit(); // DRAFT → PENDING_REVIEW
        Fixture fixture = fixture(item);

        assertThatThrownBy(() -> fixture.service.revise(
                ITEM, draft(), null, 1L, ACTOR, "ADMIN", "01JREQUEST0000000000000000"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_NOT_EDITABLE");
        verify(fixture.revisions, never()).saveAndFlush(any());
    }

    // ---------- 夹具 ----------

    private record Fixture(ContentManagementService service, ContentItemRepository items,
                           ContentRevisionRepository revisions, AuditService audit) { }

    private static Fixture fixture(ContentItem item) {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        AuditService audit = mock(AuditService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(items.findByIdForUpdate(eq(ITEM))).thenReturn(Optional.of(item));
        when(items.saveAndFlush(any(ContentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.saveAndFlush(any(ContentRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ContentManagementService service = new ContentManagementService(
                items, revisions, mock(PartnerProfileRepository.class), audit, JSON,
                new ContentSourcePolicy(false, "https://voidhaven.example"),
                new RichTextPolicy(), new ContentLanguagePolicy(), jdbc);
        return new Fixture(service, items, revisions, audit);
    }

    /** 已发布：currentRevision 与 publishedRevision 同一行，版本号 1。 */
    private static ContentItem publishedItem() {
        ContentItem item = draftItem();
        item.publish();
        return item;
    }

    /** 草稿：有当前修订、没有公开修订，版本号 1。 */
    private static ContentItem draftItem() {
        ContentItem item = new ContentItem(ITEM, ContentKind.RESOURCE, WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE);
        item.setModuleKey("resource");
        ContentRevision revision = new ContentRevision(REV_1, item, 1, "原合成标题", "原合成摘要",
                "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"原正文\"}]}",
                "zh-CN", null, "https://resources.example.edu/guide", "合成来源", "已授权", "合成作者",
                "NOT_ALLOWED", "NOT_ALLOWED", "{\"resourceType\":\"GUIDE\"}", ACTOR);
        item.setCurrentRevision(revision);
        setVersion(item, 1L);
        return item;
    }

    /** 13 参便捷构造器：markdownSource 为空 → 由 MarkdownDocumentService 从旧版 body 转换。 */
    private static ContentManagementService.Draft draft() {
        return new ContentManagementService.Draft(
                "RESOURCE", "修改后的合成标题", "修改后的合成摘要",
                JSON.readTree("{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"修改后正文\"}]}"),
                "zh-CN", null, "https://resources.example.edu/guide", "合成来源", "已授权", "合成作者",
                "NOT_ALLOWED", "NOT_ALLOWED", JSON.readTree("{\"resourceType\":\"GUIDE\"}"));
    }

    /** 学习用探针：Hibernate 在真实运行时会填 @Version，单元测试里用反射模拟。 */
    private static void setVersion(ContentItem item, long value) {
        try {
            Field field = ContentItem.class.getDeclaredField("version");
            field.setAccessible(true);
            field.set(item, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("探针夹具无法设置实体版本", error);
        }
    }
}
