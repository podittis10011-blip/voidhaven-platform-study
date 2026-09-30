# VH05 注释候选

仅在自己完成对应断点及断言后采用；本次交付不自动修改产品源码。每条注释只陈述对应测试能够证明的范围。

| 站 | 位置 | 注释候选 | 使用判据 |
|---|---|---|---|
| D1 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaValidator.java:38` | `// 学习·VH05：本节驱动的观察结果见学习包 D1，模拟依赖不证明真实存储行为。` | 能给出 测试帧 validated.width() / height() 的实际值，并说明本节边界 |
| D2 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaService.java:63` | `// 学习·VH05：本节驱动的观察结果见学习包 D2，模拟依赖不证明真实存储行为。` | 能给出 正文上传主顺序 的实际值，并说明本节边界 |
| D3 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaReadService.java:22` | `// 学习·VH05：本节驱动的观察结果见学习包 D3，模拟依赖不证明真实存储行为。` | 能给出 公开 ca 分支 的实际值，并说明本节边界 |
| D4 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:30` | `// 学习·VH05：本节驱动的观察结果见学习包 D4，模拟依赖不证明真实存储行为。` | 能给出 成功 removed 的实际值，并说明本节边界 |
| D5 | `apps/api/src/main/java/cn/voidhaven/api/service/MarkdownDocumentService.java:31` | `// 学习·VH05：本节驱动的观察结果见学习包 D5，模拟依赖不证明真实存储行为。` | 能给出 测试帧 document.path("version").asInt() 的实际值，并说明本节边界 |
