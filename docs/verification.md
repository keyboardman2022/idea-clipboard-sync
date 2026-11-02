# 验证记录

最新验证日期：2026-11-02（Asia/Shanghai）；下方保留旧版本的历史记录。

## 0.3.1：接收端隐藏相同文件

- 安装包：`build/distributions/idea-clipboard-sync-0.3.1.zip`，97,019 字节；SHA-256：`26803B1D606BDE547174D9B68D5EF80ABCE536A669F3BF880D7173A0356582E7`。
- 接收预览仅展示 ADD、MODIFY、BLOCKED，对比结果为 SAME 的文件不进入目录树，仅含这些文件的子目录自然消失。原始快照和全部对比结果保留，源端选择、传输、写入与重新比较逻辑不受影响。
- 接收文件全相同时提示“接收的文件均相同，无需同步”，禁用应用按钮；目标端独有文件仍保留。手动、自动和重新预览共用同一展示入口。
- 测试：**53 项发现、51 项通过、0 项失败、2 项环境中止**，日志 `build/hide-same-tests.log`。新增实际目录测试覆盖新增/修改/冲突保留、相同文件和空子目录隐藏、全相同为空、重新比较后文件变化重新出现，并断言完整快照未被展示过滤改写。
- 更新真实 FileTree 无窗口交互/渲染检查，验证相同文件、docs 和 src/test 子目录不再出现在接收树；既有勾选、锁定和接收可选项检查通过。预览图已同步更新。
- 2025.1.2 API 打包与 2025.3.3 SDK 编译通过。官方 Plugin Verifier 对最终 0.3.1 安装包的 IC-251.26094.121 和 IU-253.31033.145 均报告 Compatible，见 `build/verifier-hide-same-final/`；最低 SDK 子集和真实 IDE/RDP 验证范围限制与下方旧记录一致。
- Windows PowerShell 5.1 单份和 11 份分段安装文本还原校验通过，包含逆序和重复片段。目录 `build/distributions/clipboard-install-0.3.1/`。

## 0.3.0 历史：目录树与取消备份

- 插件：`build/distributions/idea-clipboard-sync-0.3.0.zip`，96,638 字节。
- SHA-256：`82F0F65FCFDF07AF930DC1943F0834835E0A2B017649386901087FCCF9847B86`。
- 本次按用户明确要求更换发送与接收选择视图，移除备份创建、备份模型和恢复入口。保留 0.2 自动收发协议、差异预览、未保存文档保护、预览后修改检查、文件路径与摘要检查，以及临时文件替换和清理。
- 核心测试：`./tools/test-local.ps1`，**50 项发现、48 项通过、0 项失败、2 项环境中止**。中止仍为 Windows 符号链接权限和 POSIX 权限视图；见 `build/tree-core-test.log`。旧备份/恢复功能的专属测试随功能删除，已有写入保护、部分失败、取消结果和自动传输测试继续通过。
- 新增目录树测试：目录层级、文件夹优先排序、父目录递归选择、边界前缀不串目录、同名文件独立、子文件切换引起父目录部分选择、半选目录恢复全选、接收端相同/冲突项不可选择、选择冻结、空目录树、10,000 文件计数。选择后的快照往返和应用只产生被选中目录的文件，保留目标端独有文件。
- 无备份测试：在旧备份路径被普通文件占用时仍正常同步；检查目标目录全部文件集合，确认不创建备份、不改动旧状态、不遗留临时文件。该测试实际写临时目录，不修改用户代码。
- 实际 Swing `FileTree` 组件测试：运行 `tools/TreeUiSmoke.java`，验证构造、空格键、文件夹/子文件勾选、鼠标复选框点击、生成后锁定和接收端过滤；渲染到 `build/tree-selection-preview.png` 并目视检查。发现并用失败断言复现了树渲染器子控件未布局导致空白行的问题，修复后渲染与交互检查通过。此项使用真实组件的无窗口环境，不等同于完整 IDEA 会话操作。
- 编译：2025.1.2 平台 API 与 2025.3.3 SDK 均通过，日志 `build/tree-package.log`、`build/tree-compile-253.log`。最终 ZIP 使用最低目标版本的 API。
- 官方 Plugin Verifier 1.411：对 **IC-251.26094.121 与 IU-253.31033.145 均为 Compatible**，没有插件二进制兼容问题或弃用 API 使用提示。报告 `build/verifier-tree/`，日志 `build/verifier-tree.log`。最低 SDK 的官方库子集/缺少可选组件布局提示与旧记录一致，不将其解释为完整 IDE 启动验证。
- 单份文本与 11 份分段安装文本已在 Windows PowerShell 5.1 还原验证；逆序和重复片段还原的 ZIP 校验值一致。目录 `build/distributions/clipboard-install-0.3.0/`。
- 一次独立只读审查无 Critical/Important 问题。当前运行源码和最终 JAR 不包含 BackupStore、RestoreBackupAction 或恢复菜单；旧版本备份文件未删除。
- 未在用户的完整 IDEA 窗口中操作新目录树，也未重新测试实机 RDP。自动传输协议未改变，沿用用户已确认满足需求的 0.2 逻辑。

## 0.2.0 历史：自动收发交付（2026-10-23）

- 插件：`build/distributions/idea-clipboard-sync-0.2.0.zip`，94,158 字节。
- SHA-256：`06624C3423ACAF42FF0AF3FD84541FCB81C2EE6CF084051A799F71C41B863F99`。
- 测试命令：`./tools/test-local.ps1`。**43 项发现，41 项通过，0 项失败，2 项环境条件中止**；中止项仍为符号链接创建权限与 POSIX 权限视图。日志：`build/auto-final-test.log`。
- 自动传输测试使用超过 300 个真实压缩/编码分段、真实临时目录接收缓存和虚拟时钟，覆盖确认丢失后重发、错误确认 ID/摘要、重发上限、暂停继续、剪贴板被普通内容替换、损坏分段拒收、末段重复确认、完整快照解码后才完成、磁盘缓存损坏检测。
- 审查修正回归：遗留 DATA、遗留 HELLO、延迟 READY 不会绑错新会话或无故暂停；后台忙时最终预览延后执行，过期窗口不会显示旧预览。UI 进度使用不可变发布值，暂停/继续命令在工作线程排队，不从 EDT 等待完整磁盘校验。
- 编译：官方 2025.1.2 平台 API 与本机 2025.3.3 SDK 均通过。最终安装包由最低版本 API 编译；`build/auto-final-package.log` 与 `build/auto-compile-253.log` 记录结果。
- 官方 Plugin Verifier 1.411：`dev.clipboard.sync.directory:0.2.0` 对 **IC-251.26094.121 与 IU-253.31033.145 均为 Compatible**，无二进制兼容问题；各有原有恢复选择框的 1 项弃用 API 提示。报告位于 `build/verifier-auto/`，日志 `build/verifier-auto.log`。251 的验证库子集与可选模块警告限制与下方历史记录相同。
- Windows PowerShell 5.1 安装文本校验：整份文本、11 份分段逆序执行、重复执行一段均可还原完全相同 SHA-256 的 0.2.0 ZIP。安装文本目录：`build/distributions/clipboard-install-0.2.0/`。
- 0.2.0 自动模式的两端均须升级；手动数据格式仍兼容。没有修改 mstsc 设置、Windows 策略或用户正在运行的 IDEA 安装。
- **未实际验证真实 mstsc 会话中的剪贴板传播、RDP 断线重连、两端 GUI 操作及慢磁盘下的实际 UI 响应**。当前结果为自动协议模拟、文件系统测试、SDK 编译和二进制兼容检查，不是实机 RDP 验收。

## 以下为 0.1.0 历史记录

## 交付产物

- 插件 ID：`dev.clipboard.sync.directory`，版本：`0.1.0`。
- ZIP：`build/distributions/idea-clipboard-sync-0.1.0.zip`，77,092 字节。
- SHA-256：`50AF9D1604D93FD531781F64678670B3BD60226F1D37DA6584C3DC1D4AE96945`。
- 包含 `idea-clipboard-sync/lib/idea-clipboard-sync.jar`，描述文件声明 `since-build=251`、`until-build=253.*`。
- 最终交付 ZIP 以官方 IDEA 2025.1.2 API 编译；SDK 和测试库不打入插件。

## 自动测试

命令：`./tools/test-local.ps1`。

实际使用 Java 21、JUnit Platform Console Standalone 1.11.4。结果：**28 项发现，26 项通过，0 项失败，2 项按环境条件中止**。

覆盖：递归扫描、中文与 CRLF、二进制原始字节、默认及自定义忽略、大小/数量边界、取消扫描；快照往返、空快照、重复路径、错误摘要、截断与超限；乱序接收、重复段、会话隔离、重启续传、坏段拒绝与取消清理；目录新增与父目录创建、已有内容更新、目标独有文件保留、预览后修改保护、文件/目录与大小写冲突、非法路径、备份失败、恢复保护；未保存文档守卫、提交时再次检查、模拟提交失败、部分取消、双向目录往返；取消后部分结果交付和恰好一次回调。

环境中止项：

1. 符号链接测试：当前 Windows 账户缺少创建符号链接权限。
2. POSIX 可执行权限测试：当前 NTFS 环境不提供 POSIX 文件属性视图。实现保留已有目标文件 POSIX 模式，但 Linux/macOS 上的该用例未在本次环境实际运行。

日志：`build/test-final.log`。测试读取/写入的是临时目录，不是业务代码目录。

## 编译与打包

1. `./tools/build-local.ps1`：**通过**。使用从官方 `ideaIC-2025.1.2.win.zip` 通过 HTTP Range 提取并校验 ZIP CRC 的 API JAR；版本 `IC-251.26094.121`。最终 ZIP 出自此路径。
2. `./tools/build-local.ps1 -IdeHome "D:\IntelliJ IDEA 2025.3.3"`：**通过**。实际安装 SDK 为 `IU-253.31033.145`。之后用最低 SDK 重新生成最终交付 ZIP。

日志：`build/compile-251-delivery.log`、`build/compile-253-post-review.log`。

本地脚本使用实际 Java 21 编译器以及 `-source 21 -target 21`。当前沙箱中 GraalVM 的 `--release 21` 在关闭 `ct.sym` 时产生 `AccessDeniedException`；没有更改全局 JDK 配置。

标准 Gradle 9.0.0 / IntelliJ Platform Gradle Plugin 2.19.0 构建入口保留。最初 Gradle 无法访问插件仓库；显式使用当前会话 HTTPS 代理后已成功解析插件，并修正验证 IDE 列表的 DSL 为 `create(...)`。

`gradle help`：**BUILD SUCCESSFUL**，见 `build/gradle-config.log`。完整的标准 `gradle test buildPlugin verifyPlugin` 未在本次环境重新跑完；测试、编译、ZIP 打包、Plugin Verifier 分别使用上述独立入口完成，不能把 `gradle help` 当作完整构建成功。

## JetBrains Plugin Verifier

工具：官方 `verifier-cli-1.411-all.jar`。离线执行，验证工作目录显式放在工程 `build/` 中，避免沙箱账户默认缓存路径与同时运行造成的缓存冲突。

```powershell
java "-Dplugin.verifier.home.dir=<project>/build/verifier-workspace" `
  -jar .tools/verifier-cli-1.411-all.jar check-plugin `
  build/distributions/idea-clipboard-sync-0.1.0.zip `
  .tools/idea-2025.1.2 "D:\IntelliJ IDEA 2025.3.3" `
  -runtime-dir "D:\IntelliJ IDEA 2025.3.3\jbr" `
  -verification-reports-dir build/verifier-delivery -offline
```

结果：

| 目标 | 结果 |
| --- | --- |
| `IC-251.26094.121`（2025.1.2） | Compatible |
| `IU-253.31033.145`（2025.3.3） | Compatible |

两边均无二进制兼容问题。各有 **1 项弃用 API 提示**：恢复备份选择框使用 `Messages.showChooseDialog`，两个目标版本仍提供该方法。

2025.1.2 使用从官方包提取的编译/验证平台库及官方模块描述文件，未提取 Android、Kotlin 等无关捆绑插件；Verifier 会打印这些未提取组件的缺失警告。本插件仅声明平台依赖，其所使用的 API 均完成解析。2025.3.3 使用本机安装目录，也有部分可选组件布局缺失警告。该验证结果不能代替完整 IDE 启动和真实界面操作。

完整日志：`build/verifier-delivery.log`。报告：`build/verifier-delivery/IC-251.26094.121/` 与 `build/verifier-delivery/IU-253.31033.145/`。

## 剪贴板首次安装文本

使用最终 ZIP 生成单份安装文本及 9 份约 12 KiB 的分段文本，并在 **Windows PowerShell 5.1** 下执行 `tools/test-installer.ps1`。

- 单份文本还原：通过。
- 全部分段反向顺序执行、重复执行一段、最后拼装：通过。
- 两种方式还原的 ZIP 与交付 ZIP 的 SHA-256 完全相同。

这里验证的是脚本还原逻辑，未经过用户虚拟机软件的实际剪贴板桥接。

## 审查与已知边界

完成一次独立、只读的全项目审查，无 Critical 问题。已修复两项 Important：取消后部分写入结果被 IDE 任务回调吞掉；替换现有可执行文件时丢失 POSIX 权限。另补充了发送窗口的跳过项明细。

已记录的限制：超限扫描会在文件选择前停止，需先配置忽略或选择更小目录；一份损坏的备份清单会阻止备份列表加载，需先移走该次损坏备份；文本差异仅对 UTF-8 提供预览。文件原始字节传输不受文本预览编码限制影响。

尚未实际验证：两台 IDEA 的真实界面交互、虚拟机剪贴板桥接、Linux/macOS 实机、跨系统路径与权限的实际操作。没有向用户正在运行的 IDEA 安装插件，也没有修改业务源码。
