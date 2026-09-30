# 开源教学仓库准备审核记录（2026-09-30）

## 审核目标

按开源教学项目标准检查本仓库：确认仓库边界、清理敏感标识、补齐 README 与贡献/安全说明、确认许可证、执行可行验证，并在推送远程前把可选清理项处理完。

## 结论

本仓库可以作为「VOIDHAVEN 学习与开源教学仓库」公开发布。它在 README 与 AGENTS 中明确标注为非产品真源、非生产发行版；测试服真实公网 IP 与正式域名已从工作区和可达 Git 历史中替换为文档示例值；许可证沿用团队既有约定的 MIT。

未完成的验证项（Docker 集成、Playwright E2E、Compose 冷启动）在下文单独列出，不计为通过。

## 仓库边界

- 本地路径：`D:\学习资料\voidhaven-platform`，分支 `main`。
- 远程：`https://github.com/podittis10011-blip/voidhaven-platform-study`（public，新建）。
- 内容：`voidhaven-platform` 源码副本 + `docs/study-Resource/` 学习材料 + `docs/learning-Resource/` 学习卡片。
- 本仓库不部署、不绑定域名、不创建外部资源。
- `.env`、`node_modules/`、`apps/api/target/`、`.release/`、崩溃日志与构建产物均在忽略规则内，不进入提交。

## 检查结果

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| 高置信度 Secret | 通过 | 私钥、GitHub/OpenAI/AWS 令牌、证书块模式 0 命中 |
| `.env` 是否入库 | 通过 | 工作区存在但从未被提交，`git log -- .env` 为空 |
| 凭据/真实数据 | 通过 | 仅保留 `.env.*.example` 占位值与 `ci-only-*` 测试值 |
| 测试服 IP 与正式域名 | 已清理 | `203.0.113.10`、`voidhaven.example` 替换，见下文脱敏 |
| 仓库体积 | 通过 | `.git` 7.7 MB；最大跟踪文件约 230 KB（学习材料插图） |
| 许可证 | 已补齐 | `LICENSE`，MIT，版权归 VOIDHAVEN Project Team |
| README | 已重写 | 面向学习与教学定位，含学习路线、目录、启动与检查 |
| 贡献与安全说明 | 已补齐 | `CONTRIBUTING.md` 重写，新增 `SECURITY.md` |
| CI 工作流 | 保留 | `quality-gate` 与 `release-candidate` 只做验证与打包，无部署任务，仅使用假值与 `.example.invalid` 域名 |

## 已完成的整理

- **脱敏**：`203.0.113.10` 替换测试服真实公网 IP（TEST-NET-3 文档地址段）；`voidhaven.example` 替换正式域名。涉及 4 个测试类与 3 篇文档，工作区与 Git 历史同步处理。
- **README**：改为「学习与开源教学仓库」定位，补充学习路线（VH00 重建索引 → 六条核心链路 → 2026-09-30 调试实验包）、目录说明、本地启动、检查命令、贡献与许可。
- **AGENTS.md**：改为教学仓库协作规则，删除「唯一真源」表述，补充学习材料与证据分类约定。
- **CONTRIBUTING.md**：改为教学项目贡献流程，明确三类证据区分和完成条件。
- **SECURITY.md**：新增漏洞报告与凭据处理边界。
- **LICENSE**：新增 MIT。
- **.gitignore**：补充 `.DS_Store`、语言缓存、临时目录与证书类文件忽略规则。
- **格式修复**：`apps/web/src/lib/api.ts` 存在既有 Prettier 不符（仅注释缩进与空行），已按 Prettier 输出规整，未改逻辑。
- **集成测试镜像修复**：MinIO 官方已下架 `minio/minio:RELEASE.2025-04-22T22-12-26Z`（Docker Hub 返回 404），首次远程 CI 在 Testcontainers 拉取镜像时失败。改为社区维护的 `coollabsio/minio` 同版本镜像并声明兼容替代，Compose 三份编排的服务端与 `mc` 客户端镜像同步替换，`quality-gate` 在跑 Maven 前预拉取镜像并重试 3 次。

## 本次实际运行的验证

通过：

- `./infra/scripts/check-repository.ps1`（仓库边界与高置信度 Secret 扫描）
- `npm run format:check`
- `npm run web:lint`
- `npm run web:test`：15 个文件、65 项通过
- `npm run web:build`
- `npm run contract:lint`
- `apps/api`：`./mvnw.cmd -B test "-Dtest=!*IT,!MySqlAndMinioIntegrationTest"`：132 项通过、0 失败、0 错误、0 跳过
- `docs/study-Resource/2026-09-30-debug-labs`：124 项基线复跑全绿，断点锚点审计 88 个命中 74 个，见该包 `verification/`

未运行（不计为通过）：

- Playwright E2E、Compose 冷启动、真实 HTTP 写入、备份与恢复、发布回滚：需要完整后端与数据库环境，本机未完成。

## 远程 CI 结果

推送后由 GitHub Actions 执行 `quality-gate`，四个任务 `repository`、`web`、`api`、`compose` 全部通过：

- 首次运行（提交 `f6f9d1b`）：`api` 失败，原因是 MinIO 官方镜像 404，已在下一提交修复。
- 修复后（提交 `b62b380`）：Surefire 133 项通过、0 失败、0 跳过（含 `MySqlAndMinioIntegrationTest` 的 MySQL 与 S3 兼容存储验证）；Failsafe `DistributedSafetyIT` 1 项通过（14.4 秒，真实 Testcontainers MySQL 并发场景）；`BUILD SUCCESS`。
- 运行记录：<https://github.com/podittis10011-blip/voidhaven-platform-study/actions/runs/36711139658>

这说明 `DistributedSafetyIT` 与 `MySqlAndMinioIntegrationTest` 在远程 Docker 环境已经跑通；本地未复跑这两项，本地结论仍以“未运行”为准。

## 已知残留

- `docs/CURRENT.md`、`docs/evidence/verification-log.md` 等同步自产品的历史资料中仍保留本机绝对路径、内部仓库名与历史 CI 标识。它们用于说明当时状态，不含凭据；如需彻底清理需另行处理并同步历史。
- `docs/study-Resource/2026-09-30-debug-labs/verification/breakpoint-audit.json` 中标为待验收的锚点仍未完成 IDEA 手工复核。

## 清理与回退

- 历史改写会改变本地提交哈希。改写前已生成 Git bundle 备份；若需回退可从该 bundle 恢复。
- 本次未创建远程前的强制推送；远程仓库为新建，首次推送不覆盖任何已有内容。
