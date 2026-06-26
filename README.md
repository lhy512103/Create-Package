# Create Package / 机械动力封包

机械动力封包为 AE2 自动合成提供 Create 序列组装支持。

它的目标是让一个 AE2 样板供应器把一整条序列组装请求交给一个封包分发器。分发器负责把基础原料送到起点料盘或传送带，把 Deployer 施加物送到对应 Deployer，把 Spout 流体送到对应 Spout，并把终点产物回收到 AE2 网络。真正的加工仍由玩家搭建的 Create 机器、传送带和动力系统完成。

仓库地址: https://github.com/lhy512103/Create-Package.git

## 当前状态

这是 Minecraft 1.21.1 / NeoForge 的早期开发版本。

已实现:

- 封包分发器接入 AE2 网格，并向相邻样板供应器暴露 AE2 crafting-machine capability。
- 机器链接器可以保存一组有序链接的 Create 机器位置。
- 可以把 Create 序列组装配方解析为供料计划。
- 分发器会校验、模拟、执行投料，然后等待主产物出现在终点料盘或传送带并回收到 AE2。
- 几率主产物没有产出时会自动补刷：终点收到副产物会立即再投一轮；一轮完全没有产物时会在配置的等待时间后再投一轮。
- 工程师护目镜/Jade 可显示作业主产物名称、剩余主产物数量、当前补刷轮次、AE 状态和已链接机器。
- 运行时不使用 mixin、不使用反射、不扫描世界；只访问玩家链接的位置，空闲时 AE2 tick 会睡眠。

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

模组内置了几条用于测试分发器的封包分发器序列组装配方:

- `createpackage:sequenced_assembly/package_distributor`: 黄铜外壳 -> 注水 -> 切割 -> 辊压 -> Deployer 应用 AE2 逻辑处理器 -> 封包分发器。
- `createpackage:sequenced_assembly/package_distributor_fluid_test`: 铜锭 -> 注水 -> Deployer 应用红石 -> 封包分发器。
- `createpackage:sequenced_assembly/package_distributor_cutting_test`: 石头 -> 切割 -> Deployer 应用铁粒 -> 封包分发器。
- `createpackage:sequenced_assembly/package_distributor_pressing_test`: 铁锭 -> 辊压 -> Deployer 应用红石 -> 封包分发器。

这些配方主输出相同，但基础输入不同。AE2 加工样板里放入对应基础输入即可让分发器消歧。

## 链接顺序规则

第一个被识别为料盘或传送带的链接位置是起点。
最后一个被识别为料盘或传送带的链接位置是终点。

Deployer 按链接顺序对应 deploying 步骤。这个顺序很重要，因为一个 Deployer 同一时间只能握一种施加物。

Spout 按链接顺序对应 filling 步骤。分发器通过 NeoForge 流体 capability 给已链接的 Spout 补流体。

Pressing 和 cutting 步骤由真实 Create 流水线上的机器自然完成，它们不需要分发器补耗材。

## 当前限制

- 一个分发器同一时间只处理一个作业。
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
diagnostics, and low-overhead ticking with no mixins, reflection, or world scanning.

Planned: dedicated sequenced-assembly pattern encoder and more detailed missing-input diagnostics.

## License

Create Package is licensed under the MIT License. See [LICENSE](LICENSE).
