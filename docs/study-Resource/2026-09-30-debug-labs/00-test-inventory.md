# 测试清单与计数口径

VH02～VH06 去重基线 124 项，不含 Docker IT、VH01 探针或完整 HTTP 流程。

| 层 | 精确测试类 | 用例数 |
|---|---|---:|
| VH02 | `ContentItemStateTest`, `ContentManagementServiceTest`, `ContentRevisionCandidateServiceTest`, `ContentLifecycleServiceTest`, `ContentActionServiceTest`, `ContentManagementServiceReviseProbeTest` | 20 |
| VH03 | `AccountEligibilityFilterTest`, `PasswordServiceTest`, `AuthenticationRateLimiterTest`, `AccountAdministrationServiceTest`, `ActivationServiceTest`, `ProductionConfigurationValidatorTest`, `BootstrapAdminCommandTest`, `LocalDataInitializerTest`, `ClientNetworkSubjectTest`, `ApiExceptionHandlerTest`, `PasswordEncoderDebugProbeTest` | 28 |
| VH04 | `IdempotencyServiceTest`, `ContentManagementServiceTest`, `CommunityServiceTest`, `CatalogOrderServiceTest`, `HomepageAdministrationServiceTest`, `ProjectJoinServiceTest`, `PublicCaseAdministrationServiceTest`, `PublicWriteRateLimiterTest`, `PublicStatusQueryRateLimiterTest`, `AuthenticationRateLimiterTest`, `MediaOrphanCleanupServiceTest` | 39 |
| VH05 | `MediaValidatorTest`, `MediaServiceTest`, `MediaReadServiceTest`, `MediaOrphanCleanupServiceTest`, `MarkdownDocumentServiceTest`, `RichTextPolicyTest`, `PublicMediaControllerTest`, `MediaRollbackDebugProbeTest` | 22 |
| VH06 | `CommunityServiceTest`, `ProjectJoinServiceTest`, `PublicCaseAdministrationServiceTest`, `ContentActionServiceTest`, `PublicInputAdministrationServiceTest`, `PrivateValueCipherTest`, `ContentSourcePolicyTest`, `PublicCaseServiceTest`, `PublicWriteRateLimiterTest`, `PublicStatusQueryRateLimiterTest`, `ClientNetworkSubjectTest`, `PublicMediaControllerTest`, `ManualDeliveryServiceTest`, `PublicWriteInterceptorDebugProbeTest`, `PublicCaseDebugProbeTest`, `PublicContactRevealDebugProbeTest` | 50 |

探针安装位置见 probe-manifest.json；逐站方法见 station-manifest.json 与各层 01 地图。[VH01](vh01/README.md) 保持原学习入口，其用例不计入本轮 124 项。
