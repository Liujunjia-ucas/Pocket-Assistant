# Pocket Assistant Community

一款 Android AI 个人助理和电脑 Codex 远程客户端。支持手机自行配置模型 API、记录待办和笔记、可选通讯信息整理、文件生成和保存、语音陪伴，以及通过 Tailscale 连接自己的电脑。

这是独立社区项目，与 OpenAI、DeepSeek、其他模型厂商及 Tailscale 没有官方隶属或合作关系。品牌名称仅用于描述兼容能力。项目名称为暂定名称，未声称完成商标注册或全球商标检索。

**这是经过素材清理和安全加固的社区源码版。不是原私人安装包的直接公开副本，也不构成“零漏洞”“零侵权”认证。** 请先阅读 [风险与授权边界](RISK-NOTICE.md)、[隐私说明](PRIVACY.md) 和 [安全说明](SECURITY.md)。

## 包含的功能

- 手机直连：自行配置主流模型平台的 HTTPS API、Key 和模型 ID；API 可用范围及费用由平台决定。
- AI 助理：待办、随手记、自然语言指令分类；短信、通话、邮箱和通知整理需要分别授权并开启自动同步。
- 电脑连接：调用用户自己安装和配置的 Codex CLI，接收流式结果、处理审批、上传附件和下载工作区内生成文件。
- 实验室：汐月、凌雪两个虚构的文字/语音陪伴设定，使用原创程序绘制的语音光环。支持手机系统语音服务和用户配置的火山语音。
- 数学公式和 Markdown、本机文件导出、颜色主题及深浅色模式。

## 公开版的删减与默认值

- 不包含鲸鱼娘、龙娘的立绘、动画、壁纸、角色精灵图、原图截图或下载/重建这些素材的脚本。没有第三方厂商 Logo 或仿官方启动图标。
- 不包含离线语音模型、原生 ASR 库、音色包、电脑语音环境、FFmpeg、旧安装包或私人生成记录。手机系统缺少语音服务时请使用文字或输入法语音；没有自动下载替代模型。
- 不预置私人邮箱、真实电脑 IP、配对信息、API Key 或第三方 API 中转服务。OpenAI 选项默认官方 API；内部历史配置槽名不代表指定中转商。
- 自动同步、云端通讯整理、请求自动附加私人记录、后台常驻、长期记忆提取和联网搜索默认关闭。关闭同步不会撤销系统已授予的权限，请按需在系统中撤销。
- 新建电脑会话默认只读。电脑桥接服务默认只绑定 `127.0.0.1`，远程代理开关默认禁用。
- 结构化本机对话、待办、工作记录、角色记忆通过 Android Keystore 与 AES-GCM 加密；禁止应用备份/设备迁移，隐藏截图与最近任务预览。详情及不覆盖的临时文件见隐私说明。

## 编译 Android

需要 JDK 17、Android SDK Platform 34 / Build Tools 34.0.0、Gradle 8.7。首次构建需要访问 Google Maven 和 Maven Central。建议使用 Android Studio 打开根目录并配置 SDK，或在终端运行：

```powershell
$env:JAVA_HOME = '你的 JDK 17 目录'
$env:ANDROID_HOME = '你的 Android SDK 目录'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-android.ps1 -GradleExecutable '你的 Gradle 8.7 的 bin\gradle.bat'
```

Linux/macOS 配置 `JAVA_HOME`、`ANDROID_HOME` 后运行 `gradle --no-daemon assembleRelease lintRelease`。

仓库不附带 Gradle 可执行文件或 Wrapper JAR；请从 [Gradle 官方](https://gradle.org/releases/) 安装 8.7，或使用 Android Studio 配置该版本。Java 包命名保留历史命名空间，但独立应用 ID 是 `org.pocketassistant.community`，不会覆盖原私人版。

输出 `app/build/outputs/apk/release/app-release-unsigned.apk` 为 **不可直接安装的未签名 Release 包**，`debuggable=false`。使用自己的私钥签名后才可安装；参考 [发布指南](docs/PUBLISHING.md)。构建过程不会自动创建、读取或上传你的正式签名密钥。

## 手机独立使用

安装自己签名的 APK，在设置 → 模型与 API 中选择平台，填写你申请的 API Key、HTTPS 地址及平台实际提供的模型 ID。模型示例不代表该账户已经开通或厂商承诺持续供应。不需要连接电脑；语音服务另行配置。不要把 Key 写进源码、截图或 Issue。

在设置 → 隐私与数据中自行选择是否自动接入通讯资料，以及是否允许云端读取。**授予系统权限与允许云端处理是两回事。** 自动整理不是可靠的闹钟或通讯服务替代品，请核对重要事项。

## 电脑远程使用

需要 Node.js 22 或更新版本、用户自己安装并授权的 Codex CLI。先在电脑确认 `codex --version` 和独立启动 Codex 正常。安装 Codex/Tailscale 是用户自己的操作，本项目不附带这些软件或其账号。

```powershell
# 创建一个专门的项目/输出目录，然后设置它的绝对路径。
$env:CODEX_POCKET_CWD = '你的专用工作目录绝对路径'
# 本地自测默认只监听回环地址。
npm start
```

需要手机连接时，在电脑和手机连接自己的同一个 Tailscale 网络，运行 `tailscale ip -4`，将结果自行设置为 `CODEX_POCKET_HOST` 后再启动。只接受回环或 `100.64.0.0/10` 地址。**不支持绑定 `0.0.0.0`、公网 IP 或公开反向代理；不要通过 Funnel/公网端口映射暴露服务。** 在 Tailscale ACL/设备管理中仅允许自己的受信任设备连接。

启动时会在本机生成被忽略的 `pairing.txt`；把其中地址和令牌填写到手机电脑连接设置。令牌不能发给他人。HTTP 传输依赖 Tailscale 隧道加密，地址范围检查不能证明对端身份或设备安全。`.ts.net` 域名仅接受 HTTPS，需要用户自行配置证书/Serve；本项目不自动配置这些服务。

默认从本机 Codex `config.toml` 获取模型和服务配置；多模型可以通过 `CODEX_POCKET_MODELS_FILE` 指定仅供本机使用的 JSON 文件，格式见 [配置示例](docs/models.example.json)。文件中填写自己的模型 ID、provider 和对应 TOML 文件名；凭据保留在用户环境变量/CLI 登录状态，不复制到项目。没有 CLI 配置时服务会报告未就绪。

文件下载只允许专用工作区内的普通文件，拒绝常见秘密目录/文件、硬链接、指向区外的符号链接及服务运行数据。这个过滤器不能判断任意文档内容是否敏感；**不要把整个用户目录作为工作区，也不要在工作区放真实密钥。** Codex 执行本身仍受其自己的沙箱和审批机制约束，详见安全说明。

可选的 Windows Clash Verge 控制需要电脑端显式设置 `CODEX_POCKET_ALLOW_PROXY_CONTROL=1`。原生控制辅助配置脚本会修改你本机的 Verge 快捷键并重启 Verge，**不会自动执行**；只有理解影响且需要功能时才手动运行 `tools/setup-clash-native-control.ps1`。默认关闭不影响普通 API 使用。

## 验证与源码发布

```powershell
npm test
python tools/audit-publication.py
# 需要 JDK 17；测试 JSON 库使用者自行获取，参数见脚本。
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\test-core.ps1 -JsonJar 'json-20240303.jar 的路径'
```

测试库为 [JSON-java 20240303](https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar)，脚本会验证其 SHA-256；该 JAR 不进入源码包。

手机实机行为、系统语音服务、HyperOS 权限和付费模型仍需要部署者自行验证。构建/测试结果及限制见 [验证记录](docs/VERIFICATION.md)。

本项目自有代码、文档和原创几何视觉使用 [MIT](LICENSE)，允许商业使用；第三方依赖保留各自许可，见 [第三方声明](THIRD-PARTY-NOTICES.md)。不要用根目录 MIT 许可证给你后来导入的第三方角色、语音或模型重新授权。

解压源码包后上传里面的项目文件，而不是只向 GitHub 上传一个 ZIP。发布前重新运行审计，按 [发布指南](docs/PUBLISHING.md) 检查文件清单。仓库不包含任何私人运行数据，也不会自动向 GitHub 发布。
