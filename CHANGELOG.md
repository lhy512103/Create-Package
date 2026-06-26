# Changelog

## 2026-06-26

- Changed the active job overlay/Jade line to show the primary output display name and refill round instead of the raw sequenced-assembly recipe id.
- Added probabilistic-output refill handling: secondary outputs trigger an immediate refill round, empty-output rounds refill after a configurable timeout, and missing refill inputs are retried without world scanning.
- Added Package Distributor sequenced-assembly test recipes covering spout fluid filling, mechanical cutting, mechanical pressing, and deployer held-item supply, plus an incomplete distributor transitional item.
- Added Package Distributor diagnostics for Create Engineer's Goggles and optional Jade: current status, last rejection reason, active job, AE node state, linked machine names, roles, icons, and positions are now visible in-game.
- Recorded the distributor's last pattern rejection reason and synchronized linked-machine/job state to clients so blocking crafts can be diagnosed without checking logs first.
- Registered the Package Distributor's AE2 network-tool representation and synchronized server-side AE node state to the overlay; deployer/spout consumables can now be satisfied by either the AE2 pattern inputs or existing machine contents.
- Replaced the NeoForge template README with bilingual project documentation covering Package Distributor setup, link order, current AE2 processing-pattern usage, limitations, and performance design.
- Switched project metadata and repository licensing to MIT, added a root LICENSE file, and removed the template license placeholder.
- Implemented the first real package distributor job pipeline: AE2 pattern pushes are now planned, validated, supplied to linked Create machines, and tracked until the primary output is recovered.
- Added low-overhead runtime behavior: no world scanning, no mixins or reflection; the distributor only touches linked block positions and only wakes its AE2 grid ticker while a job is active.
- Added conservative safety checks for incomplete lines, ambiguous recipes, same input/output depot links, unused pattern inputs, and output recovery timeouts.

## 2026-06-26 中文

- 将活动作业在护目镜/Jade 中显示为主产物名称和当前补刷轮次，不再直接显示序列组装配方 id。
- 增加几率产物补刷处理：收到副产物会立即补刷，完全空产出会在可配置超时后补刷，缺少补刷材料时会低频重试且不扫描世界。
- 增加封包分发器序列组装测试配方，覆盖 Spout 流体填充、机械切割、机械辊压和 Deployer 施加物供料，并新增未完成的封包分发器过渡物品。
- 为封包分发器新增 Create 工程师护目镜诊断显示和可选 Jade 信息显示：现在可在游戏内看到当前状态、最近拒单原因、活动作业、AE 节点状态、已链接机器名称、角色、图标和坐标。
- 记录分发器最近一次样板拒单原因，并把链接机器/作业状态同步到客户端，排查阻塞合成时不必先翻日志。
- 注册封包分发器在 AE2 网络工具中的代表物品，并把服务端 AE 节点状态同步到信息显示；Deployer/Spout 耗材现在可由 AE2 样板输入或机器内已有内容共同满足。
- 将 NeoForge 模板 README 替换为中英双语项目文档，补充分发器搭建流程、链接顺序、当前 AE2 加工样板用法、限制与低开销设计说明。
- 将项目元数据和仓库许可证改为 MIT，新增根目录 LICENSE 文件，并移除模板许可证占位文件。
- 实现第一版封包分发器真实作业管线：AE2 样板推送会被解析、校验、投料到已链接的 Create 机器，并等待主产物从终点回收到 AE2 网络。
- 保持低开销运行方式：不扫描世界、不使用 mixin/反射；分发器只访问玩家已链接的位置，并且只有存在作业时才唤醒 AE2 网格 tick。
- 增加保守安全检查：流水线不完整、配方歧义、起点/终点相同、多余样板输入、产物回收超时都会拒单或记录日志，避免丢物品。
