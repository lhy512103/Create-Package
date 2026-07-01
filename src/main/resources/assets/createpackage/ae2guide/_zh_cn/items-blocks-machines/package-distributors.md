---
navigation:
  parent: createpackage:create-package.md
  title: 机械动力封包分发器
  icon: package_distributor
  position: 255
categories:
- devices
item_ids:
- createpackage:package_distributor
- createpackage:basic_package_distributor
- createpackage:advanced_package_distributor
- createpackage:incomplete_package_distributor
---

# 机械动力封包分发器

<Row gap="16">
<ItemImage id="package_distributor" scale="4" />
<ItemImage id="basic_package_distributor" scale="4" />
<ItemImage id="advanced_package_distributor" scale="4" />
</Row>

机械动力封包分发器用于把 AE2 自动合成连接到真实的 Create 序列组装流水线。它会把基础原料送到装配线起点，把机械手施加物和注液器流体送到已链接机器，并把最终产物回收到 ME 网络。

实际加工仍由玩家搭建的 Create 机器完成。传送带、料盘、机械手、注液器、冲压机、动力锯、动力来源和物品移动都必须在世界中正常工作。

## 封包分发器

封包分发器是普通 <ItemLink id="ae2:pattern_provider" /> 的外部目标。把它接入 ME 网络，在旁边放置样板供应器，然后用 <ItemLink id="machine_linker" /> 链接 Create 装配线。

链接顺序必须固定：

1. 起点料盘或传送带。
2. 按物理加工顺序链接机械手和注液器。
3. 终点料盘或传送带。

当前使用普通 AE2 <ItemLink id="ae2:processing_pattern" />。样板输出必须能唯一匹配一个 Create 序列组装配方，输入里要包含基础物品、配方会消耗的机械手物品和注液器流体。

## 基础封包分发器

基础封包分发器内置 AE2 样板供应器库存。它使用和封包分发器相同的机器链接路线，但不需要额外相邻的 AE2 样板供应器。

右键打开界面，把已编码的 AE2 处理样板直接放进基础封包分发器，然后从 AE2 下单。

## 高级封包分发器

高级封包分发器用于在一个方块里保存多条装配线路线。它不使用机器链接器。每张 <ItemLink id="mechanical_package_pattern" /> 都会携带自己的机器路线。

使用 <ItemLink id="mechanical_pattern_converter" /> 标记路线，并把 AE2 处理样板转换成机械封包样板。然后把这些机械封包样板放进高级封包分发器。

高级封包分发器可以安装 <ItemLink id="parallel_card" />，同时运行多条互不重叠的已保存装配线。

## 说明

未完成的封包分发器是本模组测试和制作配方使用的序列组装中间物品，它本身不是合成机器。

分发器会先模拟所有目标插入，成功后才真正投料。如果指南、护目镜或 Jade 显示路线阻塞，需要先修正链接机器、目标库存或产物回收位置再重新下单。
