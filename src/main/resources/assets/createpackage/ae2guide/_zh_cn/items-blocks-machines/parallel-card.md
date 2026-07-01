---
navigation:
  parent: createpackage:create-package.md
  title: 并行卡
  icon: parallel_card
  position: 258
categories:
- tools
item_ids:
- createpackage:parallel_card
---

# 并行卡

<ItemImage id="parallel_card" scale="6" />

并行卡用于升级 <ItemLink id="advanced_package_distributor" /> 和 <ItemLink id="kinetic_pattern_provider" />。

每安装一张卡，高级封包分发器可同时运行的互不重叠机械封包路线数量翻倍：

* 不安装：1 条活动装配线。
* 1 张：2 条活动装配线。
* 2 张：4 条活动装配线。

最多只能安装两张。并行卡在普通库存中可以堆叠，但 AE2 升级槽仍然一槽只能放一张。

并行卡不会让同一条物理 Create 流水线同时处理两份作业。如果新作业的路线和正在运行的路线重叠，高级封包分发器会等待那条路线空闲。

在动力样板供应器中，并行卡会启用已链接的同类型机器：

* 不安装：只使用正面机器。
* 1 张：最多 16 台活动机器。
* 2 张：最多 32 台活动机器。

用 <ItemLink id="machine_linker" /> 选中动力样板供应器，再链接和正面目标同方块类型的机器即可。
