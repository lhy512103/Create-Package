# Forge 1.20.1 自动构建与发布

## 一次性配置

目标仓库：`lhy512103/Create-Package`，工作分支：`forge-1.20.1`；不会修改 NeoForge 的 `main` 分支或抢占其 Latest Release。

在 **Settings → Secrets and variables → Actions** 配置：

| 类型 | 名称 | 值/用途 |
| --- | --- | --- |
| Variable | `MODRINTH_PROJECT_ID` | `5vJMWJv6`（Create Package） |
| Variable | `CURSEFORGE_PROJECT_ID` | `1596843`（Create Package） |
| Secret | `MODRINTH_TOKEN` | 有权读取项目/版本、创建版本和修改版本的 Modrinth 令牌 |
| Secret | `CURSEFORGE_TOKEN` | CurseForge **作者后台 Upload API Token**，不是 Core API 开发者读 API Key |

两个项目 ID 同时提供工作流默认值；令牌没有默认值，缺失时对应平台明确失败，不伪装为发布成功。`GITHUB_TOKEN` 由 Actions 自动提供，无需配置 PAT。不要提交令牌、写入日志或在 Issue 中粘贴令牌。

发布器需要向 GitHub Release 附加防重复发布的 intent/receipt JSON，所以仓库不能开启阻止发布后新增附件的 **Immutable Releases**。如果组织规则禁止 `contents: write` 或限制 Actions，先由管理员放行本仓库工作流；构建/PR本身仅有读权限。

## 平时 CI

[build.yml](.github/workflows/build.yml) 在 `forge-1.20.1`、旧别名 `1.20.1-forge` 的 push/PR，以及手动运行时执行：

1. Python 发布工具离线单元测试（mock 网络，不上传任何内容）。
2. JDK 17 下执行 `clean build gameTestClasses`，包含 Java/JUnit 测试及 GameTest 编译。
3. 保存发行 JAR 和 JUnit 报告为 Actions artifacts。

CI 不执行真实平台发布。实际游戏内 GameTest 可在本地执行 `./gradlew runGameTestServer`；不把 GameTest 类或结构放进发行 JAR。

## 发一个新版本

1. 修改 `gradle.properties` 的 `mod_version`，例如 `20.1.2`。
2. 将要发布的 `CHANGELOG.md` 中 Unreleased 内容归入准确的版本标题，例如下例；保留简短的双语有序条目。
3. 按仓库约定本地验证、提交并推送 `forge-1.20.1`。
4. 创建并推送新 tag；示例只是操作说明，不会由配置过程自动执行：

```bash
git tag -a forge-1.20.1-v20.1.2 -m "发布：20.1.2"
git push origin forge-1.20.1-v20.1.2
```

支持 `forge-1.20.1-v20.1.2`（推荐）、`v20.1.2`、`20.1.2`，以及 `-alpha.1`、`-beta.1`、`-rc.1` 预发布后缀；每个版本只能选一个 tag，不要混用别名重复发版。只有 `20.1.x` 版本线会进入本 Forge 发布流程，tag 必须准确匹配项目版本并指向本分支中的提交。alpha 标记 alpha；beta/rc 标记 beta；无后缀标记 release。

```markdown
## [20.1.2] - 2026-11-08

### 中文

1. 新增：一句话概括用户可见变化。
2. 修复：一句话概括用户可见问题。

### English

1. Added: A one-sentence summary of the user-visible change.
2. Fixed: A one-sentence summary of the user-visible fix.
```

旧版将英文、中文放在两处同名版本段的布局也受支持，会合并两部分并标注语言。**不会退回使用 Unreleased，也不会把整份日志或其他版本放进 Release**；找不到该版本/内容为空/tag 与版本不符均会停止。当前源码仍是 `20.1.1`，且这个版本已经发布过；本次只安装工作流，不擅自改版号、发布 Unreleased 或创建 tag。

## 自动发布链

[release.yml](.github/workflows/release.yml)：

```text
推送新 Forge tag
  → 核对版本、日志、分支归属
  → Python 测试 + Java 17 干净构建
  → 验证 build/libs/createpackage-版本.jar
  → 创建草稿 Release 并附上 JAR + 该版本中英文日志
  → 确认 JAR 已附上，再公开 GitHub Release
  → 显式调用 Publish release assets（两个平台分别运行）
```

显式调用不可省略：用 `GITHUB_TOKEN` 创建的 Release 不会再触发另一个工作流的 `release` 事件。首发不能只依赖两个互相独立的事件监听器。

[publish.yml](.github/workflows/publish.yml) 也监听已公开 Release 的 `published`/`edited`，以及手动运行入口；每次读取 **GitHub 当前 Release 正文**，而不是旧事件快照或整个 changelog。它下载该 Release 的准确发行 JAR，核对 ZIP、`mods.toml` 的 modId/version、Java 17，再把同一 JAR 与完整正文上传到 MR/CF，并标记 `Forge`、`1.20.1` 及必需的 Create/AE2 依赖。

**必须有 JAR 才能执行任何 MR/CF 发布或正文更新。** 缺附件、空文件、假 JAR、多个候选发行 JAR、空正文、版本不符都会失败；`sources`/`dev`/`javadoc` 不算发行 JAR。不会用 Actions artifact、工作区旧文件或最新的另一个 Release 来凑数。

如果手动在 GitHub 发布 Release，先在草稿中附好 JAR 再公开。已经公开但没 JAR 的 Release 不会发往平台；补好附件后再编辑保存正文，或手动运行 **Publish release assets**。单独上传附件不保证触发 `edited`。

## 编辑正文、重试与文件不可变性

- 首次发布会把真实 JAR 和正文一起提交；每个平台成功后，在同一 GitHub Release 留存该平台的版本/file ID、JAR SHA512 等 receipt。
- 编辑 GitHub Release 正文后，先再次验证 JAR 仍在且 SHA512 与首次发布一致，再更新平台原版本正文，**复用已经上传的同一 JAR，不重复上传、不生成重复版本**。
- CF 官方 `update-file` 仅允许修改元数据，不能替换原 JAR。MR/CF统一采取不可变版本规则：二进制有变化，必须提高版本号并创建新 tag，不能挪动 tag 或覆盖同版本文件。
- 已公开 GitHub Release 的 JAR 也不会被本工作流悄悄替换。构建包含时间戳，因此重新跑完整 tag 构建可能得到不同字节并被拒绝；仅需同步正文/重试平台时，应运行 **Publish release assets**，复用原附件，而非重新构建旧版本。
- 每个平台独立串行发布，失败不会取消另一平台；两平台之间不是跨站原子事务。CF上传成功仍可能需要平台审核，不代表立即公开下载。
- GitHub Release 的 intent/receipt JSON 是发布恢复记录，不含令牌。**不要手动删除**；它们防止网络超时后盲目重传。

### 上传结果未知时

创建版本的 POST 不自动重试。脚本会在上传前持久化 intent，上传成功后持久化 receipt。若网络中断/进程退出导致只有 intent 而无 receipt，将停止，不猜测上传是否成功，也不继续创建重复文件。

MR可以用已存在版本及文件哈希恢复。CF没有公开的作者 Upload API 文件列表：先在 CF作者后台核实该 JAR 是否确实上传、核实内容与 SHA512，然后在手动运行 **Publish release assets** 时填写 `curseforge_file_id` 以接管那个已核实的 file ID；正常发布留空。该输入是人工确认，不会把作者 token 当 Core API Key，也不宣称能凭该接口自动验证远端文件哈希。若上传根本未发生，确认无远端文件后再人工处理 intent；不要为绕过失败而直接删除标记重跑。

手动工作流入口需要工作流在默认分支上可被 GitHub 发现；本次只推送 Forge 分支，不自动合并/改动 NeoForge `main`。tag push → 显式调用发布的自动链不依赖默认分支中的新入口；如 Actions UI 尚未显示新的手动入口，请由维护者将工作流入口同步到默认分支后再使用，或使用该 tag 对应的现有工作流运行记录。

## 本地检查（不会发布）

```bash
python -m unittest discover -s tools/tests -v
python tools/release_metadata.py --tag v20.1.1 --output build/release-notes-check.md \
  --jar build/libs/createpackage-20.1.1.jar --check-java17
```

不要在本地随意执行 `create_github_release.py` / `publish_release.py`；带有效令牌时它们会真实写入远端。CI配置本身不写入玩家 changelog，因为它不改变游戏内功能。

## 参考

- [GitHub：GITHUB_TOKEN 触发规则](https://docs.github.com/en/actions/concepts/security/github_token)
- [GitHub CLI：创建 Release](https://cli.github.com/manual/gh_release_create)
- [CurseForge 作者 Upload / Update API](https://support.curseforge.com/en/support/solutions/articles/9000197321-curseforge-upload-api)
- [Modrinth：创建版本](https://docs.modrinth.com/api/operations/createversion/)
- [Modrinth：修改版本](https://docs.modrinth.com/api/operations/modifyversion/)
