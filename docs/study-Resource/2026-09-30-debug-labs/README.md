# VH01～VH06 调试学习包

**唯一学习入口是本目录下的 vh01～vh06。** package 已直接合并，不另建历史归档。VH01 原材料保留。

学习版仓库：`D:\学习资料\voidhaven-platform`；源码基准 HEAD `0bd9677`。

| 层 | 主题 | 站数 | 基线用例数 | 入口 |
|---|---|---:|---:|---|
| VH01 | 公开内容读链路 | 9 | 本轮未复跑 | [VH01](vh01/README.md) |
| VH02 | 生命周期与双修订 | 6 | 20 | [进入学习](vh02/README.md) |
| VH03 | 认证与账号资格 | 7 | 28 | [进入学习](vh03/README.md) |
| VH04 | 幂等与并发 | 5 | 39 | [进入学习](vh04/README.md) |
| VH05 | 媒体与存储 | 5 | 22 | [进入学习](vh05/README.md) |
| VH06 | 公开投稿与门禁 | 6 | 50 | [进入学习](vh06/README.md) |

五层共 29 站，其中 VH04 D5 为 Docker 环境站。基线去重后 124 项，包含 6 个探针类的 17 项（5 类新增、VH02 原探针保留）。各层共享测试，上表数量不能直接相加。

## 学习顺序

1. 读 [通用方法](00-debug-method.md) 和对应层 README，按 05 安装探针。
2. 用 01 地图选精确测试方法，照 03 脚本 Debug，在 04 观察表先预测后填写。
3. 做 06 的三个实验，恢复后重跑基线，用 07 闭卷题检查理解。
4. 在 08 记录自己的证据，09 注释候选验证后再采用。

## 五层统一基线

```powershell
$pack = 'D:\学习资料\voidhaven-platform\docs\study-Resource\2026-09-30-debug-labs'
& "$pack\manage-probes.ps1" -Action Install -Layer all
try {
    & "$pack\run-baselines.ps1" -Layer all
} finally {
    & "$pack\manage-probes.ps1" -Action Clean -Layer all
}
```

要在 IDEA 调试时先 Install，学习结束再 Clean。脚本不覆盖已有同名文件；清理只删除脚本登记且内容未变化的副本。

## 验收与边界

学习版原地已复跑 124 项基线（全绿）并加做断点锚点行级审计：88 个锚点命中 74 个，22/29 站全覆盖。结果见 [整理验收记录](REPAIR-ACCEPTANCE.md)、[基线留证](verification/current-baseline-summary.json) 与 [断点审计留证](verification/breakpoint-audit.json)。未命中的锚点、IDEA 逐键操作、Docker 与真实 HTTP 各自保留待验收，不以单测绿色代替。

- [测试清单](00-test-inventory.md)
- [真实栈门槛](10-real-stack-boundary.md)
- [本地任务记录](REPAIR-ISSUE.md)
