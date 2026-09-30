# VH06 注释候选

仅在自己完成对应断点及断言后采用；本次交付不自动修改产品源码。每条注释只陈述对应测试能够证明的范围。

| 站 | 位置 | 注释候选 | 使用判据 |
|---|---|---|---|
| D1 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java:54` | `// 学习·VH06：本节驱动的观察结果见学习包 D1，模拟依赖不证明真实存储行为。` | 能给出 写入例 request.getMethod() / getRequestURI() 的实际值，并说明本节边界 |
| D2 | `apps/api/src/main/java/cn/voidhaven/api/web/PublicWriteRateLimitInterceptor.java:60` | `// 学习·VH06：本节驱动的观察结果见学习包 D2，模拟依赖不证明真实存储行为。` | 能给出 路径 的实际值，并说明本节边界 |
| D3 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseService.java:83` | `// 学习·VH06：本节驱动的观察结果见学习包 D3，模拟依赖不证明真实存储行为。` | 能给出 created.id().length() / queryCode().length() 的实际值，并说明本节边界 |
| D4 | `apps/api/src/main/java/cn/voidhaven/api/service/ProjectJoinService.java:48` | `// 学习·VH06：本节驱动的观察结果见学习包 D4，模拟依赖不证明真实存储行为。` | 能给出 无 grant 项目 SQL 的实际值，并说明本节边界 |
| D5 | `apps/api/src/main/java/cn/voidhaven/api/service/PublicCaseAdministrationService.java:80` | `// 学习·VH06：本节驱动的观察结果见学习包 D5，模拟依赖不证明真实存储行为。` | 能给出 result.contact() 的实际值，并说明本节边界 |
| D6 | `apps/api/src/main/java/cn/voidhaven/api/service/ManualDeliveryService.java:31` | `// 学习·VH06：本节驱动的观察结果见学习包 D6，模拟依赖不证明真实存储行为。` | 能给出 人工投递审计 action 的实际值，并说明本节边界 |
