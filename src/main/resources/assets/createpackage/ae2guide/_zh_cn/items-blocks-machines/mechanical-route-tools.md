---
navigation:
  parent: createpackage:create-package.md
  title: 机械路线工具
  icon: machine_linker
  position: 257
categories:
- tools
item_ids:
- createpackage:machine_linker
- createpackage:mechanical_pattern_converter
- createpackage:mechanical_package_pattern
---

# 机械路线工具

<Row gap="16">
<ItemImage id="machine_linker" scale="4" />
<ItemImage id="mechanical_pattern_converter" scale="4" />
<ItemImage id="mechanical_package_pattern" scale="4" />
</Row>

机械动力封包使用明确的路线标记来控制自动合成时访问哪些机器位置，避免运行时扫描世界。

## 机器链接器

机器链接器会把有序机器路线保存到封包分发器或基础封包分发器。

右键分发器选中它。然后按顺序右键路线方块：起点料盘或传送带在最前，机械手和注液器按实际加工顺序排列，终点料盘或传送带在最后。潜行右键已链接机器可移除。潜行右键已选中的分发器可清空路线。

安装 FTB Ultimine 后，按住连锁选择键并右键同类机器，可一次批量链接或批量取消链接所选形状内的相同机器。批量链接仍然只保存这些已选坐标，不会扫描世界。

手持机器链接器时，会在世界中高亮当前选中分发器的路线。

## 机械样板转换器

机械样板转换器会先把路线保存在转换器物品上，然后再转换样板。

右键可标记的路线方块会追加路线。潜行右键已标记方块会移除。右键空气打开转换界面。把 AE2 处理样板或已有机械封包样板放进输入槽，然后点击转换。

输出的机械封包样板会保留原 AE2 样板数据，并额外写入当前标记的路线快照。

## 机械封包样板

机械封包样板用于高级封包分发器。每张样板都携带原 AE2 样板数据，以及这份作业应该投料到哪条 Create 路线。

按住 Shift 查看物品 tooltip 时会显示已保存机器路线的名称和坐标。手持该物品时会在世界中高亮保存的路线。也可以手持机械封包样板直接右键可标记方块来编辑保存路线。

只有分发器实际会路由的方块可以标记：料盘、传送带、机械手和注液器。
