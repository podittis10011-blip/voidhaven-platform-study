# VOIDHAVEN 学习入口

八批材料一次备齐，源码标记为“学习·01”至“学习·08”。保留你原来的笔记；带“校正”的新增说明用于指出原注释与实现的差异。全部为待你审核的学习材料，不代表已掌握或审核通过。

首次约两小时：第 1 批 15 分钟、第 2 批 15 分钟、第 3～4 批 35 分钟、第 5～6 批 30 分钟、第 7～8 批 20 分钟，最后 5 分钟口述一条请求链。这里是阅读预算，不包含首次安装依赖和启动环境。

| 批次 | 要回答的问题 | 主要代码入口 |
| --- | --- | --- |
| 01 | 请求如何携带会话与保护信息？ | [api.ts](../../apps/web/src/lib/api.ts) |
| 02 | 已登录为什么仍要检查账号和权限？ | [SecurityConfig](../../apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java)、[AccountEligibilityFilter](../../apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java) |
| 03 | 正在编辑与正在公开的正文为何分开？ | [ContentItem](../../apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java)、[ContentManagementService](../../apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java) |
| 04 | 审核怎样变成公开内容？ | [ContentManagementService](../../apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java)、[ContentService](../../apps/api/src/main/java/cn/voidhaven/api/service/ContentService.java) |
| 05 | 旧页面保存如何被拒绝？ | [ContentManagementService.revise](../../apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java) |
| 06 | 同一个请求再次到达会怎样？ | [IdempotencyService](../../apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java)、[IdempotencyReservationService](../../apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyReservationService.java) |
| 07 | 文件与数据库不同步时如何处理？ | [MediaService](../../apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java)、[MediaOrphanCleanupService](../../apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java) |
| 08 | 到期内容如何在多实例下处理？ | [ContentLifecycleService](../../apps/api/src/main/java/cn/voidhaven/api/service/ContentLifecycleService.java)、[MysqlAdvisoryLock](../../apps/api/src/main/java/cn/voidhaven/api/service/MysqlAdvisoryLock.java) |

每批的测试、观察任务和两个问题见 [学习卡片](./study-cards.md)。自行阅读后，在相应卡片写三句话即可：我的理解、代码证据、仍有疑问。可以直接改源码注释，也可以集中提交问题讨论，不要求每批都与 AI 对话。

材料依据以本副本代码、OpenAPI 和现有测试为准。规范入口为 [S-06](../standards/S-06-api-and-data-contract.md)，数据库学习只阅读迁移，不修改历史迁移。

验证结果与已有问题见 [验证记录](./verification.md)。已有代码存在的疑点与这次注释变更分开记录，测试未执行的项目不会记为通过。
