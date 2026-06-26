# Changelog

## 2026-06-26

- Added the Advanced Package Distributor as a real `advanced_package_distributor` block again: it embeds AE2 pattern-provider logic but routes each job from the saved machine links inside a Mechanical Package Pattern, allowing one distributor to store patterns for multiple assembly lines. Parallel line execution is still deferred.
- Added Mechanical Package Patterns backed by AE2's custom encoded-pattern API. They preserve the original AE2 encoded pattern's inputs/outputs while carrying a Create machine-link route snapshot for the Advanced Package Distributor.
- Added the handheld Mechanical Pattern Converter with a small conversion GUI: right-click a linked distributor to bind the route source, right-click air to open the converter, insert a normal encoded AE2 pattern, and convert it into a Mechanical Package Pattern.
- Made active distributor jobs persist and refill against the route captured at job start, so probability refills and transitional-item reflow keep using the same per-pattern route even if the block's current links later change.
- Relaxed the runtime NeoForge dependency range to 21.1.230 and newer while keeping the development compile version separate.
- Changed the Machine Linker item model to inherit Create's wrench model so it renders with the same 3D geometry and item transforms instead of a flat generated texture.
- Fixed non-consuming deployer held items in sequenced assembly patterns: `keep_held_item` ingredients are now treated as reusable tools, optional extra pattern inputs are returned to the AE2 network, and the distributor can preload one from network storage when needed.
- Renamed the embedded-pattern-provider distributor from `advanced_package_distributor` to `basic_package_distributor`, including its registry id, resources, recipes, and Java symbols.
- Added a Basic Package Distributor sequenced-assembly test recipe using a Package Distributor base, reusable AE2 printed circuits in deployers, mechanical pressing, and lava filling.
- Added automatic transitional-item reflow for multi-loop sequenced assembly: current-recipe intermediate items are moved from the output link back to the input link while preserving Create's sequenced-assembly data component.
- Added a waiting status for blocked transitional reflow so goggles/Jade report when the input link cannot accept the intermediate item yet.
- Added the Basic Package Distributor, combining the existing distributor pipeline with an embedded AE2 pattern-provider inventory so patterns can be stored in the distributor itself.
- Added registration, block/item models, loot table, language entries, creative-tab entry, network-tool representation, and a shaped recipe for the Basic Package Distributor.
- Fixed Machine Linker interaction with the Basic Package Distributor: holding the linker now selects/clears links instead of opening the embedded pattern-provider GUI.
- Changed the active job overlay/Jade line to show the primary output display name and refill round instead of the raw sequenced-assembly recipe id.
- Added probabilistic-output refill handling: secondary outputs trigger an immediate refill round, empty-output rounds refill after a configurable timeout, and missing refill inputs are retried without world scanning.
- Added Package Distributor sequenced-assembly test recipes covering spout fluid filling, mechanical cutting, mechanical pressing, and deployer held-item supply, plus an incomplete distributor transitional item.
- Added Package Distributor diagnostics for Create Engineer's Goggles and optional Jade: current status, last rejection reason, active job, AE node state, linked machine names, roles, icons, and positions are now visible in-game.
- Recorded the distributor's last pattern rejection reason and synchronized linked-machine/job state to clients so blocking crafts can be diagnosed without checking logs first.
- Registered the Package Distributor's AE2 network-tool representation and synchronized server-side AE node state to the overlay; deployer/spout consumables can now be satisfied by either the AE2 pattern inputs or existing machine contents.
- Replaced the NeoForge template README with bilingual project documentation covering Package Distributor setup, link order, current AE2 processing-pattern usage, limitations, and performance design.
- Switched project metadata and repository licensing to MIT, added a root LICENSE file, and removed the template license placeholder.
- Implemented the first real package distributor job pipeline: AE2 pattern pushes are now planned, validated, supplied to linked Create machines, and tracked until the primary output is recovered.
- Added low-overhead runtime behavior: no world scanning, no mixins or reflection; the distributor only touches linked block positions and only wakes its AE2 grid ticker while a job is active.
- Added conservative safety checks for incomplete lines, ambiguous recipes, same input/output depot links, unused pattern inputs, and output recovery timeouts.

## 2026-06-26 中文

- 重新加入真正的高级封包分发器 `advanced_package_distributor`：它内置 AE2 样板供应器逻辑，但每个作业按机械封包样板里保存的机器链接路线执行，使一个分发器可以存放多条装配线的样板；多装配线并行运行后续再实现。
- 增加机械封包样板，基于 AE2 自定义 encoded-pattern API 实现：保留原 AE2 已编码样板的输入/输出，同时携带转换时的 Create 机器链接路线快照，供高级封包分发器路由。
- 增加手持机械样板转换器和简易转换 GUI：右键已配置链接的分发器绑定路线来源，空中右键打开界面，放入普通 AE2 已编码样板后可转换为机械封包样板。
- 活动作业现在会保存接单时捕获的路线，几率补刷和中间产物回流都会继续使用同一条样板路线，不会因方块当前链接后续变化而跑错装配线。
- 放宽运行时 NeoForge 依赖范围为 21.1.230 及以上，并将其与开发编译版本分离。
- 将机器链接器物品模型改为继承 Create 扳手模型，使其使用相同的 3D 几何和物品显示变换，不再只是平面贴图。
- 修复序列组装样板中的非消耗 Deployer 施加物处理：`keep_held_item` 原料现在会被视为可复用工具，样板中额外写入的同类输入会退回 AE2 网络，必要时分发器也能从网络库存预装 1 个到机械手。
- 将内置样板供应器的分发器从 `advanced_package_distributor` 改为 `basic_package_distributor`，同步更新注册名、资源、配方和 Java 符号，为后续真正的高级封包分发器预留 ID。
- 增加基础封包分发器序列组装测试配方：以封包分发器为基础，机械手使用不消耗的 AE2 已压印电路板，并包含机械辊压和熔岩注液步骤。
- 增加多循环序列组装的中间产物自动回流：当前配方的中间产物会从终点链接回到起点链接，并保留 Create 的序列组装数据组件。
- 增加中间产物回流受阻状态，起点无法接收中间产物时护目镜/Jade 会直接显示等待原因。
- 增加基础封包分发器：把现有分发器投料管线和 AE2 样板供应器库存组合在同一个方块里，样板可以直接放入分发器本体。
- 补齐基础封包分发器的注册、方块/物品模型、掉落表、语言、创造栏、AE 网络工具代表物品和有序合成配方。
- 修复机器链接器与基础封包分发器的交互：手持链接器右键时会选中/清空链接，不再打开内置样板供应器界面。
- 将活动作业在护目镜/Jade 中显示为主产物名称和当前补刷轮次，不再直接显示序列组装配方 id。
- 增加几率产物补刷处理：收到副产物会立即补刷，完全空产出会在可配置超时后补刷，缺少补刷材料时会低频重试且不扫描世界。
- 增加封包分发器序列组装测试配方，覆盖 Spout 流体填充、机械切割、机械辊压和 Deployer 施加物供料，并新增未完成的封包分发器过渡物品。
- 为封包分发器新增 Create 工程师护目镜诊断显示和可选 Jade 信息显示：现在可在游戏内看到当前状态、最近拒单原因、活动作业、AE 节点状态、已链接机器名称、角色、图标和坐标。
- 记录分发器最近一次样板拒单原因，并把链接机器/作业状态同步到客户端，排查阻塞合成时不必先翻日志。
- 注册封包分发器在 AE2 网络工具中的代表物品，并把服务端 AE 节点状态同步到信息显示；Deployer/Spout 耗材现在可由 AE2 样板输入或机器内已有内容共同满足。
- 将 NeoForge 模板 README 替换为中英双语项目文档，补充分发器搭建流程、链接顺序、当前 AE2 加工样板用法、限制与低开销设计说明。
- 将项目元数据和仓库许可证改为 MIT，新增根目录 LICENSE 文件，并移除模板许可证占位文件。
- 实现第一版封包分发器真实作业管线：AE2 样板推送会被解析、校验、投料到已链接的 Create 机器，并等待主产物从终点回收到 AE2 网络。
- 保持低开销运行方式：不扫描世界、不使用 mixin/反射；分发器只访问玩家已链接的位置，并且只有存在作业时才唤醒 AE2 网格 tick。
- 增加保守安全检查：流水线不完整、配方歧义、起点/终点相同、多余样板输入、产物回收超时都会拒单或记录日志，避免丢物品。
