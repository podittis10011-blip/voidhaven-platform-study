package cn.voidhaven.api.persistence;
import cn.voidhaven.api.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

//该类中出现的几种注释：
//1. @EntityGraph：表示查询时需要加载指定的属性。
    //@EntityGraph(attributePaths = "publishedRevision")：表示查询时需要加载publishedRevision属性。
    //@EntityGraph(attributePaths = "currentRevision")：表示查询时需要加载currentRevision属性。
//2. @Query：表示查询语句。
//3. @Param：表示查询参数。
//4. @Lock：表示查询时需要加锁。

//基础知识补习，这些注释来源于：
    //@EntityGraph来源于Spring Data JPA 的 @EntityGraph 注解，用于定义查询时需要加载的实体关系图。
    //@Query来源于Spring Data JPA 的 @Query 注解，用于定义查询语句。
    //@Param来源于Spring Data JPA 的 @Param 注解，用于定义查询参数。
    //@Lock来源于Spring Data JPA 的 @Lock 注解，用于定义查询时需要加锁。

//这些注解的常见用法语法格式：
    //@EntityGraph(attributePaths = "publishedRevision")
        //表示查询时需要加载publishedRevision属性。
    //@Query("select c from ContentItem c where c.kind = :kind and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
        //表示查询语句，查询ContentItem对象，where条件为kind不为空且publishedRevision不为空且visibility为PUBLIC且recordState为ACTIVE，order by条件为id降序。返回List<ContentItem>。
    //@Param("kind") ContentKind kind: 表示查询参数，kind为ContentKind类型。
    //List<ContentItem> findPublicByKind(@Param("kind") ContentKind kind, Pageable pageable);
        //表示查询ContentItem对象，where条件为kind不为空且publishedRevision不为空且visibility为PUBLIC且recordState为ACTIVE，order by条件为id降序。返回List<ContentItem>。


public interface ContentItemRepository extends JpaRepository<ContentItem, String> {
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.kind = :kind and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
    List<ContentItem> findPublicByKind(@Param("kind") ContentKind kind, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where c.kind = :kind and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicPageByKind(@Param("kind") ContentKind kind, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where (:kind is null or c.kind = :kind) and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicSearch(@Param("kind") ContentKind kind, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id = :id and c.kind = :kind and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    Optional<ContentItem> findPublicByIdAndKind(@Param("id") String id, @Param("kind") ContentKind kind);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c join c.publishedRevision r where c.moduleKey = :moduleKey and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and (:cursor is null or c.id < :cursor) and (:query is null or lower(r.title) like lower(concat('%', :query, '%')) or lower(r.summary) like lower(concat('%', :query, '%')) or lower(r.sourceName) like lower(concat('%', :query, '%'))) order by c.id desc")
    List<ContentItem> findPublicPageByModuleKey(@Param("moduleKey") String moduleKey, @Param("cursor") String cursor, @Param("query") String query, Pageable pageable);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id in :ids and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' order by c.id desc")
    List<ContentItem> findPublicByIds(@Param("ids") Collection<String> ids);
    @EntityGraph(attributePaths = {"publishedRevision", "currentRevision"})
    @Query("select c from ContentItem c where c.id in :ids")
    List<ContentItem> findGraphByIds(@Param("ids") Collection<String> ids);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.id = :id and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    Optional<ContentItem> findPublicById(@Param("id") String id);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findByOwnerPartnerProfileIdOrderByIdDesc(String ownerPartnerProfileId);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findByWorkflowStatusOrderByIdAsc(WorkflowStatus status);
    @EntityGraph(attributePaths = "currentRevision")
    List<ContentItem> findAllByOrderByIdDesc();
    @EntityGraph(attributePaths = "currentRevision")
    @Query("select c from ContentItem c where c.id = :id")
    Optional<ContentItem> findWithCurrentRevisionById(@Param("id") String id);

    //@Lock(LockModeType.PESSIMISTIC_WRITE)：表示查询时需要加锁。
    //为什么该查询操作需要加锁？
    //因为该查询操作需要确保查询到的内容是最新的，所以需要加锁。
    @Lock(LockModeType.PESSIMISTIC_WRITE)

    @Query("select c from ContentItem c where c.id = :id")
    Optional<ContentItem> findByIdForUpdate(@Param("id") String id);
    @Query("select count(c) from ContentItem c where c.ownerPartnerProfileId = :profileId and c.publishedRevision is not null and c.publicationStatus = 'ACTIVE' and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE'")
    long countPublicActiveByOwnerPartnerProfileId(@Param("profileId") String profileId);
    @EntityGraph(attributePaths = "publishedRevision")
    @Query("select c from ContentItem c where c.kind in ('COMPETITION', 'RECRUITMENT') and c.publishedRevision is not null and c.visibility = 'PUBLIC' and c.recordState = 'ACTIVE' and c.publicationStatus in ('ACTIVE', 'CLOSED')")
    List<ContentItem> findTimedPublicItems();
}
