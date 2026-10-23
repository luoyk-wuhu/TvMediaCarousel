# 1.1.0 构建与验证报告

日期：2026-10-23，Asia/Shanghai。以下结论来自本次实际执行。

## 构建

- Windows，Oracle JDK 21.0.3，Gradle 8.7，AGP 8.5.2，Kotlin 1.9.24。
- Android SDK Platform 34 revision 3（base SDK）、Build Tools 34.0.0。
- 任务：`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon`。
- **BUILD SUCCESSFUL**，76 项任务；最终运行 21 项，55 项为最新。
- 单元测试：**8 通过，0 失败**，包括自然排序、超过 2 GiB 的模拟流、上传截断和空间不足、无密码 API 同源策略。
- Lint：**0 错误，19 警告**；未关闭错误检查或设置 baseline。警告主要是已有固定依赖版本、可用空间检查、中文文字、备份规则和恢复偏好同步保存。
- 网页行为测试：`node --test scripts/test-upload.cjs`，**4 通过**，涵盖无密码连接、文件过滤、失败后继续队列、取消后重试。

Windows 中文目录会使 AAPT2 链接资源失败；本次使用临时 W: 映射后实际构建成功，未把关闭路径检查作为修复。下载的 SDK、代理配置、Gradle 缓存和调试 keystore 都在源码包之外。临时映射在打包后移除。

## MuMu 实际设备测试

MuMu 实例 0，Android 15，ADB 127.0.0.1:16384，测试时屏幕为 2560×1440。App 和测试 APK 均安装成功。使用 AndroidX Test Runner 1.6.2、UI Automator 2.3.0。

最终 **4 个仪器测试全部通过**，运行 51.817 秒：

1. 无密码直接进入工作台，触控打开系统文件夹选择器，再返回 App。
2. 在系统选择器内授权 `Pictures/TvCarouselUiTest`；持久读权限保留；关闭整盘发现；直接扫描找到测试图片；重新打开页面仍显示目录；经确认移除目录后源文件可读。
3. 生成两张测试图片，缩略图显示、点选播放、暂停后等待 3.6 秒媒体保持不变、下一项 / 继续、四个方向切换、上传页面入口均正常。
4. 请求 App 内真实 LAN HTTP 服务：无 PIN 列出 / 上传 / 删除成功；缺少请求标记或跨站 Origin 返回 403。图片上传后进入索引，删除后移出。

初轮脚本曾因活动异步启动、不可见项目及过期无障碍节点失败，修正为同步启动、显式滚动和可见状态等待后，完整复测通过。测试脚本不删除已有源媒体，只清理自建测试文件、授权与测试包。安装后的 App 留在 MuMu 中。

测试后查询 AndroidRuntime 错误日志，没有输出崩溃记录。未将这次短流程结果等同于长时间稳定性或电视兼容性验收。

## 视觉检查

- [文件夹管理实景](ui-folders.png)
- [触控播放实景](ui-player.png)
- [播放设置实景](ui-settings.png)
- [上传页浏览器预览](upload-preview.png)，[390px 窄屏预览](upload-mobile.png)

App 截图由 MuMu 真实渲染；播放截图中的测试图由测试代码生成。上传页截图使用本机预览夹具数据，浏览器实际完成一次文件选择和上传。原始模拟器媒体预览截图未放入源码包。

## 产物与签名

- 包名 `com.tvcarousel`，versionName **1.1.0**，versionCode **2**，minSdk **23**，targetSdk **34**。
- APK：`downloads/TvMediaCarousel-1.1.0-debug.apk`，5,668,662 字节。
- SHA-256：`188c01474d56743ca489199a59385088e6c8014c423609a6b8adeee64812c97f`。
- `apksigner verify --verbose --print-certs`：**Verifies**，v1 / v2 有效。
- 新证书 SHA-256：`172d0afb8a044ebcd5eebcf3df30cce192b4bd593b19775a81cc04f74a234045`。
- 原 1.0.0 证书 SHA-256：`94d948fcc6d52206a72d80dfb6ca6869710d905b23016246fcb218d007a7e2f8`。

两个调试签名不同，原版设备不能直接覆盖安装本包。保留原媒体和数据的升级需要原 keystore 重新签名；卸载会删除 App 内部媒体，先备份。此次 MuMu 原本没有安装本 App，测试为全新安装。

## 证据与边界

本次日志、单元测试 XML、Lint XML、签名及 MuMu 结果见 [verification-v1.1.0](verification-v1.1.0/)。旧的 `verification/` 仅为 1.0.0 历史证据。

尚未验证：Android 6 / 9 电视真机、实体遥控器、真实 U 盘热拔出、硬件视频解码、2 GiB 真实网络上传、系统重启 / 自启动、24 / 168 小时长稳运行。文件夹持久授权验证了系统记录与活动重开，未执行整机重启。后续建议见 [OPTIMIZATIONS.md](OPTIMIZATIONS.md)。
