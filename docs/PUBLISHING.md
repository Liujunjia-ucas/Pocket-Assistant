# 发布源码与安装包

## 上传源码

1. 使用本次干净包的解压目录，保留 `.gitignore`；不要改用旧私人工作目录。不要上传旧 APK、完整 `state/` 或 E 盘生产资产。
2. 运行 `python tools/audit-publication.py`、`npm test` 和 Android Release/Lint 检查。审计不是通用秘密扫描器；建议在 GitHub 开启 secret scanning，并使用 Gitleaks 等专用工具复查源码和后续 Git 历史。
3. 在 GitHub 创建一个空的私有仓库，选择自己的仓库名称，不要让 GitHub 自动生成另一个与本包不同的许可证。
4. 用 GitHub Desktop 添加本目录，查看 Changes，检查提交内容，再推送到私有仓库。网页上传也可以，但应上传解压后的源码目录内容，不是只上传一个 ZIP。
5. 核对 README、LICENSE、第三方声明、隐私、安全、风险说明和所有文件；确保没有运行/构建产物、密钥、账号、截图或私有地址。确认后在仓库设置将可见性改为公开。
6. 开启私密漏洞报告、依赖/秘密检查（按账号和仓库可用功能），设置分支保护，避免未经审查的自动发布。仓库本身不附带 GitHub token 或自动上传脚本。

源码包的 `PUBLICATION-MANIFEST.json` 记录本次文件列表与 SHA-256。`tools/audit-publication.py` 会检查这个清单，`--write-manifest` 会在无错误后重建清单；每次有意修改源码需先人工审查，再更新。清单用于发现意外改动，不是数字签名或完整安全证明。

## 正式签名

`assembleRelease` 生成 `app-release-unsigned.apk`。Debug 构建只能供自己开发，不要作为公开正式发行包。

用 Android SDK 的 `zipalign` 对齐，再用 `apksigner sign --ks <你自己的私钥文件> --out <已签名APK> <已对齐APK>` 签名，并运行 `apksigner verify --verbose --print-certs <已签名APK>` 验证。不要把密码作为命令行参数写进脚本/历史，让工具交互提示或使用安全的本机凭据管理。

通过 `aapt dump badging` / Android Studio APK Analyzer 验证应用 ID、版本和 `debuggable=false`，检查 APK 内 assets 只有 renderer 与公开的许可证/隐私/风险说明。正式签名私钥和密码不放源码、ZIP、GitHub Release 或日志；单独离线备份，否则无法用相同签名更新安装。

公开签名证书/指纹不是私钥，可以用于用户核验。若用于 GitHub Actions 自动签名，必须单独设计最小权限、受保护环境与密钥访问，不能把本次本机示例直接改为公开流水线。

## GitHub Releases

源码保存在 Git 仓库，安装包放 Releases，附 SHA-256、版本变化、已知限制和隐私/风险链接。社区源码 ZIP 不包含 APK、密钥或 Gradle 缓存。

当前社区版不包含角色美术和内置离线 ASR。不要使用原人物截图当作社区版预览，也不要声称已通过 Google Play 审核、独立渗透测试、所有手机实测或法律认证。涉及 SMS/Call Log 权限时，在商店发行前单独核实适用政策。

## 发生泄露

停止推送/分发，立即在对应平台撤销密钥，停止电脑桥接并换配对令牌。然后清理 Git 历史和 Release 附件，并告知受影响用户。仅删除最新提交里的秘密文件不足以撤销泄露；Fork、缓存和下载副本可能继续存在。
