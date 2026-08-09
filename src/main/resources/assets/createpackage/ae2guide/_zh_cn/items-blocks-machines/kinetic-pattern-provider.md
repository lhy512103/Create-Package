---
navigation:
  parent: createpackage:create-package.md
  title: 动力样板供应器
  icon: kinetic_pattern_provider
  position: 256
categories:
- devices
item_ids:
- createpackage:kinetic_pattern_provider
---

# 动力样板供应器

<ItemImage id="kinetic_pattern_provider" scale="6" />

面向单台 Create 机器（或并行同类型机器）的 AE2 样板供应器，适合不需要完整序列组装路线的配方。

接入 ME 网络，用扳手设定目标侧，放入普通 AE2 <ItemLink id="ae2:processing_pattern" />。默认只使用正面机器。

## 并行机器

安装 <ItemLink id="parallel_card" /> 后，用 <ItemLink id="machine_linker" /> 选中供应器，再链接与正面目标同类型的机器。

* 0 张：仅正面机器
* 1 张：最多 16 台
* 2 张：最多 32 台

新作业优先发往空闲的已链接机器。

## 支持目标

* **机械手**：加工物送工作位，施加物进机械手
* **注液器**：物品送工作位，流体进注液器
* **动力冲压机**：料盘/传送带模式，或下方工作盆模式
* **动力搅拌机**：工作盆配方（物品 + 流体）
* **动力锯**：按主产物物品设置过滤，从固定输出侧回收
* **磨石 / 粉碎**：从支持的输出库存回收实际产物
* **CreateMoreMachines**：按注册 ID 后缀识别同角色机器，仍用标准物品/流体能力
* **Create Crafts & Additions**：轧机使用内部库存；朝上的特斯拉线圈可处理其下方两格处置物台或传送带上的物品
* **Create Diesel Generators**：工作盆盖向正下方工作盆供料并回收；大型发酵槽通过多方块能力接收物品与流体混合输入

蒸馏槽目前会被识别但暂不执行：一次加工会把不同流体产物写入不同高度的槽体，而供应器作业目前只有一个可回收输出位置。

主产物可以是物品 **或流体**。流体产物与副产物会从输出位置的流体容器回收。

## 智能翻倍

开启后会把相同样板推送合并成更大批量，批量大小按目标**空余**罐/槽容量计算。搅拌机 / 工作盆可按空机容量预收样板，整批补满而不是 1B 慢发；目标满时每 tick 重试，回收产物后立刻唤醒合成。

## 回收注意

动力锯、磨石、粉碎等仍需在输出侧有可回收的传送带 / 料盘 / 库存。护目镜 / Jade 显示状态、作业、智能翻倍与最近失败详情。
