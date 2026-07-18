---
navigation:
  parent: createpackage:create-package.md
  title: 机械路线工具
  icon: mechanical_pattern_converter
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

用于为封包分发器和机械封包样板标记 Create 机器路线。手持路线工具时会在世界中高亮当前路线。

## 有序路线共用操作

适用于**封包分发器**链接，以及转换器 / 机械封包样板路线。动力样板供应器的并行机链接保持集合语义，没有追加/编辑标记模式。

| 操作 | 效果 |
| --- | --- |
| 左键空气 | 切换 **追加** / **编辑** |
| 追加 + 右键 | 加点 |
| 追加 + 潜行右键 | 删除点到的标记 |
| 编辑 + 滚轮 | 选择标记 |
| 编辑 + 右键 | 替换选中标记 |
| 编辑 + 潜行右键 | 删除选中标记 |
| **G** | 按端点自动重建路线（直线走廊；可选同高度 L 形） |

自动路线只保留起终点，以及真正向走廊投料的加工机；冲压机等被动路径机器会被跳过。机械封包样板在编辑模式下，若能解析 AE 样板与序列组装配方，会显示当前标记对应的预期材料。

## 机器连接器

1. 右键 <ItemLink id="package_distributor" />、<ItemLink id="basic_package_distributor" /> 或 <ItemLink id="kinetic_pattern_provider" /> 绑定主机
2. 右键机器链接；潜行 + 右键取消链接
3. 潜行 + 右键主机可清空其保存的链接（使用存储链接的分发器）
4. 按住 FTB Ultimine 选择可批量链接 / 取消同类机器

**高级封包分发器不用连接器**，路线写在机械封包样板上。

## 机械样板转换器

1. 在可标记方块上标记路线（料盘、传送带、机械手、注液器等）
2. 右键空气打开转换界面
3. 放入最多 9 张 AE2 处理样板（或已有机械封包样板）并转换

转换会把转换器当前标记的路线写入输出机械封包样板，同时保留原 AE2 编码数据。可以先无路线转换，稍后再补路线；没有路线标记时 AE2 不会把它注册为可自动合成样板。有效的机械处理样板可在 AE2 样板编码终端中重编码，并保留已保存路线。

## 机械封包样板

保存原 AE2 样板数据 + Create 路线，供 <ItemLink id="advanced_package_distributor" /> 使用。

* Tooltip 显示编码者、模式、标记数量；按住 Shift 显示路线坐标
* 编辑模式下，可解析时显示选中标记的材料提示
* 手持样板直接右键可标记方块以编辑保存路线
* 按 **G** 可按端点重建已保存路线
