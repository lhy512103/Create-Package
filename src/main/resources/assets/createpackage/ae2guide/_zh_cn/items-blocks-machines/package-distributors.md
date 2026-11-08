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
---

# 机械动力封包分发器

<Row gap="16">
<ItemImage id="package_distributor" scale="4" />
<ItemImage id="basic_package_distributor" scale="4" />
<ItemImage id="advanced_package_distributor" scale="4" />
</Row>

封包分发器把 AE2 自动合成接到真实的 Create 序列组装流水线：负责投料、等待 Create 加工，并把产物回收到 ME 网络。传送带、料盘、机械手、注液器、动力和物流仍需在世界中正常运转。

## 封包分发器

作为普通 <ItemLink id="ae2:pattern_provider" /> 的外部目标。接入 ME 网络，旁边放样板供应器，再用 <ItemLink id="machine_linker" /> 链接 Create 装配线。

链接顺序：

1. 起点料盘 / 传送带
2. 按物理加工顺序的机械手 / 注液器
3. 终点料盘 / 传送带

机械手应按 Create 的实际工作距离安装在目标置物台上方两格处；路线标记机械手方块本身。

使用普通 AE2 <ItemLink id="ae2:processing_pattern" />。主输出需唯一匹配 Create 序列组装配方；输入需包含基础原料以及配方会消耗的机械手物品与注液流体。

## 基础封包分发器

链接方式与封包分发器相同，但内置 AE2 样板槽，不需要额外相邻的样板供应器。

## 高级封包分发器

路线保存在每张 <ItemLink id="mechanical_package_pattern" /> 上，而不是方块本身。用 <ItemLink id="mechanical_pattern_converter" /> 把 AE2 处理样板转换成机械封包样板后放入。

可安装 <ItemLink id="parallel_card" />，同时运行多条互不重叠的已保存装配线。

若多张机械封包样板的底层配方相同但路线不同，高级分发器会在空闲路线间轮询派发。要同时启动多条线，一次合成请求必须包含多份产物，且各路线不能共享已标记的置物台或加工机器。

## 行为说明

安装 AE2 Utility 1.8 或更新版本后，基础和高级封包分发器可在四个通用升级槽中放入一张 NBT 撕裂卡；它与并行卡共用槽位。普通封包分发器请把卡放在负责下单的外接样板供应器中。

产物识别、合成回传和补料选择遵循撕裂卡的白名单及 AE2 Utility 黑名单，保留实际物品的数据。每个分发器作业保存接单时的卡片规则，修改规则对后续新作业生效。

* 投料前会先模拟，失败时尽量回滚已写入目标
* 概率配方可在副产物 / 空产出超时后从 AE 补刷
* AE 取消合成会释放作业并清除缺料提示
* 护目镜 / Jade 显示状态、作业、缺料与最近失败详情
