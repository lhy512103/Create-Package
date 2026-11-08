# Create Package — Forge 1.20.1

Create Package 是一个连接 **Applied Energistics 2（AE2）自动合成**与 **Create 实体机械产线**的联动模组。AE2 负责发起处理样板任务，而传送带、工作盆、机械手、注液器、动力锯、动力冲压机等 Create 设备仍负责实际加工。

> 本分支专用于 Minecraft 1.20.1 Forge。NeoForge 1.21.1 版本请查看仓库的 `main` 分支。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Mod Loader | Forge 47.4.10（支持范围 `[47,)`） |
| Java | 17 |
| Applied Energistics 2 | 15.4.10（支持范围 `[15.4.10,16)`） |
| Create | 1.20.1-6.0.8（支持范围 `[6.0.8,7)`） |

Create 的构建坐标为 `maven.modrinth:LNytGWDc:8amzvn9x`。

## 主要内容

- **包裹分发器**：连接普通 AE2 样板供应器与一条 Create 序列装配产线。
- **基础包裹分发器**：内置样板库存，适合紧凑的单线路处理。
- **高级包裹分发器**：提供 36 格样板槽，通过机械包裹样板保存多条机器路径，每张并行卡默认增加 16 路并行。
- **动力样板供应器**：面向单台或多台同类 Create 机器，支持动力合成器自动布局、箭头整理、独立组投料与出口回收，也支持附魔工业砂轮。
- **机器连接器**：按物品流动顺序记录输入、加工和输出位置。
- **机械样板转换器 / 机械包裹样板**：将 AE2 处理样板与实际 Create 路线绑定。
- **Ponder 教程**：提供 8 个中英文主题、21 个分段，覆盖装配循环、16/64 台并行、粉碎轮、动力合成器、特殊工位和 FTB Ultimine 批量绑定，内置播放器无需额外安装 Ponderer。

模组仅访问已配置或已保存的位置，不通过全局实体扫描寻找产物。

内置供应器提供 4 个共享升级槽，支持并行卡与 NBT 撕裂卡；NBT 撕裂卡需要提供相应兼容接口的 Forge 版 AE2 Utility，未安装或版本不支持时不启用该集成。

补刷缺料自动下单默认关闭，可在配置中启用；护目镜和 Jade 会显示补刷重试倒计时。

## 安装

1. 安装 Minecraft 1.20.1 与 Forge 47.x。
2. 安装兼容版本的 Create 6.0.8 和 Applied Energistics 2 15.4.10。
3. 将本模组 JAR 放入实例的 `mods` 目录。
4. 启动游戏并在模组列表中确认 `Create Package` 已加载。

用户正常游玩只需安装 `mods.toml` 中声明的 Forge、Minecraft、AE2 和 Create。构建脚本中的其他 `runtimeOnly` 项目用于开发环境兼容性测试，不代表全部都是整合包的强制依赖。

## 从源码构建

先安装 JDK 17，然后执行：

```powershell
.\gradlew.bat clean build --no-daemon
```

Linux/macOS：

```bash
chmod +x ./gradlew
./gradlew clean build --no-daemon
```

构建产物位于 `build/libs/`。开发环境可使用 `runClient` 和 `runServer` 任务进行测试。

## 分支与迁移资料

- `main`：Minecraft 1.21.1 NeoForge 版本。
- `1.20.1-forge`：Minecraft 1.20.1 Forge 版本。
- [MIGRATION_PLAN_zh_CN.md](MIGRATION_PLAN_zh_CN.md)：迁移范围、API 对照、依赖树、验证结果和后续维护检查表。
- [CHANGELOG.md](CHANGELOG.md)：版本变更记录。

## 问题反馈

提交 Issue 时请附上 Minecraft、Forge、Create、AE2 与本模组的精确版本、`latest.log` 或崩溃报告、最小复现步骤；若与路线有关，请附方块布局或截图。

## 许可证

本项目使用 [MIT License](LICENSE)。第三方模组、Minecraft、Forge、Create、AE2 及其资源分别适用其各自许可证。
