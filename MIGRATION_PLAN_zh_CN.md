# CreatePackage：NeoForge 1.21.1 → Forge 1.20.1 迁移计划

> 编制日期：2026-07-23  
> 源工程：`D:\Code\java\CreatePackage-neoforge-1.21.1`  
> 目标工程：`D:\Code\java\CreatePackage-forge-1.20.1`  
> Forge 构建参考：`D:\Code\java\ae2wcwt-forge-1.20.1`  
> 运行时依赖来源：`D:\Code\java\AE2-Pattern-Workstation`  
> 指定 Create 构件：`maven.modrinth:LNytGWDc:8amzvn9x`

---

## 0. 实施完成记录（2026-07-23）

> **当前结论：代码级迁移、资源格式迁移、完整依赖接入、客户端启动验证、专用服务器启动验证和发布构建均已完成。**  
> 本节是迁移后的最终状态，优先级高于本文后面保留的原始规划/风险说明。后续仍应在真实存档中执行人工玩法回归，但不再存在通过禁用业务源码绕过的迁移阻塞。

### 0.1 最终目标基线

| 项目 | 最终值 |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.10` |
| Java 目标字节码 | Java 17 / class major `61` |
| mappings | Mojang 官方映射 `official / 1.20.1` |
| AE2 | `15.4.10` |
| Create | `maven.modrinth:LNytGWDc:8amzvn9x`，实际 Create `1.20.1-6.0.8` |
| Ponder | `1.0.91` |
| Flywheel API/运行时 | `1.0.5` |
| Vanillin | `1.0.0` |

### 0.2 已完成的迁移范围

- Forge 入口、`mods.toml`、DeferredRegister、客户端初始化和事件总线已落地。
- 1.21.1 Data Components 已回退为集中管理的 1.20.1 NBT helper；机械样板和序列装配状态不再依赖 1.21 API。
- 四个 NeoForge Payload 已改写为 Forge `SimpleChannel + FriendlyByteBuf`，并保留正确的消息方向与主线程入队。
- 物品/流体能力、AE2 crafting machine、grid node host 和 `PatternProviderLogic` 能力均已用 Forge 1.20.1 capability 方式暴露。
- AE2 19.x 相关样板、菜单、持久化、网络节点 API 已迁移到 AE2 `15.4.10`。
- Create 配方、流体处理、动力方块、序列装配 NBT、客户端渲染和交互 API 已迁移到 Create `6.0.8`。
- 菜单和 GUI 已恢复，包括 Basic/Advanced Package Distributor、Kinetic Pattern Provider、Mechanical Pattern Converter 和路线高亮。
- Ponder 三个业务源码已正式恢复，并通过 `PonderIndex.addPlugin(...)` 注册；没有保留业务 `.java.disabled` 文件。
- Jade、ExtendedAE/ExtendedAE Plus 和 FTB Ultimine 兼容层已恢复。FTB Ultimine 1.20.1 使用 `PlayerInteractEvent.RightClickBlock` 构建批量链接兼容桥，不依赖 1.21 专用事件。
- 1.21 配方 JSON 已回退到 1.20.1 格式：`result.id -> item`、`transitional_item -> transitionalItem`、NeoForge 流体 ingredient -> Create 1.20.1 流体结构、`c:` 标签 -> `forge:` 标签。
- 已删除 Forge MDK 示例源码及所有残留 `.disabled` 文件。

### 0.3 运行时依赖接入审计

`D:\Code\java\AE2-Pattern-Workstation` 中所有实际 `modRuntimeOnly` 依赖均已在目标工程出现；Create 使用用户指定的精确坐标替代参考工程的普通 Create 坐标。另外保留了参考工程中以 `modImplementation` 方式进入开发运行环境的 Jade、Configuration 和 Cosmetic Armor。

特殊去重/分类规则：

1. **Registrate** 已由 Create 6.0.8 内嵌，不能再加入独立运行时 JAR，否则会出现 `Modules Registrate... export package ... to module jade` 的 JPMS 重复模块冲突。
2. **Programmed Circuit Card** 在参考工程中仅为 `modCompileOnly`，目标工程同样保持 `compileOnly`，不错误地强制加载。
3. 两套 **Just Enough Characters** 以及 Extreme Sound Muffler、caramelChat、Mouse Tweaks、Searchables、Controlling 被归入 `clientRuntimeOnly`；`runClient` 显式追加该配置，`runServer` 不加载客户端类。
4. Create 6.0.8 的 Modrinth 构件在开发 classpath 中需要显式补充 Ponder、Flywheel 和 Vanillin；版本取自 Create `mc1.20.1-6.0.8` 官方构建配置。
5. 官方 mappings 开发运行配置已启用 `mixin.env.remapRefMap` 和 `mixin.env.refMapRemappingFile`，解决 EMI 等第三方模组 SRG refmap 在 Mojang mappings 环境中的注入失败。

### 0.4 最终验证结果

| 验证项 | 结果 | 证据/说明 |
|---|---|---|
| NeoForge/1.21 API 残留扫描 | 通过 | Java/JSON/TOML/MCMeta 中未发现 `net.neoforged`、Payload、StreamCodec、DataComponentMap、`neoforge:` 等残留 |
| 业务禁用文件扫描 | 通过 | `src` 下没有 `.disabled` 文件 |
| `clean build` | 通过 | `build-final.txt`，`BUILD SUCCESSFUL` |
| 发布 JAR | 通过 | `build/libs/createpackage-0.1.0-migration.jar`；仅 Forge `mods.toml`，无 NeoForge 元数据、无嵌套第三方 JAR，class major 61 |
| `runClient` | 通过启动验证 | `run-client-06.txt`；Ponder scenes/tags 注册完成、OpenAL 和材质图集完成加载，进程保持运行直至验证超时后人工终止 |
| `runServer` | 通过启动验证 | `run-server-05.txt`；专用服务器于 `2026-07-23 17:36:51 CST` 输出 `Done (8.887s)!`，随后人工终止 |
| 本模组配方解析 | 通过 | 修复后日志不再出现任何 `createpackage:` recipe parse error |

运行验证过程中修复了三类真实问题：独立 Registrate 重复模块、EMI refmap 未重映射、客户端工具模组进入专用服务器 classpath，以及所有 1.21 配方字段格式。

### 0.5 已知非阻塞项与人工回归清单

- `clean build` 仍有 Forge 1.20.1 API 的 removal/deprecation 警告（主要是 `ResourceLocation` 构造器和 Forge context getter），不影响 Java 17 构建或运行。
- 完整依赖环境自身会输出可选兼容目标缺失、AE2 Crystal Science mixin 元数据、ExtendedAE 某 loot table、AdvancedAE 缺失模型等警告；这些警告来自外部运行时模组，未阻止客户端或服务器启动。
- 自动验证不能替代游戏内人工操作。发布前仍建议逐项测试：四种网络消息、机械样板 NBT 往返、三个分配器菜单、动力样板供应器、物品/流体路线、AE2 自动合成取消与回滚、Ponder 场景、Jade 显示、FTB Ultimine 批量链接、世界保存重载以及客户端连接专用服务器。

## 1. 迁移目标

把当前可运行于 Minecraft 1.21.1 / NeoForge 的 CreatePackage，迁移为 Minecraft 1.20.1 / Forge 47.4.10 模组，并尽量保持以下行为一致：

1. `createpackage` 模组 ID、存档数据键、注册名、资源路径保持稳定。
2. 机械包裹样板、机械样板转换器、包裹分配器、动力样板供应器、机器链接器等主体功能保留。
3. AE2 自动合成、Create 序列装配、物品/流体输入输出、GUI、网络同步和客户端高亮保留。
4. Jade、FTB Ultimine、ExtendedAE Plus 等兼容代码在对应模组存在时启用，不存在时不得导致崩溃。
5. 客户端、集成服务器、专用服务器都能启动；最终产物可通过 `gradlew build` 构建。
6. 开发运行环境包含 `AE2-Pattern-Workstation` 当前声明的全部运行时依赖，Create 使用用户指定的精确 Modrinth 坐标。

本迁移不是简单地替换 `net.neoforged` 包名。1.21.1 到 1.20.1 同时跨越了 Java 版本、Minecraft 数据组件、网络协议、能力系统、注册 API，以及 AE2/Create 的主要 API 版本，必须分阶段迁移。

---

## 2. 当前工程审计结果

### 2.1 源工程规模

| 项目 | 审计结果 |
|---|---:|
| Java 源文件 | 67 |
| Java 总行数 | 约 12,117 |
| 直接引用 NeoForge 的 Java 文件 | 30 |
| 涉及 1.21 数据组件的文件 | 6 |
| 涉及自定义 Payload/StreamCodec 的文件 | 6 |
| 明确涉及能力查询的文件 | 3 |
| `assets` 资源文件 | 47 |
| `data` 资源文件 | 11 |

最大风险文件：

- `KineticPatternProviderBlockEntity.java`：约 2,221 行；同时涉及 AE2、Create 动力、能力、菜单、配置导入导出和持久化。
- `PackageDistributorBlockEntity.java`：约 1,814 行；涉及物流状态、路由、同步和物品/流体操作。
- `MachinePlanFactory.java`、`AssemblyPlanner.java`、`SupplyActions.java`：依赖 AE2/Create 配方与存储 API。
- 四个网络 Payload：必须整体改写为 Forge 1.20.1 `SimpleChannel`。

### 2.2 目标工程现状

目标工作区原本是标准 Forge 1.20.1 MDK 模板，使用：

- ForgeGradle 6
- Forge `47.4.10`
- Java 17
- `mods.toml`

已完成的迁移准备：

- Gradle 依赖拆分为 `dependencies.gradle`，结构参考 `ae2wcwt-forge-1.20.1`。
- 仓库拆分为 `gradle/scripts/repositories.gradle`。
- `gradle.properties` 已切换为 `createpackage` 基本信息、Mojang 官方映射和迁移依赖版本。
- 已加入 `AE2-Pattern-Workstation` 的运行时依赖集合。
- Create 已固定为 `maven.modrinth:LNytGWDc:8amzvn9x`，不再用模糊版本范围。

> 历史说明：此处描述的是迁移开始时的准备状态；实际完成情况见第 0 节。

---

## 3. 版本基线与不可随意变更项

| 组件 | 目标值 | 说明 |
|---|---|---|
| Minecraft | `1.20.1` | 存档和资源格式基线 |
| Forge | `47.4.10` | 与参考工程一致 |
| Java | `17` | 1.20.1 Forge 开发与发布目标 |
| 映射 | Mojang 官方 `official / 1.20.1` | 当前目标工程实际使用的映射 |
| AE2 | `15.4.10` | 使用 Forge 构件 `appeng:appliedenergistics2-forge` |
| AE2WTLib | `15.3.3-forge` | 参考工程版本 |
| Create | `maven.modrinth:LNytGWDc:8amzvn9x` | 用户指定精确构件，优先于其他 Create 坐标 |
| 模组 ID | `createpackage` | 不允许迁移中改名 |
| Java 包名 | `com.lhy.createpackage` | 与源工程保持一致 |

规则：

1. 不把 1.21.1 的 NeoForge/MC/AE2/Create JAR 混入 1.20.1 classpath。
2. 不通过反射长期绕过核心 API；反射只允许作为可选兼容层的临时隔离手段。
3. 不在“能编译”前提下删除功能；如 1.20.1 无等价 API，必须记录替代设计。
4. 每一阶段完成后都要提交可复查的小变更，避免一次性复制 12,000 行后集中修错。

---

## 4. 推荐的目标构建树

构建树参考 `ae2wcwt-forge-1.20.1` 的“主构建脚本 + 独立依赖脚本”方式：

```text
CreatePackage-forge-1.20.1/
├─ build.gradle
├─ dependencies.gradle
├─ gradle.properties
├─ settings.gradle
├─ gradle/
│  ├─ wrapper/
│  └─ scripts/
│     └─ repositories.gradle
├─ src/
│  ├─ main/
│  │  ├─ java/com/lhy/createpackage/
│  │  └─ resources/
│  │     ├─ META-INF/mods.toml
│  │     ├─ assets/createpackage/
│  │     ├─ data/createpackage/
│  │     └─ pack.mcmeta
│  └─ generated/resources/
└─ run/
```

职责边界：

- `build.gradle`：ForgeGradle、运行配置、资源替换、JAR、编译选项。
- `dependencies.gradle`：Minecraft、编译依赖、运行依赖及注解处理器。
- `gradle/scripts/repositories.gradle`：所有 Maven 仓库。
- `gradle.properties`：唯一的版本号和模组元数据来源。

后续如依赖数量继续增长，可再升级为 `gradle/libs.versions.toml`；迁移期间不建议同时更换版本管理体系，以免增加排错变量。

---

## 5. 依赖纳入方案

### 5.1 核心依赖

以下依赖必须同时参与编译和开发运行：

- Applied Energistics 2 Forge `15.4.10`
- Create：`maven.modrinth:LNytGWDc:8amzvn9x`
- AE2 Wireless Terminals Library `15.3.3-forge`
- Glodium `1.20-1.5-forge`

### 5.2 从 AE2-Pattern-Workstation 同步的运行时集合

已在 `dependencies.gradle` 中纳入：

- JEI、EMI、Jade
- GTCEu、LDLib、Registrate、Configuration
- Curios、Cloth Config、Architectury、GuideME
- ExtendedAE、ExtendedAE Plus、AdvancedAE、Applied Flux、AE2 Crystal Science
- Mekanism、Mekanism Generators、Applied Mekanistics
- AE2 WCWT
- Cucumber、Mystical Agriculture、Mystical Agradditions
- Programmed Circuit Card、Just Enough Characters
- GeckoLib、Cosmetic Armor Reworked
- Extreme Sound Muffler、caramelChat、Mouse Tweaks、Searchables、Controlling

依赖处理原则：

1. ForgeGradle 下的模组依赖统一使用 `fg.deobf(...)`。
2. Create 只能保留一个实际版本，避免同时声明 `maven.modrinth:create:mc1.20.1-6.0.8` 和指定坐标造成重复模组。
3. JEI/EMI/Jade 的 API使用 `compileOnly`，完整模组使用 `runtimeOnly`。
4. 客户端工具后续应拆到 `clientRuntimeOnly`；第一阶段先以 `runtimeOnly` 验证完整依赖树，避免遗漏。
5. 所有兼容模组不应写入 `mods.toml` 的强制依赖；真正的硬依赖仅应是 Forge、Minecraft、AE2、Create，必要时再加入 AE2WTLib。
6. 如果某一运行时模组与 Create/AE2 目标版本冲突，先记录冲突链，再决定是否升级/降级该兼容模组，不能静默删除。

### 5.3 依赖验收命令

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat dependencies --configuration compileClasspath
.\gradlew.bat runClient
```

验收点：

- 无重复的 `create`、`ae2` 或 Forge 版本。
- 无 NeoForge 1.21.1 构件进入 classpath。
- 所有 Modrinth/CurseMaven 坐标可解析。
- 客户端启动日志中不存在 missing mandatory dependency。

---

## 6. 分阶段迁移路线

## 阶段 0：建立可回滚基线

### 操作

- [ ] 确认目标目录独立 Git 根目录；当前上级目录存在大量未跟踪工程，禁止误提交相邻项目。
- [ ] 建立 `codex/migrate-forge-1.20.1` 或等价迁移分支。
- [ ] 提交仅包含 MDK 模板、依赖脚本和本计划的基线。
- [ ] 执行 `gradlew build`，确认空模板可构建。
- [ ] 执行依赖报告，保存初始失败/冲突记录。

### 完成标准

- 模板在没有源模组代码时可构建。
- 依赖树中只有目标 1.20.1 Forge 构件。

---

## 阶段 1：资源、元数据和包结构迁移

### 操作

1. 删除模板的：
   - `src/main/java/com/example/examplemod/ExampleMod.java`
   - `src/main/java/com/example/examplemod/Config.java`
2. 复制源工程的 `assets/createpackage` 和 `data/createpackage`。
3. 复制音频、纹理、模型、语言文件和 Ponder 资源；不复制 `build`、`bin`、`run`、`.gradle`。
4. 把 `src/main/templates/META-INF/neoforge.mods.toml` 的信息人工合并到 Forge `src/main/resources/META-INF/mods.toml`。
5. 在 `mods.toml` 声明硬依赖：
   - `forge`
   - `minecraft`
   - `appliedenergistics2`
   - `create`
   - 如源码确实强制使用 AE2WTLib，则加入其真实 modId
6. 保留所有注册路径和翻译键，避免世界存档方块/物品丢失。

### 1.21 → 1.20 数据目录差异

重点检查资源类型目录名：

- 1.21 常见：`data/<modid>/recipe`、`loot_table`
- 1.20.1 常见：`data/<modid>/recipes`、`loot_tables`

必须把现有资源迁移到 1.20.1 可识别目录，并通过数据生成或游戏日志验证。不能只复制文件而不检查加载结果。

### 完成标准

- `processResources` 成功。
- 产物 JAR 中不存在 `neoforge.mods.toml`。
- 资源重载无 unknown recipe serializer、missing model、missing loot table 错误。

---

## 阶段 2：入口类、事件总线和注册系统

### 涉及文件

- `CreatePackage.java`
- `CreatePackageClient.java`
- `Config.java`
- `registry/ModBlocks.java`
- `registry/ModItems.java`
- `registry/ModBlockEntities.java`
- `registry/ModMenuTypes.java`
- `registry/ModCreativeTabs.java`
- `registry/ModSounds.java`
- `registry/ModCapabilities.java`
- `registry/ModComponents.java`

### API 替换

| NeoForge 1.21.1 | Forge 1.20.1 目标 |
|---|---|
| `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| NeoForge `IEventBus` | Forge `IEventBus` |
| `DeferredHolder` / `DeferredItem` | `RegistryObject<T>` |
| NeoForge `DeferredRegister` | Forge `DeferredRegister` |
| `IMenuTypeExtension.create(...)` | Forge 1.20.1 对应 `IForgeMenuType.create(...)` |
| `RegisterCapabilitiesEvent` 新能力注册方式 | Forge Capability/BlockEntity `getCapability` 暴露方式 |
| `DataComponents` 注册 | 删除并迁移为 NBT 数据层 |

### 实施顺序

1. 先迁移主类，只注册空的 Forge `DeferredRegister`。
2. 依次恢复 Sounds → Items → Blocks → BlockEntities → Menus → Creative Tab。
3. 每恢复一类注册表执行一次 `compileJava`。
4. `ModComponents` 不直接照搬；先建立 NBT 兼容服务，再删除 1.21 数据组件依赖。
5. 客户端监听器使用 `Dist.CLIENT` 和 Forge MOD 总线注册，禁止在服务端类加载客户端类。

### 完成标准

- 所有注册对象能在客户端注册完成。
- 专用服务器启动时不加载 `net.minecraft.client.*`。
- 注册名与 1.21.1 工程完全一致。

---

## 阶段 3：数据组件回退到 ItemStack NBT

这是迁移的首要技术风险。

### 涉及文件

- `registry/ModComponents.java`
- `MechanicalPackagePatternData.java`
- `MachineRouteData.java`
- `MechanicalPackagePatternItem.java`
- `MachineLinkerItem.java`
- 任何调用 `stack.get(component)`、`stack.set(component, value)`、`DataComponentMap` 的文件

### 目标设计

建立集中式 NBT 访问层，例如：

```text
content/pattern/PatternNbt.java
```

建议键结构：

```text
createpackage:
  pattern: {...}
  route: {...}
  encoder: {...}
  schema_version: 1
```

要求：

1. 所有 NBT 键定义为常量，禁止散落字符串。
2. 读操作对缺失、旧格式、损坏数据返回安全默认值。
3. 写操作执行 copy-on-write，避免无意义修改导致堆叠拆分。
4. `MechanicalPackagePatternData` 和 `MachineRouteData` 保留领域对象，但将 `Codec/StreamCodec` 替换为显式 `CompoundTag` 序列化方法。
5. 若 1.21.1 世界物品需要降级导入，应另外编写一次性转换工具；Minecraft 本身不保证跨大版本降级存档安全。
6. tooltip、物品名称、模型谓词等全部改从 NBT 读取。

### 测试

- 空白物品读取。
- 完整编码样板往返序列化。
- 多输入/输出及流体数据往返。
- 路由顺序、名称、标记往返。
- 物品复制、拆分、合并后的数据一致性。
- 畸形 NBT 不崩溃。

---

## 阶段 4：网络层整体改写

### 涉及文件

- `AutoRouteSearchPayload.java`
- `RouteCursorScrollPayload.java`
- `RouteMarkEditPayload.java`
- `RouteModeTogglePayload.java`
- `CreatePackage.java` 中 Payload 注册代码

### 迁移方案

NeoForge 1.21.1 的：

- `CustomPacketPayload`
- `StreamCodec`
- `RegisterPayloadHandlersEvent`
- `IPayloadContext`

改为 Forge 1.20.1 的：

- `SimpleChannel`
- `NetworkRegistry.ChannelBuilder`
- `FriendlyByteBuf`
- `messageBuilder(...).encoder(...).decoder(...).consumerMainThread(...)`
- `PacketDistributor`

建议新增统一入口：

```text
network/CreatePackageNetwork.java
```

协议约束：

1. 固定协议字符串，如 `"1"`。
2. 每个消息使用固定递增 ID，不依赖注册顺序之外的隐式状态。
3. C2S 消息必须验证发送者、菜单/持有物、维度、距离、方块实体类型和权限。
4. 所有世界修改在主线程执行。
5. 字符串、列表和搜索半径设置上限，防止恶意包造成内存或 CPU 滥用。
6. S2C 处理器只能在客户端类中触发 GUI/渲染操作。

### 完成标准

四类操作均通过真实双端环境测试：自动路由搜索、滚轮切换、路由标记编辑、路由模式切换。

---

## 阶段 5：Forge 能力系统迁移

### 涉及文件

- `MachineLinkerItem.java`
- `InventoryAccess.java`
- `SupplyActions.java`
- `KineticPatternProviderBlockEntity.java`
- `PackageDistributorBlockEntity.java`
- `ModCapabilities.java`

### API 替换

NeoForge：

```java
level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)
```

Forge 1.20.1：

```java
blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
```

注意事项：

1. Forge 返回 `LazyOptional`，必须正确处理失效和生命周期。
2. 自定义 BlockEntity 覆盖 `getCapability`，并在 `invalidateCaps` 中失效缓存。
3. 模拟插入/抽取与实际执行必须分离，避免测试阶段消耗物品。
4. 流体单位仍按 mB 处理，但要核对 Create/AE2 目标版本的转换工具。
5. 链接器判断“可链接机器”时遍历方向或使用空方向，行为必须与源版本一致。
6. 不把 `IItemHandler` 强转成具体实现。

---

## 阶段 6：AE2 19.x → 15.4.10 API 迁移

### 高风险模块

- `MechanicalPackagePatternDetails.java`
- `MechanicalPackageProcessingPatternDetails.java`
- `RoutedMechanicalPackagePatternDetails.java`
- `MechanicalPackagePatternEncodingCompat.java`
- `PatternStacks.java`
- `NetworkIO.java`
- `KineticPatternProviderBlockEntity.java`
- `KineticPatternProviderMenu.java`
- `CreatePackagePatternProviderScreen.java`
- `MechanicalPatternConverterMenu.java`

### 工作方法

1. 以本地 `appliedenergistics2-forge-15.4.10-sources.jar` 为唯一实现依据，不凭 1.21 API 名称猜测。
2. 先建立一张 AE2 类型映射表：
   - pattern details 接口及构造方式
   - `GenericStack`/AEKey/AEItemKey/AEFluidKey
   - crafting input/output 接口
   - pattern provider block entity/menu/screen
   - storage service、grid node、action source
   - menu locator、menu opener、submenu 返回
   - setting 导入/导出和持久化
3. 优先实现“样板可解码、AE2 可识别”，再接入完整机器行为。
4. 继承 AE2 类时逐一检查 protected 字段和可覆盖方法，不能假定 19.x 与 15.x 一致。
5. 对源工程中 `DataComponentMap` 的 AE2 配置导入导出，改为 1.20.1 AE2 使用的 `CompoundTag`/配置管理 API。
6. 保留原版 AE2 样板兼容，不覆写全局行为。

### 分步验收

- [ ] 自定义样板物品可创建并显示输入输出。
- [ ] AE2 能识别 processing pattern details。
- [ ] Pattern Provider 能挂网并保存状态。
- [ ] Crafting CPU 能发起任务。
- [ ] 网络库存的物品/流体可正确提取与回滚。
- [ ] 取消、缺料和异常路径不吞物品。

---

## 阶段 7：Create 6.0.x / 1.20.1 API 迁移

### 涉及区域

- `content/recipe/*`
- `content/crafting/CreateMachineIds.java`
- `MachinePlanFactory.java`
- `AssemblyPlanner.java`
- `KineticPatternProviderBlock.java`
- `KineticPatternProviderBlockEntity.java`
- `client/ponder/*`

### 检查项

1. 使用指定构件中的实际类签名核对：
   - `SequencedAssemblyRecipe`
   - deploy/press/cut/spout 等步骤类型
   - `ProcessingRecipe` 输入输出读取方式
   - kinetic block/entity 基类
   - stress impact、速度和方向 API
2. 1.21 的 `RecipeInput`、配方 holder/manager 调用回退到 1.20.1 形式。
3. 资源 JSON 的 recipe serializer/type 名称以 1.20.1 Create 为准。
4. Ponder 插件注册事件、场景 builder 和包名以目标 Create 构件为准；必要时先临时禁用 Ponder 编译单元，待主体功能稳定后恢复，但不得作为最终状态。
5. 每一种 Create 机器路线建立测试案例，验证顺序装配步骤和循环次数。

### 完成标准

- 所有 7 类装配/加工路径（以源代码实际枚举为准）均能生成计划。
- Ponder 场景加载无崩溃、无缺失注册项。
- 动力方块正确响应转速和应力网络。

---

## 阶段 8：BlockEntity、菜单和客户端界面

### 操作顺序

1. 先迁移纯数据类和菜单容器。
2. 再迁移 BlockEntity 的保存/加载、同步包和更新标签。
3. 再注册菜单类型和 screen。
4. 最后恢复 tooltip、高亮、按键和 Ponder。

### 重点差异

- 1.20.1 `saveAdditional/load` 的签名与 1.21 registry provider 版本不同。
- `MenuType` 扩展构造使用 Forge 1.20.1 API。
- GUI 网络数据不得依赖 1.21 `RegistryFriendlyByteBuf`。
- `ClientTickEvent`、按键注册和渲染事件切换为 Forge 事件包。
- 配置界面改为 Forge 1.20.1 支持的扩展点；NeoForge 的 `ConfigurationScreen` 不能照搬。

### 测试矩阵

- 单人游戏打开/关闭所有菜单。
- 服务端玩家远程操作。
- 菜单打开时方块被破坏或区块卸载。
- 重进世界后方块实体状态保持。
- 不安装客户端可选模组时仍能进入游戏。

---

## 阶段 9：兼容层恢复

### 兼容项

- Jade
- FTB Ultimine
- ExtendedAE Plus
- AE2 Wireless Terminals 及相关 AE2 附属
- JEI/EMI（如源工程后续有插件类）

### 原则

1. 每个兼容项单独包隔离。
2. 入口处使用 `ModList.get().isLoaded(...)` 判断。
3. 仅当目标存在时加载引用该模组类的兼容类，防止 JVM 提前链接。
4. `mods.toml` 中声明为 `mandatory=false`，除非已确认是核心硬依赖。
5. 每个兼容项至少执行“安装”和“不安装”两组启动测试。

---

## 阶段 10：资源、数据生成和存档兼容验证

### 检查清单

- [ ] 方块状态、模型、物品模型路径正确。
- [ ] 纹理和声音名称大小写一致。
- [ ] 中英文翻译键无遗漏。
- [ ] recipes/loot_tables 目录符合 1.20.1。
- [ ] Create 序列装配 JSON 可解析。
- [ ] 所有注册物品/方块都有对应模型和掉落表。
- [ ] `pack.mcmeta` 使用 1.20.1 资源包格式。
- [ ] 服务端数据包重载成功。
- [ ] 新建世界、保存、退出、重载后状态一致。

如果需要读取 1.21.1 生成的数据，只承诺物品 NBT 的显式转换；不承诺整张 1.21.1 世界直接降级到 1.20.1。

---

## 阶段 11：测试、打包和发布门禁

### 每日快速检查

```powershell
.\gradlew.bat compileJava
.\gradlew.bat processResources
```

### 阶段检查

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

### 最终测试矩阵

| 场景 | 必须通过 |
|---|---|
| 最小依赖：Forge + AE2 + Create + 必需库 | 是 |
| 完整 AE2-Pattern-Workstation 运行依赖集合 | 是 |
| 单人游戏 | 是 |
| 独立客户端连接专用服务器 | 是 |
| 无 Jade/JEI/EMI 等可选模组 | 是 |
| 物品路线 | 是 |
| 流体路线 | 是 |
| 多机器顺序路由 | 是 |
| 自动合成取消/缺料/回滚 | 是 |
| 世界保存并重新加载 | 是 |

### 发布门禁

- [ ] `build` 成功且无编译警告中的潜在服务端类加载问题。
- [ ] JAR 中只有 Forge `mods.toml`。
- [ ] JAR 不包含其他模组完整 JAR，除非明确使用合法且必要的 Jar-in-Jar。
- [ ] `mods.toml` 版本范围与真实测试版本一致。
- [ ] 许可证和第三方依赖许可已复查。
- [ ] CHANGELOG 说明这是 1.20.1 Forge 移植版。

---

## 7. 建议的实际提交序列

为方便回查，建议按下列粒度提交：

1. `build: 建立 Forge 1.20.1 依赖与仓库结构`
2. `docs: 添加 1.20.1 迁移计划`
3. `chore: 迁移模组元数据与静态资源`
4. `port: 迁移 Forge 入口与注册表`
5. `port: 将物品数据组件回退为 NBT`
6. `port: 将 payload 网络层迁移到 SimpleChannel`
7. `port: 迁移物品与流体能力访问`
8. `port: 迁移 AE2 样板领域模型`
9. `port: 迁移 AE2 网络与样板供应器`
10. `port: 迁移 Create 配方规划与动力方块`
11. `port: 恢复菜单、界面、按键与高亮`
12. `port: 恢复 Ponder 与可选兼容层`
13. `test: 完成客户端和专用服务器回归`
14. `release: 整理元数据、许可证和发布产物`

每个提交应满足：至少 `compileJava` 通过，或在提交说明中明确记录当前唯一阻塞点和下一步。

---

## 8. 风险清单与应对

| 风险 | 等级 | 应对 |
|---|---|---|
| 1.21 Data Component 在 1.20.1 不存在 | 极高 | 集中迁移为版本化 NBT，先写往返测试 |
| AE2 19.x 与 15.x API 差异 | 极高 | 以 15.4.10 sources 为依据，按功能切片迁移 |
| 大型 BlockEntity 类一次性报错过多 | 极高 | 先抽领域逻辑，再逐接口恢复；避免全文件机械替换 |
| NeoForge Payload API 不兼容 | 高 | 统一改为 SimpleChannel，不做兼容包装 |
| NeoForge capability API 不兼容 | 高 | 改为 ForgeCapabilities + LazyOptional 生命周期 |
| Create 精确构件及传递依赖冲突 | 高 | 锁定单一 Create，检查 runtimeClasspath |
| 1.21 数据目录在 1.20.1 不识别 | 高 | 迁移 recipe/loot_table 目录并进行资源重载测试 |
| 客户端类在专用服务器加载 | 高 | 客户端入口和事件监听物理隔离 |
| 完整运行时依赖导致重复模组/版本冲突 | 中高 | 先输出依赖树，再逐个解决，不静默排除核心库 |
| 存档跨版本降级损坏 | 高 | 只承诺新 1.20.1 世界；旧数据使用显式转换工具 |
| 可选兼容类提前链接 | 中 | ModList 判断 + 独立兼容入口 |

---

## 9. 迁移过程中必须维护的复查记录

建议在本文末尾持续追加以下表格，而不是另建零散笔记：

### 9.1 API 差异记录

| 日期 | 文件/模块 | 1.21.1 API | 1.20.1 替代 | 状态 |
|---|---|---|---|---|
| 2026-07-23 | 构建系统 | NeoForge ModDev | ForgeGradle 6 | 已完成 |
| 2026-07-23 | ItemStack 数据 | Data Component | CompoundTag/NBT helper | 已完成 |
| 2026-07-23 | 网络 | Payload + StreamCodec | SimpleChannel + FriendlyByteBuf | 已完成 |
| 2026-07-23 | 能力 | NeoForge Capabilities | Forge Capability + LazyOptional | 已完成 |
| 2026-07-23 | AE2 | AE2 19.x API | AE2 15.4.10 API | 已完成 |
| 2026-07-23 | Create | Create 1.21 API/数据格式 | Create 6.0.8 API/1.20.1 配方格式 | 已完成 |
| 2026-07-23 | Ponder | Create 内部旧包路径 | 独立 Ponder 1.0.91 API | 已完成 |
| 2026-07-23 | FTB Ultimine | RegisterRightClickHandlerEvent | Forge RightClickBlock 兼容桥 | 已完成 |

### 9.2 阶段状态

| 阶段 | 状态 | 验证证据 |
|---|---|---|
| 0. 构建基线 | 已完成 | `clean build` 成功，依赖树已解析 |
| 1. 资源/元数据 | 已完成 | Forge `mods.toml` 与 1.20.1 资源目录有效 |
| 2. 入口/注册 | 已完成 | 客户端与专用服务器均完成模组初始化 |
| 3. 数据组件→NBT | 已完成 | `ModComponents` NBT helper 已替代 Data Components |
| 4. 网络层 | 已完成 | 四个消息已迁移到 SimpleChannel |
| 5. 能力系统 | 已完成 | Forge item/fluid、AE2 crafting/grid/logic capability 已接入 |
| 6. AE2 API | 已完成 | AE2 15.4.10 编译和启动验证通过 |
| 7. Create API | 已完成 | Create 6.0.8、Ponder/Flywheel/Vanillin 启动验证通过 |
| 8. GUI/客户端 | 已完成 | Screen、渲染、高亮和 Ponder 注册均完成 |
| 9. 兼容层 | 已完成 | Jade、ExtendedAE、FTB Ultimine 兼容代码已恢复 |
| 10. 资源/存档 | 已完成 | 1.20.1 配方解析通过；NBT 格式已回退 |
| 11. 自动化回归 | 已完成 | build、runClient、runServer 均通过启动级验证；玩法级人工回归见 0.5 |

---

## 10. 原始执行顺序（已完成）

以下为迁移开始时制定并已实际执行完毕的顺序，保留用于复盘：

1. 运行 `runtimeClasspath` 和 `compileClasspath` 依赖解析，修复构件坐标或版本冲突。
2. 让空模板完成 `gradlew build` 和 `runClient`。
3. 复制资源并把 1.21 数据目录改成 1.20.1 目录形式。
4. 迁移入口类和所有注册表，但暂不复制大型 BlockEntity 实现。
5. 建立 `PatternNbt`，优先解决数据组件回退。
6. 建立 `CreatePackageNetwork`，逐个迁移四个消息。
7. 再迁移能力访问、AE2 领域模型、Create 配方规划。
8. 最后接入两个大型 BlockEntity、GUI、Ponder 和兼容层。

此顺序的核心目的，是让每一个高风险模块都有独立、可编译、可验证的边界，避免在同一次构建中同时面对 NeoForge、Minecraft、AE2 和 Create 四层 API 错误。
