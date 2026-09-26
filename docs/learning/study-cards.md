# 八批学习卡片

状态：材料已准备，个人理解与审核均待填写。观察任务供隔离本地合成数据环境使用，本轮没有执行写数据或清理任务。可以用 IDE 调试/测试输出替代浏览器观察，不必搭建新的可视化平台。

## 01 请求组合

链路：页面保存 → adminRevise/partnerRevise → revise → protectedRequest → request → 后端。
阅读标记：api.ts 中的“学习·01”。
已有测试：[contracts.test.ts](../../apps/web/src/lib/contracts.test.ts)，搜索 sends v0 cookie/CSRF/revision/idempotency headers。它模拟 fetch 验证组装，不证明后端安全。
观察：Network 中对照 csrf 请求与 PATCH，确认头名和版本；不抄录 Cookie/令牌值。
问题：Cookie 与 CSRF 各解决什么？请求响应丢失后，再调用 revise 是否复用原键，If-Match 能否代替幂等？
我的理解 / 代码证据 / 疑问：待本人填写。

## 02 认证与资格

链路：安全上下文 → AccountEligibilityFilter 查账号 → 路径授权 → 业务归属检查。
已有测试：[AccountEligibilityFilterTest](../../apps/api/src/test/java/cn/voidhaven/api/web/AccountEligibilityFilterTest.java)。
观察：在测试调试器中跟踪失效 session、清空上下文和提前返回。
问题：停用账号后旧会话为何不能一直沿用？permitAll、CSRF 豁免和 authenticated 有什么区别？
注意：SecurityConfig 原有笔记有校正提示，双 build 疑点见验证记录。
我的理解 / 代码证据 / 疑问：待本人填写。

## 03 内容与修订

链路：ContentItem → currentRevision/publishedRevision → revise 的 createCandidate 分支。
已有测试：[ContentItemStateTest](../../apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java)、[ContentRevisionCandidateServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/ContentRevisionCandidateServiceTest.java)。候选服务测试是相邻实现证据，不等同于完整编辑页面测试。
观察：对比编辑已发布内容前后的两个修订引用，公开正文是否改变；看实体 version 与 revisionNumber。
问题：每次保存都新增正文修订吗？为什么实体并发版本和正文修订序号不能混用？
我的理解 / 代码证据 / 疑问：待本人填写。

## 04 审核、发布、公开

链路：create → submit → decide → publish → ContentService.publicDetail → toPublic。
已有测试：[ContentManagementServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceTest.java)、[ContentItemStateTest](../../apps/api/src/test/java/cn/voidhaven/api/domain/ContentItemStateTest.java)；测试覆盖有限，结合发布代码阅读，勿推断所有条件均已全栈验收。
观察：比较草稿、批准后、发布后的公开查询；只用合成内容。
问题：审核通过为什么不等于已经公开？公开查询为什么取 publishedRevision 而不是 currentRevision？
原注释校正：create 直接新建草稿并使用传入归属，不存在“按内容类型默认归属”；错误码以抛出位置为准。
我的理解 / 代码证据 / 疑问：待本人填写。

## 05 旧版本与并发

链路：If-Match → expectedVersion → lockedOwned → version 比较 → 保存。
已有测试：[ContentManagementServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/ContentManagementServiceTest.java) 中 refuses_to_review_a_stale_item_version；它验证审核旧版本，不等同于编辑并发压力测试。[DistributedSafetyIT](../../apps/api/src/test/java/cn/voidhaven/api/integration/DistributedSafetyIT.java) 为真实依赖集成测试入口，按其具体用例判断覆盖范围。
观察：两个页面读同一内容，A 保存后 B 使用旧版本保存，对比响应与保留的输入。
问题：只加事务能否阻止丢失更新？锁定读取与显式版本比较各有什么作用？
我的理解 / 代码证据 / 疑问：待本人填写。

## 06 幂等与响应重放

链路：execute → reserve（独立事务）→ action 或 restore → 完成记录/失败释放。
已有测试：[IdempotencyServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/IdempotencyServiceTest.java)，含处理中、异参同键、失败释放、敏感结果不可重放等用例。
观察：测试调试时比较同键同参、同键异参、处理中、敏感响应四个分支。
问题：新占位成功为什么返回 null？一次性凭据为什么只能防重复写，不能重复返回原结果？
原注释校正：reserve 不会删除所有记录；release 返回 void，不查询记录；102 是数据库处理状态标记。
我的理解 / 代码证据 / 疑问：待本人填写。

## 07 媒体补偿与清理

链路：校验字节 → 锁内容/权限/配额 → storage.put → 数据库记录/引用 → 失败补偿；另一条链是无引用登记媒体 → 删除任务 → 领取租约 → 删除/重试。
已有测试：[MediaServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/MediaServiceTest.java)、[MediaOrphanCleanupServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/MediaOrphanCleanupServiceTest.java)。
观察：调试 failed_storage_deletion_stays_retryable，查看失败后任务状态；无需对真实媒体运行清理。
问题：数据库回滚为什么不会自动撤回上传？仅存在于存储桶、没有数据库记录的对象能被这段清理 SQL 找到吗？
限制：失败补偿为尽力而为；清理任务租约可到期，不应称为严格只执行一次。
我的理解 / 代码证据 / 疑问：待本人填写。

## 08 到期与任务互斥

链路：scheduledRun → MySQL 命名锁 → run → 逐条事务 → 行锁重读 → deadline → 关闭/归档。
已有测试：[ContentLifecycleServiceTest](../../apps/api/src/test/java/cn/voidhaven/api/service/ContentLifecycleServiceTest.java)，closes_a_competition_from_its_typed_deadline_not_the_json_projection；多实例边界进一步阅读 DistributedSafetyIT。
观察：在测试中控制 now，比较精确时间、上海日期截止和关闭三天的判断。招募不进入三天自动归档分支。
问题：命名锁和内容行锁为什么同时存在？单条失败为什么不应回滚整批？
我的理解 / 代码证据 / 疑问：待本人填写。

## 后续自学方式

先沿符号定位代码，再让 AI 基于这些文件解释一个疑问；要求给出代码依据、区分已实现行为与建议方案。框架基础可查 Spring Security、Spring Transactions、Jakarta Persistence、MySQL GET_LOCK 官方文档。不要把 AI 回答直接标成个人结论，先与实现和测试核对。
