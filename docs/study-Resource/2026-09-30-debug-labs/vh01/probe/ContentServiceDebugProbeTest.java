package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.web.ApiException;
import cn.voidhaven.api.web.PublicContentDto;
import cn.voidhaven.api.web.PublicMediaDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习用探针 A（不属于产品测试资产）：给 VH-01 公开读路径的调试提供"驱动器"。
 *
 * 为什么需要它：apps/api/src/test 里 ContentService 没有单元测试，listCatalog /
 * publicDetail / related / publicSitemapEntries 的主干只能靠黑盒手动复现推断，没法下断点。
 *
 * 用法：拷到 apps/api/src/test/java/cn/voidhaven/api/service/ 下即可在 IDEA 里
 * 逐个 @Test 打断点调试；本文件常驻文档包，不随产品源码提交。
 * 数据边界：全部为合成 ULID、合成标题、合成来源域名，不含真实账号或业务数据。
 *
 * 十二个调试入口（行号会漂移，按方法名定位）：
 *   D5  1. listCatalog_pages_the_raw_page_and_encodes_the_cursor_from_its_last_row
 *       2. source_filter_can_shrink_a_page_while_the_cursor_still_follows_the_raw_page
 *   D6  3. exactly_page_size_rows_means_there_is_no_next_page
 *       4. legacy_resource_module_rejects_original_source_while_resource_article_allows_it
 *       5. page_size_is_clamped_to_one_through_fifty_before_the_sql_is_built
 *       6. cursor_sort_mismatch_fails_before_any_catalog_query_runs
 *       7. admin_default_deadline_on_resource_is_silently_downgraded_to_updated
 *   D7  8. publicDetail_loads_media_only_when_includeMedia_is_true
 *       9. publicDetail_turns_an_illegal_source_into_the_same_404
 *      10. publicDetail_returns_404_when_the_repository_finds_nothing
 *   D8 11. related_merges_link_rows_and_details_then_drops_self
 *      12. sitemap_reuses_the_same_visibility_predicate
 */
class ContentServiceDebugProbeTest {
    private static final String ITEM_A = "01JCONTENT0000000000000001";
    private static final String ITEM_B = "01JCONTENT0000000000000002";
    private static final String ITEM_C = "01JCONTENT0000000000000003";
    private static final String ITEM_S = "01JCONTENT0000000000000009";
    private static final String MEDIA_ID = "01JMEDIA000000000000000001";
    private static final String OK_SOURCE = "https://contest.example.edu/rules";
    private static final String BAD_SOURCE = "http://localhost:8080/local-fixtures/source";

    // ---------- D5-1 主干：正常翻页 ----------

    @Test
    void listCatalog_pages_the_raw_page_and_encodes_the_cursor_from_its_last_row() {
        // 断点建议：本方法第 1 行；service.listCatalog 入口；CatalogQuery.selectIds 内；
        //          jdbc.query 覆写处；hasNext 判断行；nextCursor 赋值行；断言前。
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        // 造 3 条排序结果，pageSize=2 → SQL 里 limit 应为 3，多出来的第 3 条只用于判断 hasNext
        jdbc.catalogRows(ranked(ITEM_A, "1"), ranked(ITEM_B, "2"), ranked(ITEM_C, "3"));
        // 仓储刻意按 id 倒序返回（模拟 @Query 的 order by c.id desc），顺序与游标顺序不同，
        // 这样能亲眼看到 inOrder() 用 LinkedHashMap 把顺序掰回来。
        ContentService service = service(repositoryReturning(
                publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_B, "合成卡片B", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_C, "合成卡片C", OK_SOURCE, null, null, "{}")), jdbc);

        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 2, null, null, null, null, null, null);

        assertThat(page.items()).extracting(PublicContentDto::title).containsExactly("合成卡片A", "合成卡片B");
        assertThat(page.sort()).isEqualTo("default");
        assertThat(jdbc.queryCount()).isEqualTo(1);
        assertThat(jdbc.lastArgs()[jdbc.lastArgs().length - 1]).isEqualTo(3);
        assertThat(jdbc.lastSql()).contains("left join catalog_order_entries");
        assertThat(jdbc.lastSql()).contains("join content_revisions r on r.id = c.published_revision_id");
        assertThat(jdbc.lastSql()).contains("limit ?");
        CatalogCursor decoded = CatalogCursor.decode(page.nextCursor(), CatalogSort.DEFAULT);
        assertThat(decoded.key()).isEqualTo("2");
        assertThat(decoded.id()).isEqualTo(ITEM_B);
    }

    // ---------- D5-2 来源过滤会让本页变短，但游标仍取原始页末条 ----------

    @Test
    void source_filter_can_shrink_a_page_while_the_cursor_still_follows_the_raw_page() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.catalogRows(ranked(ITEM_A, "1"), ranked(ITEM_B, "2"), ranked(ITEM_C, "3"));
        ContentService service = service(repositoryReturning(
                publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_B, "合成卡片B", BAD_SOURCE, null, null, "{}"),
                publicItem(ITEM_C, "合成卡片C", OK_SOURCE, null, null, "{}")), jdbc);

        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 2, null, null, null, null, null, null);

        // 原始页是 [A, B]，B 来源不合规被丢弃 → 本页只剩 1 条
        assertThat(page.items()).extracting(PublicContentDto::title).containsExactly("合成卡片A");
        // 游标仍然指向原始页最后一条 B，不是可见列表最后一条 A
        CatalogCursor decoded = CatalogCursor.decode(page.nextCursor(), CatalogSort.DEFAULT);
        assertThat(decoded.id()).isEqualTo(ITEM_B);
        assertThat(decoded.key()).isEqualTo("2");
    }

    // ---------- D6-3 恰好 pageSize 条 → 没有下一页 ----------

    @Test
    void exactly_page_size_rows_means_there_is_no_next_page() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.catalogRows(ranked(ITEM_A, "1"), ranked(ITEM_B, "2"));
        ContentService service = service(repositoryReturning(
                publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_B, "合成卡片B", OK_SOURCE, null, null, "{}")), jdbc);

        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 2, null, null, null, null, null, null);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
        assertThat(jdbc.lastArgs()[jdbc.lastArgs().length - 1]).isEqualTo(3);
    }

    // ---------- D6-5 同一个 ORIGINAL 元数据，模块不同，结果不同 ----------

    @Test
    void legacy_resource_module_rejects_original_source_while_resource_article_allows_it() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.catalogRows(ranked(ITEM_A, "1"), ranked(ITEM_B, "2"));
        // A 走 legacy 模块（moduleKey 为空 → "resource"，allowsOriginalSource=false）
        ContentItem legacy = publicItem(ITEM_A, "旧模块原创", null, null, "{\"sourceMode\":\"ORIGINAL\"}", "{}");
        // B 走 resource.article 模块（allowsOriginalSource=true）
        ContentItem article = publicItem(ITEM_B, "文章模块原创", null, "resource.article", "{\"sourceMode\":\"ORIGINAL\"}", "{}");
        ContentService service = service(repositoryReturning(legacy, article), jdbc);

        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 5, null, null, null, null, null, null);

        assertThat(service.sourcedForPublic(legacy)).isFalse();
        assertThat(service.sourcedForPublic(article)).isTrue();
        assertThat(page.items()).extracting(PublicContentDto::moduleKey).containsExactly("resource.article");
    }

    // ---------- D6-3 pageSize 夹取 ----------

    @Test
    void page_size_is_clamped_to_one_through_fifty_before_the_sql_is_built() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentService service = service(repositoryReturning(), jdbc);

        service.listCatalog(ContentKind.RESOURCE, null, 0, null, null, null, null, null, null);
        assertThat(jdbc.lastArgs()[jdbc.lastArgs().length - 1]).isEqualTo(2); // 夹到 1，再加 1

        service.listCatalog(ContentKind.RESOURCE, null, 999, null, null, null, null, null, null);
        assertThat(jdbc.lastArgs()[jdbc.lastArgs().length - 1]).isEqualTo(51); // 夹到 50，再加 1
    }

    // ---------- D6-4 游标排序不匹配：在建 SQL 之前就失败 ----------

    @Test
    void cursor_sort_mismatch_fails_before_any_catalog_query_runs() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentService service = service(repositoryReturning(), jdbc);
        String deadlineCursor = CatalogCursor.of(CatalogSort.DEADLINE, "2026-10-15", ITEM_A).encode();

        assertThatThrownBy(() -> service.listCatalog(
                ContentKind.COMPETITION, null, 20, deadlineCursor, null, "updated", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CATALOG_CURSOR_MISMATCH");
        assertThat(jdbc.queryCount()).isZero();
    }

    // ---------- D6-6 后台默认排序误配 → 静默降级；用户点名要 → 报错 ----------

    @Test
    void admin_default_deadline_on_resource_is_silently_downgraded_to_updated() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        jdbc.configuredSort("DEADLINE");
        ContentService service = service(repositoryReturning(), jdbc);

        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 20, null, null, null, null, null, null);
        assertThat(page.sort()).isEqualTo("updated");
        assertThat(jdbc.lastSql()).contains("order by c.updated_at desc");

        assertThatThrownBy(() -> service.listCatalog(
                ContentKind.RESOURCE, null, 20, null, null, "deadline", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CATALOG_SORT_UNSUPPORTED");
    }

    // ---------- D7-1 详情：媒体开关 ----------

    @Test
    void publicDetail_loads_media_only_when_includeMedia_is_true() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentItem item = publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}");
        MediaReadService media = mock(MediaReadService.class);
        when(media.forPublishedRevision(anyString())).thenReturn(List.of(
                new PublicMediaDto(MEDIA_ID, "/api/v1/public/media/" + MEDIA_ID, "image/png", 1200, 800, "合成配图")));
        ContentService service = service(repositoryReturning(item), jdbc, media);

        // 详情：includeMedia = true
        PublicContentDto detail = service.publicDetail(ContentKind.RESOURCE, ITEM_A);
        assertThat(detail.title()).isEqualTo("合成卡片A");
        assertThat(detail.media()).hasSize(1);
        assertThat(detail.media().get(0).altText()).isEqualTo("合成配图");
        verify(media, times(1)).forPublishedRevision(item.getPublishedRevision().getId());

        // 列表：includeMedia = false，媒体不应被查询第二次
        jdbc.catalogRows(ranked(ITEM_A, "1"));
        ContentService.PublicPage page = service.listCatalog(
                ContentKind.RESOURCE, null, 5, null, null, null, null, null, null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).media()).isEmpty();
        verify(media, times(1)).forPublishedRevision(anyString());
    }

    // ---------- D7-2 详情：来源不合规 → 与"不存在"同一个 404 ----------

    @Test
    void publicDetail_turns_an_illegal_source_into_the_same_404() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentService service = service(repositoryReturning(
                publicItem(ITEM_A, "来源非法", BAD_SOURCE, null, null, "{}")), jdbc);

        assertThatThrownBy(() -> service.publicDetail(ContentKind.RESOURCE, ITEM_A))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_NOT_FOUND");
    }

    // ---------- D7-3 详情：仓储查不到 → 404 ----------

    @Test
    void publicDetail_returns_404_when_the_repository_finds_nothing() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentService service = service(repositoryReturning(), jdbc);

        assertThatThrownBy(() -> service.publicDetail(ITEM_A))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_NOT_FOUND");
    }

    // ---------- D8-1 关联：关系表 + 详情字段求并集、去重、去掉自己 ----------

    @Test
    void related_merges_link_rows_and_details_then_drops_self() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        // 关系表返回 [B, C]；详情字段声明 [A, B] → 并集去重后应保留 [B, C, A] 的顺序
        jdbc.linkRows(ITEM_B, ITEM_C);
        ContentItem source = publicItem(ITEM_S, "源内容", OK_SOURCE, null, null,
                "{\"relatedCompetitionIds\":[\"" + ITEM_A + "\",\"" + ITEM_B + "\"]}");
        ContentService service = service(repositoryReturning(
                source,
                publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_B, "合成卡片B", OK_SOURCE, null, null, "{}"),
                publicItem(ITEM_C, "合成卡片C", OK_SOURCE, null, null, "{}")), jdbc);

        ContentService.PublicPage page = service.related(ITEM_S);

        assertThat(page.items()).extracting(PublicContentDto::title)
                .containsExactly("合成卡片B", "合成卡片C", "合成卡片A");
        assertThat(page.nextCursor()).isNull();
        assertThat(jdbc.lastQueryForListSql()).contains("content_items source");
    }

    // ---------- D8-2 sitemap 与详情共用同一条可见性谓词 ----------

    @Test
    void sitemap_reuses_the_same_visibility_predicate() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ContentItem legal = publicItem(ITEM_A, "合成卡片A", OK_SOURCE, null, null, "{}");
        ContentItem illegal = publicItem(ITEM_B, "来源非法", BAD_SOURCE, null, null, "{}");
        ContentItemRepository repository = repositoryReturning(legal, illegal);
        when(repository.findPublicByKind(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(0) == ContentKind.RESOURCE ? List.of(legal, illegal) : List.of());
        ContentService service = service(repository, jdbc);

        List<ContentService.SitemapEntry> entries = service.publicSitemapEntries();

        // 来源不合规的已发布内容不会进 sitemap，与详情 404 用的是同一条规则
        assertThat(entries).extracting(ContentService.SitemapEntry::id).containsExactly(ITEM_A);
        assertThat(entries).extracting(ContentService.SitemapEntry::kind).containsExactly(ContentKind.RESOURCE);
    }

    // ---------- 夹具：把协作者换成可观察的替身 ----------

    /** 继承 JdbcTemplate 只为拦截三个方法：拿到真实 SQL 与参数、返回预置行，不去连数据库。 */
    static final class RecordingJdbcTemplate extends JdbcTemplate {
        record Bound(String sql, Object[] args) { }

        private final List<CatalogQuery.RankedId> rows = new ArrayList<>();
        private final List<String> linkIds = new ArrayList<>();
        private String configuredSort;
        private Bound bound;
        private Bound linkBound;
        private int queryCount;

        void catalogRows(CatalogQuery.RankedId... values) {
            rows.clear();
            rows.addAll(List.of(values));
        }

        void linkRows(String... ids) {
            linkIds.clear();
            linkIds.addAll(List.of(ids));
        }

        void configuredSort(String value) {
            this.configuredSort = value;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.bound = new Bound(sql, args);
            this.queryCount++;
            return cast(rows);
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            this.linkBound = new Bound(sql, args);
            return cast(linkIds);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(configuredSort);
        }

        String lastSql() { return bound == null ? null : bound.sql(); }

        String lastQueryForListSql() { return linkBound == null ? null : linkBound.sql(); }

        Object[] lastArgs() { return bound == null ? new Object[0] : bound.args(); }

        int queryCount() { return queryCount; }

        @SuppressWarnings("unchecked")
        private static <T> List<T> cast(List<?> value) { return (List<T>) value; }
    }

    private static CatalogQuery.RankedId ranked(String id, String key) {
        return new CatalogQuery.RankedId(id, key);
    }

    private static ContentService service(ContentItemRepository repository, RecordingJdbcTemplate jdbc) {
        return service(repository, jdbc, mock(MediaReadService.class));
    }

    private static ContentService service(ContentItemRepository repository, RecordingJdbcTemplate jdbc, MediaReadService media) {
        return new ContentService(
                repository,
                JsonMapper.builder().build(),
                new ContentSourcePolicy(false, "https://voidhaven.example"),
                media,
                new ContentLanguagePolicy(),
                new ContentModuleRegistry(JsonMapper.builder().build()),
                jdbc);
    }

    /**
     * 模拟公开读仓储：查得到就返回，查不到就返回空；列表查询按真实 @Query 的
     * order by c.id desc 排列，这样 inOrder 的重排效果才看得出来。
     */
    private static ContentItemRepository repositoryReturning(ContentItem... candidates) {
        Map<String, ContentItem> byId = List.of(candidates).stream()
                .collect(Collectors.toMap(ContentItem::getId, Function.identity()));
        ContentItemRepository repository = mock(ContentItemRepository.class);
        when(repository.findPublicByIds(any())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            List<ContentItem> found = new ArrayList<>();
            for (String id : ids) {
                ContentItem item = byId.get(id);
                if (item != null) found.add(item);
            }
            found.sort(Comparator.comparing(ContentItem::getId).reversed());
            return found;
        });
        when(repository.findPublicById(any())).thenAnswer(invocation ->
                Optional.ofNullable(byId.get(invocation.getArgument(0))));
        when(repository.findPublicByIdAndKind(any(), any())).thenAnswer(invocation -> {
            ContentItem item = byId.get(invocation.getArgument(0));
            ContentKind kind = invocation.getArgument(1);
            return item != null && item.getKind() == kind ? Optional.of(item) : Optional.empty();
        });
        return repository;
    }

    /** 造一条"已发布"的合成内容；moduleKey 传 null 表示走 legacy 模块。 */
    private static ContentItem publicItem(String id, String title, String sourceUrl, String moduleKey,
                                          String metadataJson, String detailsJson) {
        ContentItem item = new ContentItem(id, ContentKind.RESOURCE, WorkflowStatus.APPROVED, Visibility.HIDDEN, RecordState.ACTIVE);
        if (moduleKey != null) item.setModuleKey(moduleKey);
        ContentRevision revision = new ContentRevision(
                id.replace("CONTENT", "REVISION"), item, 1, title, "合成摘要",
                "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"合成正文\"}]}",
                "zh-CN", null, sourceUrl, "合成来源", "已授权", "合成作者",
                "NOT_ALLOWED", "NOT_ALLOWED", detailsJson, null);
        revision.setEvolvableFields("# 合成正文", 1, 1, "{}", metadataJson);
        item.setCurrentRevision(revision);
        item.publish();
        return item;
    }
}
