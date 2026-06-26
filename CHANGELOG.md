# Changelog

## 2026-06-26

- Replaced the NeoForge template README with bilingual project documentation covering Package Distributor setup, link order, current AE2 processing-pattern usage, limitations, and performance design.
- Switched project metadata and repository licensing to MIT, added a root LICENSE file, and removed the template license placeholder.
- Implemented the first real package distributor job pipeline: AE2 pattern pushes are now planned, validated, supplied to linked Create machines, and tracked until the primary output is recovered.
- Added low-overhead runtime behavior: no world scanning, no mixins or reflection; the distributor only touches linked block positions and only wakes its AE2 grid ticker while a job is active.
- Added conservative safety checks for incomplete lines, ambiguous recipes, same input/output depot links, unused pattern inputs, and output recovery timeouts.

## 2026-06-26 中文

- 将 NeoForge 模板 README 替换为中英双语项目文档，补充分发器搭建流程、链接顺序、当前 AE2 加工样板用法、限制与低开销设计说明。
- 将项目元数据和仓库许可证改为 MIT，新增根目录 LICENSE 文件，并移除模板许可证占位文件。
- 实现第一版封包分发器真实作业管线：AE2 样板推送会被解析、校验、投料到已链接的 Create 机器，并等待主产物从终点回收到 AE2 网络。
- 保持低开销运行方式：不扫描世界、不使用 mixin/反射；分发器只访问玩家已链接的位置，并且只有存在作业时才唤醒 AE2 网格 tick。
- 增加保守安全检查：流水线不完整、配方歧义、起点/终点相同、多余样板输入、产物回收超时都会拒单或记录日志，避免丢物品。
