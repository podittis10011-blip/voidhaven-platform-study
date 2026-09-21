package cn.voidhaven.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_items")

//ContentItem类是内容管理的核心类，它包含了内容的各种属性，以及内容的各种操作。
//ContentItem类的属性：
//1. id：内容id
//2. kind：内容类型
    //kind内容类型都包含哪些内容类型？
        //ContentKind.ARTICLE: 文章
        //ContentKind.VIDEO: 视频
        //ContentKind.AUDIO: 音频
        //ContentKind.IMAGE: 图片
        //ContentKind.DOCUMENT: 文档
        //ContentKind.LINK: 链接
        //ContentKind.OTHER: 其他


//3. moduleKey：内容模块key
    //moduleKey内容模块key都包含哪些内容模块key？
        //ContentModuleKey.ARTICLE: 文章
        //ContentModuleKey.VIDEO: 视频
        //ContentModuleKey.AUDIO: 音频
        //ContentModuleKey.IMAGE: 图片
        //ContentModuleKey.DOCUMENT: 文档
        //ContentModuleKey.LINK: 链接
        //ContentModuleKey.OTHER: 其他
        //ContentModuleKey.COMPETITION: 竞赛
        //ContentModuleKey.RECRUITMENT: 招聘
        //ContentModuleKey.OTHER: 其他

//4. workflowStatus：内容工作流状态
    //workflowStatus内容工作流状态都包含哪些内容工作流状态？
        //WorkflowStatus.DRAFT: 草稿
        //WorkflowStatus.PENDING_REVIEW: 待审核
        //WorkflowStatus.APPROVED: 批准
        //WorkflowStatus.REJECTED: 拒绝
        //WorkflowStatus.CHANGES_REQUESTED: 请求更改
        //WorkflowStatus.PUBLISHED: 发布
        //WorkflowStatus.ARCHIVED: 归档
        //WorkflowStatus.DELETED: 删除
        //WorkflowStatus.OTHER: 其他

//5. publicationStatus：内容发布状态
    //publicationStatus内容发布状态都包含哪些内容发布状态？
        //PublicationStatus.NONE: 未发布
        //PublicationStatus.ACTIVE: 活跃
        //PublicationStatus.CLOSED: 关闭
        //PublicationStatus.OTHER: 其他

//6. operationalStatus：内容运营状态
    //operationalStatus内容运营状态都包含哪些内容运营状态？
        //OperationalStatus.OPEN: 开放
        //OperationalStatus.CLOSED: 关闭
        //OperationalStatus.OTHER: 其他

//7. visibility：内容可见性
    //visibility内容可见性都包含哪些内容可见性？
        //Visibility.PUBLIC: 公开
        //Visibility.HIDDEN: 隐藏
        //Visibility.OTHER: 其他

//8. recordState：内容记录状态
    //recordState内容记录状态都包含哪些内容记录状态？
        //RecordState.ACTIVE: 活跃
        //RecordState.ARCHIVED: 归档
        //RecordState.DELETED: 删除
        //RecordState.OTHER: 其他

//9. currentRevision：内容当前版本
//10. publishedRevision：内容发布版本
//11. ownerPartnerProfileId：内容归属合作方
//12. closedAt：内容关闭时间
//13. archivedAt：内容归档时间
//14. submittedAt：内容提交时间
//15. updatedAt：内容更新时间
//16. version：内容版本

//ContentItem类的构造函数：
//1. ContentItem(String id, ContentKind kind, WorkflowStatus workflowStatus, Visibility visibility, RecordState recordState)：构造函数，用于构造ContentItem对象。
//2. ContentItem()：构造函数，用于构造ContentItem对象。

//ContentItem类的方法：
//1. getId()：获取内容id
//2. getKind()：获取内容类型
//3. getModuleKey()：获取内容模块key
//4. getWorkflowStatus()：获取内容工作流状态
//5. getPublicationStatus()：获取内容发布状态
//6. getOperationalStatus()：获取内容运营状态
//7. getVisibility()：获取内容可见性
//8. getRecordState()：获取内容记录状态
//9. getCurrentRevision()：获取内容当前版本
//10. getPublishedRevision()：获取内容发布版本
//11. getOwnerPartnerProfileId()：获取内容归属合作方
//12. getClosedAt()：获取内容关闭时间
//13. getArchivedAt()：获取内容归档时间
//14. getSubmittedAt()：获取内容提交时间
//15. getUpdatedAt()：获取内容更新时间
//16. getVersion()：获取内容版本

//ContentItem类的方法：
//1. setCurrentRevision(ContentRevision value)：设置内容当前版本
//2. setPublishedRevision(ContentRevision value)：设置内容发布版本
//3. setOwnerPartnerProfileId(String value)：设置内容归属合作方
//4. setModuleKey(String value)：设置内容模块key
//5. setOperationalStatus(String value)：设置内容运营状态
//6. beginCandidateRevision()：开始候选版本
//7. submit()：提交内容
//8. approve()：批准内容    
public class ContentItem {
    @Id private String id;
    @Enumerated(EnumType.STRING) private ContentKind kind;
    @Column(name = "module_key") private String moduleKey;
    @Enumerated(EnumType.STRING) @Column(name = "workflow_status") private WorkflowStatus workflowStatus;
    @Enumerated(EnumType.STRING) @Column(name = "publication_status") private PublicationStatus publicationStatus = PublicationStatus.NONE;
    @Column(name = "operational_status") private String operationalStatus;
    @Enumerated(EnumType.STRING) private Visibility visibility;
    @Enumerated(EnumType.STRING) @Column(name = "record_state") private RecordState recordState;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_revision_id") private ContentRevision currentRevision;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_revision_id") private ContentRevision publishedRevision;
    @Column(name = "owner_partner_profile_id") private String ownerPartnerProfileId;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Version private Long version;
    protected ContentItem() { }
    public ContentItem(String id, ContentKind kind, WorkflowStatus workflowStatus, Visibility visibility, RecordState recordState) {
        this.id = id; this.kind = kind; this.workflowStatus = workflowStatus; this.visibility = visibility; this.recordState = recordState; this.updatedAt = Instant.now();
    }
    public String getId() { return id; }
    public ContentKind getKind() { return kind; }
    public String getModuleKey() { return moduleKey; }
    public WorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public PublicationStatus getPublicationStatus() { return publicationStatus; }
    public String getOperationalStatus() { return operationalStatus; }
    public Visibility getVisibility() { return visibility; }
    public RecordState getRecordState() { return recordState; }
    public ContentRevision getCurrentRevision() { return currentRevision; }
    public ContentRevision getPublishedRevision() { return publishedRevision; }
    public String getOwnerPartnerProfileId() { return ownerPartnerProfileId; }
    public Long getVersion() { return version; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setCurrentRevision(ContentRevision value) { currentRevision = value; }
    public void setPublishedRevision(ContentRevision value) { publishedRevision = value; }
    public void setOwnerPartnerProfileId(String value) { ownerPartnerProfileId = value; }
    public void setModuleKey(String value) { moduleKey = value; }
    public void setOperationalStatus(String value) { operationalStatus = value; touch(); }
    public void beginCandidateRevision() { workflowStatus = WorkflowStatus.DRAFT; submittedAt = null; touch(); }
    public void submit() { workflowStatus = WorkflowStatus.PENDING_REVIEW; submittedAt = Instant.now(); touch(); }
    public void approve() { workflowStatus = WorkflowStatus.APPROVED; touch(); }
    public void reject() { workflowStatus = WorkflowStatus.REJECTED; touch(); }
    public void requestChanges() { workflowStatus = WorkflowStatus.CHANGES_REQUESTED; touch(); }
    public void reopenForChanges() { workflowStatus = WorkflowStatus.CHANGES_REQUESTED; touch(); }
    public void publish() { workflowStatus = WorkflowStatus.PUBLISHED; publicationStatus = PublicationStatus.ACTIVE; closedAt = null; visibility = Visibility.PUBLIC; recordState = RecordState.ACTIVE; publishedRevision = currentRevision; if (kind == ContentKind.RECRUITMENT && operationalStatus == null) operationalStatus = "OPEN"; touch(); }
    public void closePublic(Instant when) { publicationStatus = PublicationStatus.CLOSED; closedAt = when; touch(); }
    public void hide() { visibility = Visibility.HIDDEN; touch(); }
    public void show() { visibility = Visibility.PUBLIC; touch(); }
    public void archive() { recordState = RecordState.ARCHIVED; visibility = Visibility.HIDDEN; archivedAt = Instant.now(); touch(); }
    public void restore() { recordState = RecordState.ACTIVE; visibility = Visibility.HIDDEN; workflowStatus = WorkflowStatus.APPROVED; publicationStatus = PublicationStatus.NONE; archivedAt = null; touch(); }
    public void markDeleted() { recordState = RecordState.DELETED; visibility = Visibility.HIDDEN; touch(); }
    public void touch() { updatedAt = Instant.now(); }
}
