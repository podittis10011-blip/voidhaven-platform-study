# VH04 注释候选

仅在自己完成对应断点及断言后采用；本次交付不自动修改产品源码。每条注释只陈述对应测试能够证明的范围。

| 站 | 位置 | 注释候选 | 使用判据 |
|---|---|---|---|
| D1 | `apps/api/src/main/java/cn/voidhaven/api/service/IdempotencyService.java:41` | `// 学习·VH04：本节驱动的观察结果见学习包 D1，模拟依赖不证明真实存储行为。` | 能给出 在途测试 actions.get() 的实际值，并说明本节边界 |
| D2 | `apps/api/src/main/java/cn/voidhaven/api/service/ContentManagementService.java:347` | `// 学习·VH04：本节驱动的观察结果见学习包 D2，模拟依赖不证明真实存储行为。` | 能给出 内容冲突 code 的实际值，并说明本节边界 |
| D3 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicWriteRateLimiter.java:36` | `// 学习·VH04：本节驱动的观察结果见学习包 D3，模拟依赖不证明真实存储行为。` | 能给出 公开写输入主体 的实际值，并说明本节边界 |
| D4 | `apps/api/src/main/java/cn/voidhaven/api/service/MediaOrphanCleanupService.java:46` | `// 学习·VH04：本节驱动的观察结果见学习包 D4，模拟依赖不证明真实存储行为。` | 能给出 成功测试 removed 的实际值，并说明本节边界 |
| D5 | `apps/api/src/main/java/cn/voidhaven/api/service/RateLimitLockService.java:36` | `// 学习·VH04：本节驱动的观察结果见学习包 D5，模拟依赖不证明真实存储行为。` | 能给出 公开写 / 登录 并发允许-拒绝 的实际值，并说明本节边界 |
