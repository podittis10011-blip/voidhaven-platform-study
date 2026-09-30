# VH03 注释候选

仅在自己完成对应断点及断言后采用；本次交付不自动修改产品源码。每条注释只陈述对应测试能够证明的范围。

| 站 | 位置 | 注释候选 | 使用判据 |
|---|---|---|---|
| D1 | `apps/api/src/main/java/cn/voidhaven/api/web/AccountEligibilityFilter.java:46` | `// 学习·VH03：本节驱动的观察结果见学习包 D1，模拟依赖不证明真实存储行为。` | 能给出 过滤器帧 authentication.getName() 的实际值，并说明本节边界 |
| D2 | `apps/api/src/main/java/cn/voidhaven/api/config/SecurityConfig.java:40` | `// 学习·VH03：本节驱动的观察结果见学习包 D2，模拟依赖不证明真实存储行为。` | 能给出 探针 first.equals(second) 的实际值，并说明本节边界 |
| D3 | `apps/api/src/main/java/cn/voidhaven/api/service/AuthenticationRateLimiter.java:56` | `// 学习·VH03：本节驱动的观察结果见学习包 D3，模拟依赖不证明真实存储行为。` | 能给出 失败输入 的实际值，并说明本节边界 |
| D4 | `apps/api/src/main/java/cn/voidhaven/api/service/AccountAdministrationService.java:133` | `// 学习·VH03：本节驱动的观察结果见学习包 D4，模拟依赖不证明真实存储行为。` | 能给出 停用后 account.getQualificationStatus() 的实际值，并说明本节边界 |
| D5 | `apps/api/src/main/java/cn/voidhaven/api/service/ActivationService.java:61` | `// 学习·VH03：本节驱动的观察结果见学习包 D5，模拟依赖不证明真实存储行为。` | 能给出 测试帧 token.getTokenHash().equals(raw) 的实际值，并说明本节边界 |
| D6 | `apps/api/src/main/java/cn/voidhaven/api/config/ProductionConfigurationValidator.java:77` | `// 学习·VH03：本节驱动的观察结果见学习包 D6，模拟依赖不证明真实存储行为。` | 能给出 合法配置 run 的实际值，并说明本节边界 |
| D7 | `apps/api/src/main/java/cn/voidhaven/api/config/BootstrapAdminCommand.java:53` | `// 学习·VH03：本节驱动的观察结果见学习包 D7，模拟依赖不证明真实存储行为。` | 能给出 注解 profile.value() 的实际值，并说明本节边界 |
