# 整理验收记录

已将 package 直接合入当前学习包，覆盖重名新版文档，补齐五层 08 证据台账、统一入口与运行脚本。不创建归档；VH01 与其他原有文件保留。

## 学习版原地结果（2026-09-30）

| 项目 | 结果 | 证据 |
|---|---|---|
| 基线 124 项 | 通过：失败 0 / 错误 0 / 跳过 0 | verification/current-baseline-summary.json |
| 6 个探针 | 安装后运行，结束已清理，工作区无残留 | 同上；清理后 apps 只剩原有学习批注改动 |
| 断点锚点行级审计 | 部分通过：88 个锚点命中 74 个，22/29 站全覆盖 | verification/breakpoint-audit.json |
| 实验结果 15 项 | 符合预期，原字节已恢复 | verification/experiment-summary.json（此前隔离副本） |

基线命令就是 README 里那条：Install → run-baselines → Clean，运行目录为 `D:\学习资料\voidhaven-platform\apps\api`。

## 仍未验收

| 项目 | 状态 | 说明 |
|---|---|---|
| IDEA 逐键单步 | 待学习者验收 | 材料给出站点脚本；自动化审计只证明断点被命中，不证明手顺 |
| 未命中的断点锚点 | 待手工复核 | VH02-D5、VH05-D1、VH05-D2、VH03-D5-B1、VH06-D5-B4 在自动审计中无断点事件，按待验收保留 |
| VH03-D4-B3 驱动归属 | 待核对 | 锚点被执行到，但驱动来自 AccountEligibilityFilterTest，不在该站 drivers 清单内 |
| Docker（VH04 D5、VH05 集成） | BLOCKED_ENV | 需要 Docker 引擎，未执行 |
| 真实 HTTP / CSRF / Session / grant | 未执行 | 见 10-real-stack-boundary.md |

## 本次修正

VH03-D2-B1 的锚点文本原为 `@Bean PasswordEncoder passwordEncoder()`，会先匹配到学习批注块里的同名注释行（第 29 行），导致就近取可执行行时落到 SecurityConfig 构造函数。已改为 `passwordEncoder() { return Argon2PasswordEncoder`，唯一命中真实方法第 40 行，station-manifest.json 与 vh03/01-debug-walkthrough.md 同步更新。
