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

用于升级 <ItemLink id="advanced_package_distributor" /> 与 <ItemLink id="kinetic_pattern_provider" />。每台最多 4 张。普通库存可堆叠，AE2 升级槽仍一槽一张。

## 高级封包分发器

提高互不重叠的机械封包路线并发数：

* 0 张 → 1 个作业
* 1 张 → 16 个作业
* 2 张 → 32 个作业
* 3 张 → 48 个作业
* 4 张 → 64 个作业

默认每张卡提供 16 个作业位，可通过服务端配置 `advancedDistributorParallelCardCapacity` 调整。不会让同一条或相互重叠的物理流水线同时运行多份作业。

## 动力样板供应器

解锁用 <ItemLink id="machine_linker" /> 链接的同类型并行机器：

* 0 张 → 仅正面
* 1 张 → 最多 16 台
* 2 张 → 最多 32 台
* 3 张 → 最多 48 台
* 4 张 → 最多 64 台
