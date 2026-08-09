---
navigation:
  parent: createpackage:create-package.md
  title: 扩展动力样板供应器
  icon: extended_kinetic_pattern_provider
  position: 257
categories:
- devices
item_ids:
- createpackage:extended_kinetic_pattern_provider
---

# 扩展动力样板供应器

<ItemImage id="extended_kinetic_pattern_provider" scale="6" />

扩展动力样板供应器是 <ItemLink id="kinetic_pattern_provider" /> 的大容量版本。机器投料、产物回收、智能翻倍和并行机器逻辑与普通版本完全相同，但样板区改为可翻页的 **9×4（每页 36 槽）** 布局。

## 样板分页

* 默认开放 **1 页**，可在服务端配置中调整为 1～9 页。
* 使用 AE2 左侧工具栏中的左右箭头切换页面。
* 翻页只切换当前可见槽位，不会移动、复制或重新排列样板。
* Shift 快速移动及槽位交互只作用于当前页。

如果管理员调低页数，已有样板的高页不会被隐藏或删除。这些页面会继续保留，直到其中样板被取空后才缩回配置的页数。

## 使用方法

接入 ME 网络后，用扳手设定目标侧，并像普通动力样板供应器一样放入 AE2 处理样板。并行卡、机器连接器、智能翻倍、支持机器和回收要求参见 [动力样板供应器](kinetic-pattern-provider.md)。

## 合成

使用动力样板供应器进行序列组装。每轮依次经过 4 次冲压、安装 1 个磨制玫瑰石英、使用不消耗的玫瑰石英安装，再由动力锯切割；循环 4 轮后完成。