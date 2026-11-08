# Changelog

## Unreleased — Forge 1.20.1 (2026-11-08)

### 中文

1. 修复：机器链接器优先响应右键，避免被动力合成器等机器收走。
2. 新增：高级封包分发器提供 36 格样板槽，每张并行卡默认提供 16 路并行。
3. 新增：动力样板供应器支持动力合成器自动布局、箭头整理、独立组投料和出口回收。
4. 新增：支持附魔工业砂轮投料，并正确识别砂轮与下方流体槽。
5. 优化：内置供应器统一使用 4 个共享升级槽，兼容并行卡与 AE2 Utility 的 NBT 撕裂卡。
6. 修复：少量作业持续回收真实产物，不再误收预存材料或不消耗的工具。
7. 修复：补刷重试按实际游戏时间计时并显示倒计时，缺料自动下单默认关闭。
8. 修复：供应器转向或重新进入存档后刷新网络连接，并正确显示升级槽和网络工具槽。
9. 优化：减少动力合成器重复规划开销，并在机器变化后重新校验布局。
10. 修复：智能倍增在回收端点发生变化后不再向旧作业追加投料。

### English

1. Fixed: The Machine Linker handles right-clicks before machines can take it as input.
2. Added: The Advanced Package Distributor provides 36 pattern slots and defaults to 16 parallel jobs per Parallel Card.
3. Added: Kinetic Pattern Providers arrange Mechanical Crafter layouts and arrows, supply independent groups, and recover products from their exits.
4. Added: Mechanical Grindstones from Enchantment Industry support input delivery and recognition of the drain below them.
5. Improved: Built-in providers share four upgrade slots for Parallel Cards and compatible AE2 Utility NBT Tear Cards.
6. Fixed: Small jobs keep collecting real products without extracting pre-existing ingredients or reusable tools.
7. Fixed: Refill retries use elapsed game time with a countdown, while automatic crafting of missing inputs remains off by default.
8. Fixed: Providers refresh ME connections after rotation or world reload and correctly display upgrade and network tool slots.
9. Improved: Mechanical Crafter planning avoids repeated searches and revalidates layouts when machines change.
10. Fixed: Smart batching no longer appends inputs to an existing job after its recovery endpoints change.

## English

## [20.1.1] - 2026-08-08

### Added

- Added the Extended Kinetic Pattern Provider, supporting up to 9 pages with 36 pattern slots per page.
- Added planning and input/output compatibility for machines from Create Crafts & Additions, Create Diesel Generators, Vintage Improvements, Create Metallurgy, Create Bitterballen, and Create Confectionery.

### Fixed

- Calculated smart batch sizes based on actual machine capacity, with improved failure compensation and rollback handling.
- Fixed item and fluid loss when the AE network could only partially accept outputs; unaccepted outputs are now persisted and retried.
- Fixed network tool slot rendering and refined the Package Distributor collision shape based on its orientation.

## [20.1.0] - 2026-07-24

### Added

- Established a dedicated build branch for Minecraft 1.20.1 / Forge 47.4.10.
- Added AE2 15.4.10 and Create 1.20.1-6.0.8 dependencies and mod metadata constraints.
- Added a Java 17 GitHub Actions workflow for builds and artifact uploads.
- Added a Chinese README, migration plan, issue templates, and pull request template.

### Migrated

- Ported registration, events, networking, capabilities, menus, and GUI APIs from NeoForge 1.21.1 to Forge 1.20.1.
- Adapted the project to the Create 6.0.8, Ponder, Flywheel/Vanillin, and AE2 15.4.10 APIs.
- Migrated the Package Distributor, Kinetic Pattern Provider, mechanical patterns, recipes, assets, and data files.
- Integrated the AE2 Pattern Workstation project's development runtime dependency tree into the ForgeGradle run configuration for compatibility testing.

### Verified

- The `clean build` task completes successfully.
- The Forge client starts successfully and loads Ponder scenes, sounds, models, and textures.
- The Forge dedicated server starts successfully and reaches the `Done` state.

### Known Notes

- This is the first stable release for the Forge 1.20.1 branch; subsequent patches follow semantic versioning.
- Not all third-party development runtime dependencies are mandatory for users; required dependencies are defined by `META-INF/mods.toml`.

---

# 更新日志

## 中文

## [20.1.1] - 2026-08-08

### 新增

- 增加扩展动力样板供应器，支持最多 9 页、每页 36 个样板槽位。
- 增加对 Create Crafts & Additions、Create Diesel Generators、Vintage Improvements、Create Metallurgy、Create Bitterballen 与 Create Confectionery 机器的规划和输入输出兼容。

### 修复

- 按机器真实容量计算智能批处理规模，并强化失败补偿和回滚。
- 修复 AE 网络只能部分接收产物时的物品/流体丢失，未接收产物会持久化并重试。
- 修复网络工具槽位显示，并按朝向细化包裹分配器碰撞箱。

## [20.1.0] - 2026-07-24

### 新增

- 建立 Minecraft 1.20.1 / Forge 47.4.10 专用构建分支。
- 加入 AE2 15.4.10 与 Create 1.20.1-6.0.8 的依赖及模组元数据约束。
- 补充适用于 Java 17 的 GitHub Actions 构建与产物上传流程。
- 补充中文 README、迁移计划、Issue 模板和 Pull Request 模板。

### 迁移

- 将 NeoForge 1.21.1 的注册、事件、网络、能力、菜单与 GUI API 迁移到 Forge 1.20.1。
- 适配 Create 6.0.8、Ponder、Flywheel/Vanillin 和 AE2 15.4.10 API。
- 迁移包裹分发器、动力样板供应器、机械样板及其配方、资源和数据文件。
- 将 AE2 Pattern Workstation 项目的开发运行时依赖树并入 ForgeGradle 运行配置，用于兼容性验证。

### 验证

- `clean build` 构建成功。
- Forge 客户端可启动并完成 Ponder、声音、模型与材质资源加载。
- Forge 专用服务器可启动至 `Done` 状态。

### 已知事项

- 这是 Forge 1.20.1 分支的首个稳定发布版本；后续补丁遵循语义化版本号。
- 第三方开发运行时依赖不是全部强制安装项；实际强制依赖以 `META-INF/mods.toml` 为准。
