# Create Package / 机械动力封包

机械动力封包为 AE2 自动合成提供 Create 序列组装支持。

它的目标是让一个 AE2 样板供应器把一整条序列组装请求交给一个封包分发器。分发器负责把基础原料送到起点料盘或传送带，把 Deployer 施加物送到对应 Deployer，把 Spout 流体送到对应 Spout，并把终点产物回收到 AE2 网络。真正的加工仍由玩家搭建的 Create 机器、传送带和动力系统完成。

仓库地址: https://github.com/lhy512103/Create-Package.git

## 当前状态

这是 Minecraft 1.21.1 / NeoForge 的早期开发版本。

已实现:

- 封包分发器接入 AE2 网格，并向相邻样板供应器暴露 AE2 crafting-machine capability。
- 基础封包分发器内置 AE2 样板供应器逻辑，可直接放入样板并向 AE2 网络提供这些样板。
- 高级封包分发器内置 AE2 样板供应器逻辑，并按每张机械封包样板保存的机器链接路线执行，可在一个方块里存放多条装配线样板；安装并行卡后可同时运行多条不重叠装配线。
- 动力样板供应器内置 AE2 样板供应器逻辑，用于单台 Create 机器或单个工作单元；它不使用机器连接器，而是直接识别正面朝向的机器。
- 机械样板转换器可以直接按顺序标记机器路线，并把普通 AE2 已编码样板转换为带路线的机械封包样板。
- 机器链接器可以保存一组有序链接的 Create 机器位置。
- 可以把 Create 序列组装配方解析为供料计划。
- 分发器会校验、模拟、执行投料，然后等待主产物出现在终点料盘或传送带并回收到 AE2。
- 多循环序列组装支持自动回流：终点出现当前配方的 Create 中间产物时，分发器会把原始物品重新放回起点，保留序列组装进度组件。
- 几率主产物没有产出时会自动补刷：终点收到副产物会立即再投一轮；一轮完全没有产物时会在配置的等待时间后再投一轮。
- 工程师护目镜/Jade 可显示作业主产物名称、剩余主产物数量、当前补刷轮次和 AE 状态；普通/基础分发器显示已链接机器，高级分发器显示机械封包样板路线来源。
- 运行时不使用 mixin、不使用反射、不扫描世界；只访问玩家链接的位置，空闲时 AE2 tick 会睡眠。
- 不消耗的 Deployer 持有物：机械手已预装对应物品：样板里可以不写这个物品。
机械手没预装，但 AE 网络里有：分发器会从 AE 网络取 1 个放进机械手。
样板里写了 1 个：会拿这 1 个放进机械手。
样板里写了 3 个或多写了同类物品：只需要的部分用于预装，多余的会立即退回 AE 网络，不再报“样板存在未使用输入”。

计划中:

- 专用的 Create 序列组装样板编码器。
- 更细的缺料诊断。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.x
- Applied Energistics 2 19.2.x
- Create 6.0.x

## 分发器怎么使用

1. 搭建真实的 Create 序列组装流水线。
   使用正常的传送带、料盘、机械手、注液器、辊压机、切割机、动力和物品流。分发器不会直接推进序列组装进度，Create 机器本身必须能自然加工物品。

2. 把封包分发器接入 AE2 网络。
   分发器需要 AE2 频道。把 AE2 样板供应器放在分发器旁边，让供应器可以把加工样板推送给分发器。

   如果使用基础封包分发器，则不需要额外放 AE2 样板供应器。把基础封包分发器接入 AE2 网络后，右键打开它自己的样板槽，把 AE2 加工样板直接放进去即可。AE2 下单时会把该基础分发器当作样板供应源，材料会直接进入它自己的分发管线。

   如果使用高级封包分发器，也不需要额外放 AE2 样板供应器。高级封包分发器只执行机械封包样板；每张机械封包样板都带有自己的机器链接路线，所以一个高级分发器可以存放多条装配线的样板。默认同时运行 1 条装配线；升级槽内放入 1 张并行卡后为 2 条，放入 2 张后为 4 条。

3. 用机器链接器按物品流动顺序链接机器。
   先右键封包分发器选中它，再按流水线顺序右键 Create 机器:

   - 第一个链接的料盘或传送带会作为起点投料位置；
   - 按物品经过顺序链接 Deployer 和 Spout；
   - 最后一个链接的料盘或传送带会作为终点回收位置。

   潜行 + 右键已链接机器可以取消链接。潜行 + 右键已选中的分发器可以清空全部链接。

4. 目前先使用普通 AE2 加工样板。
   在专用编码器完成前，样板的主输出需要能唯一匹配一个 Create 序列组装配方。

   样板输入应包含:

   - 1 个序列组装基础原料；
   - 完整循环次数所需的 Deployer 消耗物品；
   - 完整循环次数所需的 Spout 流体。

   当前匹配逻辑比较保守。多余输入、缺少输入、或多个配方同时匹配时会拒绝接单，避免错误消耗 AE2 物品。

5. 把样板放进 AE2 样板供应器并从 AE2 发起合成。
   AE2 推送样板后，分发器会先模拟所有目标能否接收材料。模拟成功后才会真正投料，并等待终点位置出现主产物。回收到的物品会注入 AE2 网络存储。

   使用基础封包分发器时，把样板放进基础封包分发器本体，不要再额外贴一个样板供应器。

## 高级封包分发器与机械封包样板

高级封包分发器用于“一个分发器管理多条装配线”。它不读取方块当前链接列表来执行样板，而是读取机械封包样板里保存的路线。

使用流程:

1. 手持机械样板转换器，按物品流动顺序右键 Create 机器来标记路线。
2. 第一个料盘或传送带是起点，最后一个料盘或传送带是终点；Deployer/Spout 仍按标记顺序对应配方步骤。
3. 路线只需要标记料盘/传送带、Deployer 和 Spout。动力冲压机、切石机等自然加工机器不需要也不能标记。
4. 潜行 + 右键已标记机器可以取消标记；潜行 + 空中右键转换器可以清空整条路线。
5. 空中右键机械样板转换器打开转换 GUI。
6. 把普通 AE2 已编码加工样板放入左侧输入槽，点击“转换”，右侧会生成机械封包样板。
7. 取出机械封包样板，放入高级封包分发器的样板槽。
8. 对每条装配线重复以上流程。每张机械封包样板都会保存转换当时转换器内的机器路线快照。

注意:

- 转换器 tooltip 会直接显示已标记机器数量；按住 Shift 会展开机器名称和坐标。
- 转换后的机械封包样板 tooltip 也会显示路线数量；按住 Shift 会展开保存的机器路线。
- 已转换的机械封包样板可以重新放回机械样板转换器，点击转换后会保留原 AE2 样板并写入转换器当前路线。
- 手持机械封包样板也可以直接右键机器改写它自己的路线；潜行 + 右键机器会从样板路线里移除该位置。
- 手持机械样板转换器时，主世界会高亮当前标记路线；手持已转换的机械封包样板时，会高亮它保存的机器路线。终点使用橙色高亮，便于检查是否标记错线。
- 机器链接器不能选中高级封包分发器；高级封包分发器的路线来自机械封包样板，不来自方块自身链接列表。
- 转换后再修改转换器的标记路线，不会自动改写已经转换好的机械封包样板，除非把该机械封包样板重新放入转换器再转换一次。
- 当前高级封包分发器只执行机械封包样板；普通 AE2 加工样板应放在基础封包分发器或相邻样板供应器 + 普通封包分发器方案中。
- 高级封包分发器最多安装 2 张并行卡；并行卡在普通库存中可以堆叠，但 AE2 升级槽仍然一槽只能放一张。并行调度只会同时启动机器路线不重叠的作业，避免两张样板抢同一条物理装配线。
- 手持机器链接器、机械样板转换器或机械封包样板时，主世界会高亮对应的机器路线；终点使用橙色高亮。

## 动力样板供应器

动力样板供应器用于单个 Create 机器或一组相同 Create 机器，不处理多机器序列组装路线。默认只使用正面机器；安装并行卡后，可以用机器链接器额外绑定同类型机器。

使用方式:

1. 把动力样板供应器贴着目标机器放置。刚放下时它和 AE2 样板供应器一样是未定向状态，不会自动选中某一侧机器。
2. 手持 AE2/Create 扳手右键供应器的某个侧面，设置它要对准的目标侧。目标机器必须在这个侧面的相邻方块。
3. 接入 AE2 网络，右键打开原版样板供应器界面，把普通 AE2 加工样板放入其中。
4. 从 AE2 下单。供应器只访问已设置目标侧的机器和固定相邻位置，不扫描世界。

并行机器:

- 默认只使用正面机器。
- 在动力样板供应器升级槽内放入并行卡后，可以用机器链接器给它额外链接同类型机器。
- 手持机器链接器右键动力样板供应器选中它，再右键和正面机器相同方块类型的机器进行链接；潜行 + 右键已链接机器可取消链接，潜行 + 右键动力样板供应器可清空链接。
- 0 张并行卡 = 1 台机器，1 张 = 最多 16 台机器，2 张 = 最多 32 台机器。实际并行数还受已链接同类型机器数量限制。
- 下单时，动力样板供应器会优先把新作业发配到空闲机器；智能翻倍则是在某台已活动机器仍能接收下一份输入时继续堆叠同一处理样板。

目标侧规则:

- 未设置目标侧时，护目镜/Jade 会显示“目标机器：未设置”，AE2 下单会被拒绝并提示需要用扳手设置目标面。
- 设置目标侧后，该侧留给 Create 机器，其余侧仍可连接 AE2 网络。
- 再次用扳手调整目标侧时，会使用 AE2 样板供应器同款方向循环规则。

当前固定规则:

- Deployer: 供应器正面对准 Deployer；样板第一个物品输入投到 Deployer 朝向 2 格处的加工位置，第二个物品输入投到 Deployer 手持物库存；输出从加工位置回收。常见向下 Deployer 的加工位置是机械手下方第二格的料盘/传送带/置物台。
- Spout: 供应器正面对准 Spout；物品输入投到 Spout 下方第二格的料盘/传送带/置物台，流体输入投到 Spout 流体库存；输出从该工作位置回收。
- 动力冲压机: 供应器正面对准动力冲压机；如果冲压机下方第二格是工作盆，会把样板里的全部物品/流体输入投进工作盆并从工作盆回收输出；否则按单物品模式投到下方第二格的料盘/传送带并回收。不会扫描掉落物实体。
- 动力搅拌器: 供应器正面对准动力搅拌器；动力搅拌器必须使用下方第二格的工作盆。样板里的全部物品/流体输入会投进工作盆，输出也从工作盆回收。
- 动力锯等带物品 capability 的单机处理器: 物品输入直接投到正面机器自身物品库存，并从该库存回收样板输出。
- 石磨: 物品输入投到石磨输入槽，输出从石磨输出槽回收；石磨实际产出的概率副产物也会一并注入 AE 网络。
- 粉碎轮: 供应器必须对准 `create:crushing_wheel_controller`，不要直接对准普通粉碎轮方块。粉碎轮控制器必须能把产物推到 Create 固定输出位置的传送带/料盘/库存，供应器从该位置回收；不会扫描掉落物实体。
- GUI 里的物品返回栏会随供应器的低频 AE tick 自动尝试退回网络；没有作业、返回栏为空时 tick 会睡眠。

这个方块适合单机处理，不适合精密构件这类多步骤序列组装。多步骤仍使用封包分发器/基础封包分发器/高级封包分发器。

动力样板供应器处理概率产物时遵循 AE2 普通加工样板的限制:

- AE2 普通加工样板没有“概率输出”语义。写在样板输出里的物品会被 AE2 当成必定产物等待。
- 石磨/粉碎轮的概率副产物不要写进 AE 样板输出；只写需要完成订单的主产物。副产物实际出现时仍会被供应器回收到 AE 网络。
- 如果主产物本身是概率产物，供应器会在一轮没有凑齐主产物时，从 AE 网络再抽同一轮输入补刷，直到主产物回收到位、AE 取消等待、缺材料，或达到硬超时。
- 如果取消 AE 合成后供应器仍在等待，供应器会通过 AE 等待量检测或硬超时释放内部作业，不需要拆掉重放。

## 多循环自动回流

Create 的序列组装中间产物带有配方 id、步骤和进度组件。分发器会识别当前作业的中间产物:

- 终点出现当前配方的中间产物时，分发器会把这个原始 ItemStack 重新插回起点料盘或传送带。
- 回流时不会把物品拆成普通物品，也不会丢失 Create 的序列组装进度。
- 起点暂时不能接收时，状态会显示“等待起点接收中间产物”，并在后续 tick 继续重试。
- 只有最终主产物会减少 AE2 请求量；非中间产物的副产物/废料仍会回收到 AE2，并按几率产物逻辑补刷。

因此玩家可以搭建线性流水线：起点 -> 各加工机器 -> 终点。多循环配方不再强制玩家用传送带额外绕回起点。

## 几率产物与补刷

AE2 的一次样板推送只会交给分发器一轮材料。对精密构件这类几率主产物，分发器会按主产物计量:

- 如果终点回收到主产物，剩余主产物数量减少。
- 如果终点回收到副产物或废料，但主产物仍不足，分发器会从 AE 网络再抽一轮基础原料、Deployer 消耗物和 Spout 流体并继续投料。
- 如果一轮加工后终点完全没有任何产物，分发器会等待 `emptyOutputRefillTimeoutTicks` tick，默认 1200 tick/60 秒，然后按空产出处理并补刷。
- 如果 AE 网络缺少补刷材料，会显示“等待补刷材料”，并每 5 秒轻量重试一次。

精密构件样板示例:

- 输入: `1 x 金板 + 5 x 齿轮 + 5 x 大齿轮 + 5 x 铁粒`
- 输出: `1 x 精密构件`

不要把金板写成 5 个。循环 5 次的是同一个半成品，基础原料只消耗 1 个。

## 测试配方

模组内置的分发器制作配方都改为 Create 序列组装:

- `createpackage:sequenced_assembly/package_distributor`: 金板为基础物品，机械手依次安装 AE2 硅、逻辑、工程、运算压印模板，模板均不消耗，循环 3 次。
- `createpackage:sequenced_assembly/basic_package_distributor`: 封包分发器为基础物品，机械手安装精密构件，注入 1000 mB 熔岩，注入 1000 mB 水，再由动力冲压机冲压。
- `createpackage:sequenced_assembly/advanced_package_distributor`: 基础封包分发器为基础物品，机械手安装坚固板，机械手安装赛特斯水晶，动力锯切割，动力冲压机冲压，注入 500 mB 熔岩，循环 5 次。赛特斯水晶正常消耗。结果池使用精密构件同款权重：120 权重高级封包分发器，30 总权重随机废料，实际为 80% 成功。

旧的封包分发器流体/切割/冲压测试配方，以及基础/高级封包分发器的直接合成配方已经移除。

## 链接顺序规则

第一个被识别为料盘或传送带的链接位置是起点。
最后一个被识别为料盘或传送带的链接位置是终点。

Deployer 按链接顺序对应 deploying 步骤。这个顺序很重要，因为一个 Deployer 同一时间只能握一种施加物。

Spout 按链接顺序对应 filling 步骤。分发器通过 NeoForge 流体 capability 给已链接的 Spout 补流体。

Pressing 和 cutting 步骤由真实 Create 流水线上的机器自然完成，它们不需要分发器补耗材。

## 当前限制

- 普通封包分发器和基础封包分发器同一时间只处理一个作业。
- 基础封包分发器复用 AE2 原版样板供应器菜单和样板库存，但目前仍只按本模组支持的 Create 序列组装样板执行。
- 高级封包分发器当前只支持机械封包样板；并行卡只并行不重叠的物理路线，不会把同一条路线拆成多个并发作业。
- 自动回流只处理带有当前配方 `sequenced_assembly` 组件的中间产物；普通副产物不会被回流。
- 起点和终点料盘或传送带必须是不同位置。
- Create 流水线必须有效、有动力，并且能把产物送到终点位置。
- 如果真实流水线卡住但没有达到空产出等待时间，分发器会继续等待。
- 当前实现会严格拒绝不确定的样板匹配，优先避免丢物品。

## English

Create Package adds AE2 autocrafting support for Create sequenced assembly.

One AE2 Pattern Provider can push a full sequenced-assembly craft into one
Package Distributor. The distributor supplies the linked Create line: base input
to the input depot or belt, deployer held items to linked deployers, spout fluids
to linked spouts, and recovered outputs back into AE2 storage. The actual
processing still happens in the real Create machines and belts built by the
player.

### Current Usage

1. Build a real Create sequenced-assembly line with normal belts, depots,
   deployers, spouts, presses, saws, power, and item movement.
2. Place a Package Distributor on the AE2 network and put an AE2 Pattern
   Provider next to it.
   With a Basic Package Distributor, no adjacent Pattern Provider is needed:
   place it on the AE2 network, right-click it, and put encoded AE2 processing
   patterns into its built-in pattern slots.
3. Use the Machine Linker: right-click the distributor to select it, then link
   the input depot or belt first, deployers/spouts in physical order, and the
   output depot or belt last.
4. Encode a normal AE2 processing pattern for now. Inputs should contain one
   base item, all consumed deployer items for the full loop count, and all spout
   fluids for the full loop count. The primary output must uniquely match one
   Create sequenced-assembly recipe.
5. Request the craft from AE2. The distributor simulates all insertions first,
   supplies the linked machines only if the simulation succeeds, then waits for
   the primary output at the linked output position and inserts recovered items
   into AE2 storage.

### Status

Implemented: AE2 grid integration, ordered machine linking, sequenced-assembly
supply-plan parsing, conservative validation, simulated insertion before real
insertion, output recovery, probabilistic-output refills, Engineer's Goggles/Jade
diagnostics, a Basic Package Distributor with an embedded AE2 pattern-provider
inventory, an Advanced Package Distributor that routes mechanical package
patterns by their saved machine-link snapshots, automatic transitional-item
reflow for multi-loop sequenced assembly, Parallel Cards for running up to four
disjoint Advanced Package Distributor lines at once, a Kinetic Pattern Provider
for single Create machines facing the provider, and low-overhead ticking with no
mixins, reflection, or world scanning.

### Kinetic Pattern Provider

The Kinetic Pattern Provider is for one Create machine or a group of matching
Create machines. Place it so its configured target side points at the machine,
put normal AE2 processing patterns into its built-in pattern-provider slots, and
request the craft from AE2.

By default it uses only the front machine. Install Parallel Cards in its upgrade
slots to unlock linked machines, then use the Machine Linker to select the
provider and link machines of the same block type as the front target. New jobs
are dispatched to idle linked machines before the provider reports busy: 1 card
allows up to 16 active machines, and 2 cards allow up to 32. Smart Doubling still
batches extra copies into an already active machine only when that machine can
simulate accepting the next input set.

Current fixed routing:

- Deployer: first item input goes to the working position two blocks along the
  deployer's facing direction, second item input goes into the deployer's
  held-item inventory, and output is recovered from that working position.
- Spout: item input goes to the depot/belt/table two blocks below the spout,
  fluid input goes into the spout, and output is recovered from that working
  position.
- Mechanical Press: with a basin two blocks below the press, all item/fluid
  pattern inputs are inserted into that basin and outputs are recovered from the
  basin. Without a basin, it stays in single-item depot/belt mode. Dropped item
  entities are not scanned.
- Mechanical Mixer: requires a basin two blocks below the mixer. All item/fluid
  pattern inputs are inserted into that basin and outputs are recovered from it.
- Mechanical Saw: face an upward-facing running saw. When a pattern is pushed,
  the provider sets the saw's recipe filter to the pattern's primary output,
  inserts the item into the saw, and recovers results from the adjacent
  belt/depot/inventory position selected by the saw's current rotation
  direction. Dropped item entities are not scanned, so that output position must
  be recoverable.
- Other single-machine item handlers: item input goes into the front machine's
  item handler and expected outputs are recovered from the same handler.
- Millstone: item input goes into the millstone input slot. Outputs are
  recovered from the millstone output slots, including probabilistic byproducts
  that actually appeared.
- Crushing Wheel: face the `create:crushing_wheel_controller`. The controller
  must push results into Create's fixed downstream belt/depot/inventory output
  position; the provider recovers from that position and does not scan dropped
  item entities.
- Pattern Access/Management terminals group Kinetic Pattern Providers by the
  machine they face when possible, falling back to the provider itself only when
  no target machine name can be resolved.
- Return slots are merged into the provider's own AE ticker, so they return
  items to the network without adding a separate world scan or always-on tick.

AE2 processing patterns do not encode probabilistic outputs. Do not put
probabilistic byproducts in the AE pattern output list unless you want AE2 to
wait for them as required outputs. The provider still recovers probabilistic
byproducts from millstone/crushing outputs when they appear. If the primary
output itself is probabilistic, the provider refills one more input round from
AE storage until the primary output arrives, AE stops waiting, inputs run out,
or the job reaches its timeout.

Planned: dedicated sequenced-assembly pattern encoder and more detailed missing-input diagnostics.

## License

Create Package is licensed under the MIT License. See [LICENSE](LICENSE).
