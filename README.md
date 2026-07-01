# Create Package

Create Package connects Applied Energistics 2 autocrafting to real Create machines. It is designed for players who want AE2 to request a craft while Create still performs the physical processing with belts, depots, deployers, spouts, presses, saws, basins, power, and item movement.

Instead of replacing Create contraptions with an abstract crafting block, this mod adds routing devices that receive AE2 processing-pattern jobs, supply the selected Create machine positions, watch the configured output positions, and return recovered products to the ME network.

Repository: https://github.com/lhy512103/Create-Package.git

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Applied Energistics 2 19.2.x
- Create 6.0.x

## Core Ideas

Create Package has two main crafting paths:

- **Sequenced assembly routing** for multi-step Create lines. Use Package Distributors, route tools, and Mechanical Package Patterns.
- **Single-machine processing** for one Create machine or several identical machines. Use the Kinetic Pattern Provider.

The mod uses AE2 processing patterns as the crafting contract. It validates and simulates target insertions before spending inputs whenever possible. It does not scan the world for dropped item entities; outputs must be recoverable through configured depots, belts, basins, inventories, or machine handlers.

Runtime behavior is intentionally narrow: devices touch their configured or saved positions, sleep while idle, and avoid mixins, reflection, and world scanning.

## Blocks and Items

### Package Distributor

The Package Distributor is the external target for a normal AE2 Pattern Provider.

Use it when you already have an AE2 Pattern Provider and want one linked Create sequenced-assembly route. Place the Package Distributor on the ME network, place an AE2 Pattern Provider next to it, put processing patterns in the AE2 Pattern Provider, then link the physical Create line with the Machine Linker.

The linked route is interpreted as:

- first depot or belt: input position;
- deployers and spouts: supplied in linked order;
- last depot or belt: output recovery position.

Pressing, cutting, and other passive Create processing steps are performed by the real Create line. They usually do not need to be linked unless they are also an inventory target required by the route.

### Basic Package Distributor

The Basic Package Distributor combines a Package Distributor with an embedded AE2 pattern-provider inventory.

Use it when you want a compact single-route distributor. Put AE2 processing patterns directly into the Basic Package Distributor GUI. It still uses the Machine Linker route saved on the block, but it does not need a separate AE2 Pattern Provider beside it.

It is best for one physical sequenced-assembly line at a time.

### Advanced Package Distributor

The Advanced Package Distributor stores patterns for multiple saved routes in one block.

It does not use the Machine Linker route on the block itself. Instead, each Mechanical Package Pattern carries its own saved machine route. Put Mechanical Package Patterns into the Advanced Package Distributor GUI, and the distributor will execute each job using the route stored inside that pattern.

Parallel Cards allow the Advanced Package Distributor to run multiple jobs at once when their saved routes do not overlap:

- 0 Parallel Cards: 1 active line;
- 1 Parallel Card: up to 2 active lines;
- 2 Parallel Cards: up to 4 active lines.

This is the preferred option for a shared AE2 interface that manages several independent Create assembly lines.

### Machine Linker

The Machine Linker stores ordered machine routes.

For Package Distributors and Basic Package Distributors:

1. Right-click the distributor with the Machine Linker to select it.
2. Right-click route blocks in item-flow order.
3. Sneak-right-click a linked block to remove it.
4. Sneak-right-click the selected distributor to clear all links.

For Kinetic Pattern Providers with Parallel Cards:

1. Right-click the Kinetic Pattern Provider to select it.
2. Right-click matching machines of the same block type as the provider's target machine.
3. Sneak-right-click linked machines to unlink them.

When FTB Ultimine shape selection is available, the Machine Linker can batch-link or batch-unlink matching machines without scanning the world globally.

### Mechanical Pattern Converter

The Mechanical Pattern Converter marks routes and converts normal AE2 encoded processing patterns into Mechanical Package Patterns.

Use it for Advanced Package Distributor workflows:

1. Right-click route blocks in order while holding the converter.
2. Sneak-right-click a marked block to remove it.
3. Sneak-right-click air to clear the current route.
4. Right-click air to open the converter GUI.
5. Insert an AE2 encoded processing pattern or an existing Mechanical Package Pattern.
6. Press Convert to write the converter's current route into the output pattern.

The converter shows the marked route in its tooltip. Hold Shift to expand machine names and coordinates. Holding the converter in-world highlights the current route.

Crafting recipe: one Create Precision Mechanism plus one AE2 Blank Pattern.

### Mechanical Package Pattern

Mechanical Package Patterns are AE2 processing patterns with an additional Create route snapshot.

They preserve the original AE2 encoded pattern and add route data for the Advanced Package Distributor. This means one Advanced Package Distributor can store patterns for different physical lines, and each pattern knows where it should run.

Mechanical Package Patterns can also be edited in-world:

- right-click a markable route block to append it to the pattern route;
- sneak-right-click a route block to remove it;
- hold the pattern to highlight its saved route;
- put it back into the Mechanical Pattern Converter to rewrite the route while preserving the original AE2 pattern.

### Kinetic Pattern Provider

The Kinetic Pattern Provider is for single-machine Create processing.

It embeds AE2 Pattern Provider behavior and faces one configured target side. Use a wrench to set the side that points at the Create machine. Put normal AE2 processing patterns into the provider GUI, connect it to the ME network, and request the craft from AE2.

Supported routing includes:

- **Deployer**: first item input goes to the working position two blocks along the deployer's facing direction; second item input goes into the deployer's held-item inventory; output is recovered from the working position.
- **Spout**: item input goes to the working position two blocks below the spout; fluid input goes into the spout; output is recovered from that working position.
- **Mechanical Press**: with a basin two blocks below, all item and fluid inputs go into the basin and outputs are recovered from the basin. Without a basin, one item input goes to the depot, belt, or inventory two blocks below.
- **Mechanical Mixer**: requires a basin two blocks below. Inputs and outputs use that basin.
- **Mechanical Saw**: the saw must face up and be running. The provider sets the saw filter from the pattern's primary output, inserts the input, and recovers from the saw's fixed output side.
- **Millstone**: input goes into the millstone and actual outputs, including probabilistic byproducts, are recovered.
- **Crushing Wheel**: target the `create:crushing_wheel_controller`, not the wheel block. The controller must output into a recoverable belt, depot, or inventory.
- **Generic item handlers**: one item input goes into the target machine inventory and expected outputs are recovered from it.

Parallel Cards let one Kinetic Pattern Provider distribute jobs to multiple identical machines:

- 0 Parallel Cards: front target only;
- 1 Parallel Card: up to 16 active machines;
- 2 Parallel Cards: up to 32 active machines.

Smart Doubling can be enabled per provider. When enabled, matching processing-pattern pushes can be batched into the current active job. If the target machine cannot currently receive another input set, the provider keeps a lightweight internal pending-dispatch queue and tries again while the job is active. Pending inputs are persisted with the job and returned through AE2 return inventory if the craft is cancelled before dispatch.

### Parallel Card

Parallel Cards upgrade the Advanced Package Distributor and Kinetic Pattern Provider.

In the Advanced Package Distributor, they increase the number of non-overlapping saved routes that can run at the same time. In the Kinetic Pattern Provider, they unlock linked identical machines for multi-machine dispatch.

Parallel Cards stack in normal inventories, but AE2 upgrade slots still accept one card per slot.

### Incomplete Package Distributor

The Incomplete Package Distributor is the transitional item used by the mod's Create sequenced-assembly crafting recipes.

It is not a normal tool or machine. It exists so the mod's own blocks can be crafted through Create assembly chains.

## Pattern Rules

Create Package currently uses normal AE2 processing patterns.

For sequenced assembly, encode:

- one base input item;
- consumed deployer items for the full loop count;
- spout fluids for the full loop count;
- the final primary output.

For Precision Mechanism-style recipes, the base item is consumed once and the intermediate item loops through the line. Do not multiply the base item by the number of loops.

Example Precision Mechanism pattern:

- inputs: `1 Gold Sheet`, `5 Cogwheels`, `5 Large Cogwheels`, `5 Iron Nuggets`;
- output: `1 Precision Mechanism`.

AE2 processing patterns do not have probabilistic-output semantics. Do not put optional byproducts in the pattern output list unless AE2 should wait for them as required outputs. Create Package still recovers actual byproducts when they appear.

## Sequenced Assembly Behavior

Package Distributors match the pattern's primary output to a Create sequenced-assembly recipe. If multiple recipes match or required inputs are missing, the job is rejected before consuming inputs.

During a job, the distributor:

1. validates the route and pattern inputs;
2. simulates all target insertions;
3. supplies base items, deployer held items, and spout fluids;
4. watches the configured output position;
5. returns recovered outputs to AE2.

For multi-loop sequenced assembly, Create's transitional item keeps recipe progress data. When the distributor sees the current job's transitional item at the output position, it sends that same item stack back to the input position without stripping its progress data.

For probabilistic primary outputs, the distributor can refill another input round from AE storage when byproducts or empty-output timeouts show that the primary output has not arrived yet.

## Diagnostics

Engineer’s Goggles and Jade show useful runtime state:

- AE node power, channel, and online status;
- current status and rejection reason;
- active job output and remaining amount;
- route links or saved Mechanical Package Pattern routes;
- parallel-card capacity and active job count;
- Kinetic Pattern Provider target machine and linked parallel machines.

These diagnostics are meant to make blocked crafts understandable without checking logs first.

## Limitations

- Create machines must be powered and physically able to move items.
- Outputs must enter configured recoverable positions. Dropped item entities are not scanned.
- Package Distributor and Basic Package Distributor each run one route at a time.
- Advanced Package Distributor parallelism only runs routes that do not overlap.
- Kinetic Pattern Provider is for single-machine processing, not multi-step sequenced assembly.
- Current pattern matching is conservative and may reject ambiguous setups to avoid consuming the wrong inputs.

## License

Create Package is licensed under the MIT License. See [LICENSE](LICENSE).

## Third-party Notices

### Modular Routers sound assets

Create Package includes the following sound assets derived from Modular Routers by desht:

- `assets/createpackage/sounds/machine_linker_success.ogg`, from `assets/modularrouters/sounds/success.ogg`
- `assets/createpackage/sounds/machine_linker_error.ogg`, from `assets/modularrouters/sounds/error.ogg`
- `assets/createpackage/sounds/machine_linker_thud.ogg`, from `assets/modularrouters/sounds/thud.ogg`

Modular Routers is distributed under the MIT License. Copyright (c) 2016 Des Herriott.

Source: https://github.com/desht/ModularRouters

The Modular Routers README identifies its sounds as free sound assets with these source licenses:

- Scrampunk, https://freesound.org/people/Scrampunk/sounds/345297/ - Creative Commons Attribution 4.0
- Autistic Lucario, https://freesound.org/people/Autistic%20Lucario/sounds/142608/ - Creative Commons Attribution 4.0
- Reitanna, https://freesound.org/people/Reitanna/sounds/332661/ - Creative Commons 0

No code from Modular Routers' Botania-derived `me.desht.modularrouters.client.fx` package is included.

## Acknowledgements

Special thanks to **xiaoleng5261** for providing the models, textures, and matching JSON files used by Create Package.

# 机械动力封包

机械动力封包把 Applied Energistics 2 自动合成连接到真实的 Create 机器。它适合希望“由 AE2 下单、由 Create 实体流水线加工”的玩法：传送带、料盘、机械手、注液器、冲压机、动力锯、工作盆、动力系统和物品流仍然由玩家搭建。

这个模组不会用一个抽象机器替代 Create 生产线，而是提供一组路由方块：接收 AE2 处理样板作业，把材料送到指定的 Create 位置，监听配置好的产物位置，并把回收到的产物送回 ME 网络。

仓库地址：https://github.com/lhy512103/Create-Package.git

## 运行需求

- Minecraft 1.21.1
- NeoForge 21.1.x
- Applied Energistics 2 19.2.x
- Create 6.0.x

## 核心思路

机械动力封包主要有两条自动合成路径：

- **序列组装路线**：用于多步骤 Create 流水线，使用封包分发器、路线工具和机械封包样板。
- **单机器处理**：用于单台 Create 机器或多台同类型机器，使用动力样板供应器。

模组使用 AE2 处理样板作为合成契约。设备会尽量在消耗输入前校验并模拟目标插入。模组不会扫描世界里的掉落物实体；产物必须进入可回收的料盘、传送带、工作盆、库存或机器处理器。

运行时行为保持克制：只访问已配置或已保存的位置，空闲时睡眠，不使用 mixin、反射或世界扫描。

## 方块与物品

### 封包分发器

封包分发器是普通 AE2 样板供应器的外部目标。

当你已经有 AE2 样板供应器，并且只想让它驱动一条 Create 序列组装路线时，使用这个方块。把封包分发器接入 ME 网络，在旁边放置 AE2 样板供应器，把处理样板放进 AE2 样板供应器，然后用机器链接器链接真实的 Create 流水线。

链接路线按以下方式解释：

- 第一个料盘或传送带：输入位置；
- 机械手和注液器：按链接顺序供料；
- 最后一个料盘或传送带：产物回收位置。

冲压、切割等被动物理加工由真实 Create 流水线完成。通常不需要链接这些加工机器，除非它们也是路线需要访问的库存目标。

### 基础封包分发器

基础封包分发器把封包分发器和 AE2 样板供应器库存合在一个方块里。

它适合更紧凑的单路线方案。把 AE2 处理样板直接放进基础封包分发器 GUI。它仍然使用机器链接器保存在方块上的路线，但不需要旁边额外放一个 AE2 样板供应器。

它最适合一次运行一条物理序列组装线。

### 高级封包分发器

高级封包分发器可以在一个方块里存放多条已保存路线的样板。

它不使用方块自身的机器链接器路线，而是读取每张机械封包样板中保存的路线。把机械封包样板放进高级封包分发器 GUI 后，每个作业都会按照对应样板内部保存的路线执行。

并行卡可以让高级封包分发器同时运行多条互不重叠的路线：

- 0 张并行卡：1 条活动路线；
- 1 张并行卡：最多 2 条活动路线；
- 2 张并行卡：最多 4 条活动路线。

如果你希望一个 AE2 入口管理多条独立 Create 装配线，优先使用高级封包分发器。

### 机器链接器

机器链接器用于保存有序机器路线。

用于封包分发器和基础封包分发器时：

1. 手持机器链接器右键分发器以选中它。
2. 按物品流动顺序右键路线方块。
3. 潜行右键已链接方块可移除。
4. 潜行右键已选中的分发器可清空全部链接。

用于安装了并行卡的动力样板供应器时：

1. 右键动力样板供应器以选中它。
2. 右键与供应器目标机器同方块类型的机器。
3. 潜行右键已链接机器可取消链接。

如果安装了 FTB Ultimine 并存在形状选择，机器链接器可以批量链接或取消同类机器，而不进行全世界扫描。

### 机械样板转换器

机械样板转换器用于标记路线，并把普通 AE2 已编码处理样板转换成机械封包样板。

高级封包分发器的常用流程：

1. 手持转换器，按顺序右键路线方块。
2. 潜行右键已标记方块可移除。
3. 潜行右键空气可清空当前路线。
4. 右键空气打开转换器 GUI。
5. 放入 AE2 已编码处理样板或已有机械封包样板。
6. 点击转换，把转换器当前路线写入输出样板。

转换器 tooltip 会显示已标记路线。按住 Shift 可展开机器名称和坐标。手持转换器时，世界中会高亮当前路线。

合成配方：1 个 Create 精密构件加 1 个 AE2 空白样板。

### 机械封包样板

机械封包样板是带有 Create 路线快照的 AE2 处理样板。

它保留原始 AE2 已编码样板，同时额外保存供高级封包分发器使用的路线数据。因此一个高级封包分发器可以存放多条不同物理线路的样板，每张样板都知道自己应该在哪条线路运行。

机械封包样板也可以在世界中直接编辑：

- 右键可标记路线方块，将其追加到样板路线；
- 潜行右键路线方块，将其从样板路线中移除；
- 手持样板时会高亮它保存的路线；
- 放回机械样板转换器再次转换，可以保留原 AE2 样板并重写路线。

### 动力样板供应器

动力样板供应器用于单机器 Create 处理。

它内置 AE2 样板供应器逻辑，并朝向一个已配置的目标侧。用扳手设置目标侧，让这一侧指向 Create 机器。把普通 AE2 处理样板放进供应器 GUI，接入 ME 网络，然后从 AE2 下单。

当前支持的路由规则：

- **机械手**：第一个物品输入送到机械手朝向两格处的工作位置；第二个物品输入送入机械手手持物库存；产物从工作位置回收。
- **注液器**：物品输入送到注液器下方两格的工作位置；流体输入送入注液器；产物从该工作位置回收。
- **动力冲压机**：如果下方两格是工作盆，全部物品和流体输入进入工作盆，产物也从工作盆回收。没有工作盆时，按单物品模式送到下方两格的料盘、传送带或库存。
- **动力搅拌器**：需要下方两格有工作盆。输入和输出都使用该工作盆。
- **动力锯**：动力锯必须朝上且正在运行。供应器会按样板主产物设置动力锯过滤器，插入输入物，并从动力锯当前旋转方向决定的固定输出侧回收。
- **石磨**：输入送入石磨，实际产物和出现的概率副产物都会回收。
- **粉碎轮**：目标必须是 `create:crushing_wheel_controller`，不是普通粉碎轮方块。控制器需要把产物推到可回收的传送带、料盘或库存。
- **通用物品处理器**：一个物品输入进入目标机器库存，预期产物从同一库存回收。

并行卡让一个动力样板供应器把作业分发给多台同类型机器：

- 0 张并行卡：只使用正面目标；
- 1 张并行卡：最多 16 台活动机器；
- 2 张并行卡：最多 32 台活动机器。

智能翻倍可按供应器单独开启。开启后，相同处理样板可以批量并入当前活动作业。如果目标机器暂时不能接收下一份输入，供应器会把这份输入保存在轻量内部待投料队列中，并在作业活动期间继续重试。待投料输入会随作业保存；如果合成在投料前被取消，会通过 AE2 返回库存退回网络。

### 并行卡

并行卡用于升级高级封包分发器和动力样板供应器。

在高级封包分发器中，它提高可同时运行的互不重叠路线数量。在动力样板供应器中，它解锁多台同类型机器分发。

并行卡在普通库存中可以堆叠，但 AE2 升级槽仍然一槽只能放一张。

### 未完成的封包分发器

未完成的封包分发器是本模组 Create 序列组装配方使用的过渡物品。

它不是普通工具或机器，只用于让本模组自己的方块通过 Create 装配链制作。

## 样板规则

机械动力封包当前使用普通 AE2 处理样板。

序列组装样板应编码：

- 1 个基础输入物品；
- 完整循环次数所需的机械手消耗物；
- 完整循环次数所需的注液器流体；
- 最终主产物。

对于精密构件这类配方，基础物品只消耗一次，中间产物会在流水线中循环。不要把基础物品乘以循环次数。

精密构件样板示例：

- 输入：`1 金板`、`5 齿轮`、`5 大齿轮`、`5 铁粒`；
- 输出：`1 精密构件`。

AE2 处理样板没有“概率输出”语义。不要把可选副产物写进样板输出列表，除非你希望 AE2 把它当成必需产物等待。机械动力封包仍然会在副产物实际出现时回收它们。

## 序列组装行为

封包分发器会用样板主产物匹配 Create 序列组装配方。如果多个配方匹配，或样板缺少必要输入，作业会在消耗输入前被拒绝。

一次作业中，分发器会：

1. 校验路线和样板输入；
2. 模拟所有目标插入；
3. 供应基础物品、机械手手持物和注液器流体；
4. 监听配置好的输出位置；
5. 把回收到的产物送回 AE2。

对于多循环序列组装，Create 的中间产物会保存配方进度数据。分发器在输出位置发现当前作业的中间产物时，会把同一个物品栈送回输入位置，不会剥离进度数据。

对于概率主产物，如果副产物或空产出超时说明主产物还没有出现，分发器可以从 AE 存储中再抽取一轮输入继续补刷。

## 诊断信息

工程师护目镜和 Jade 会显示有用的运行状态：

- AE 节点供电、频道和在线状态；
- 当前状态和拒单原因；
- 活动作业产物与剩余数量；
- 已链接路线或机械封包样板保存的路线；
- 并行卡容量和活动作业数；
- 动力样板供应器目标机器和已链接并行机器。

这些诊断用于让卡住的合成不用先翻日志也能看懂。

## 当前限制

- Create 机器必须有动力，并且物品流必须真实可运行。
- 产物必须进入配置好的可回收位置。模组不扫描掉落物实体。
- 封包分发器和基础封包分发器一次只运行一条路线。
- 高级封包分发器的并行只会运行互不重叠的路线。
- 动力样板供应器用于单机器处理，不适合多步骤序列组装。
- 当前样板匹配偏保守，遇到不确定情况会拒绝接单，以避免消耗错误输入。

## 许可证

机械动力封包使用 MIT License。详见 [LICENSE](LICENSE)。

## 第三方声明

### Modular Routers 声音素材

机械动力封包包含以下来源于 desht 的 Modular Routers 的声音素材：

- `assets/createpackage/sounds/machine_linker_success.ogg`，来自 `assets/modularrouters/sounds/success.ogg`
- `assets/createpackage/sounds/machine_linker_error.ogg`，来自 `assets/modularrouters/sounds/error.ogg`
- `assets/createpackage/sounds/machine_linker_thud.ogg`，来自 `assets/modularrouters/sounds/thud.ogg`

Modular Routers 使用 MIT License 分发。Copyright (c) 2016 Des Herriott。

来源：https://github.com/desht/ModularRouters

Modular Routers 的 README 将其声音标识为免费声音素材，对应来源许可如下：

- Scrampunk，https://freesound.org/people/Scrampunk/sounds/345297/ - Creative Commons Attribution 4.0
- Autistic Lucario，https://freesound.org/people/Autistic%20Lucario/sounds/142608/ - Creative Commons Attribution 4.0
- Reitanna，https://freesound.org/people/Reitanna/sounds/332661/ - Creative Commons 0

本模组未包含 Modular Routers 中派生自 Botania 的 `me.desht.modularrouters.client.fx` 包代码。

## 致谢

特别感谢 **xiaoleng5261** 为机械动力封包提供模型、材质与对应 JSON 文件。
