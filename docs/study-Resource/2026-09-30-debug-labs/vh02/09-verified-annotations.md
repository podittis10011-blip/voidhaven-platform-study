# VH02 注释候选

仅在自己完成对应断点及断言后采用；本次交付不自动修改产品源码。每条注释只陈述对应测试能够证明的范围。

| 站 | 位置 | 注释候选 | 使用判据 |
|---|---|---|---|
| D1 | `apps/api/src/main/java/cn/voidhaven/api/domain/ContentItem.java:178` | `// 学习·VH02：本节驱动的观察结果见学习包 D1，模拟依赖不证明真实存储行为。` | 能给出 测试帧 item.getWorkflowStatus()，发布后 的实际值，并说明本节边界 |
| D2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:294` | `// 学习·VH02：本节驱动的观察结果见学习包 D2，模拟依赖不证明真实存储行为。` | 能给出 revise 帧 createCandidate，赋值后（发布/草稿） 的实际值，并说明本节边界 |
| D3 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:279` | `// 学习·VH02：本节驱动的观察结果见学习包 D3，模拟依赖不证明真实存储行为。` | 能给出 revise 帧 expectedVersion / item.getVersion() 的实际值，并说明本节边界 |
| D4 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:667` | `// 学习·VH02：本节驱动的观察结果见学习包 D4，模拟依赖不证明真实存储行为。` | 能给出 输入 draft 的 kind / language 的实际值，并说明本节边界 |
| D5 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentRevisionCandidateService.java:23` | `// 学习·VH02：本节驱动的观察结果见学习包 D5，模拟依赖不证明真实存储行为。` | 能给出 用例一 candidate.getRevisionNumber() 的实际值，并说明本节边界 |
| D6 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentLifecycleService.java:60` | `// 学习·VH02：本节驱动的观察结果见学习包 D6，模拟依赖不证明真实存储行为。` | 能给出 测试帧 detail.get("deadline_date") 的实际值，并说明本节边界 |
