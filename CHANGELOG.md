# 更新日志

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
