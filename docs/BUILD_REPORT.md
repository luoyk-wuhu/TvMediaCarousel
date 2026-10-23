# 构建与验证报告

日期：2026-10-23（UTC）。在 Linux 构建容器中执行；未连接 Android 电视、实体遥控器或 U 盘。

- JDK：OpenJDK 21.0.12.1；Gradle：8.7；AGP：8.5.2；Kotlin：1.9.24。
- SDK Platform / Build Tools：34 / 34.0.0。
- 命令：`gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon`（首次还生成 Gradle Wrapper）。
- 最终结果：**BUILD SUCCESSFUL**，51 个任务中 17 个执行、34 个已是最新。
- 单元测试：**6 通过，0 失败，0 错误，0 跳过**。包含模拟输入流超过 2 GiB 的 Long 计数和固定缓冲区验证，不代表真实网络 / 磁盘已完成该体积上传。
- Lint：**0 错误，18 条警告**，未建立忽略错误的 baseline，未关闭检查。警告包括固定旧版依赖、中文界面文字、保守磁盘空间判断、崩溃前同步保存偏好、备份规则建议。API 23 不兼容集合调用已替换。
- 上传网页脚本：`node --check` 通过。
- APK：包名 `com.tvcarousel`，版本 `1.0.0`；aapt 确认 **minSdk 23 / targetSdk 34**。
- 签名：`apksigner verify --verbose` 通过，v1 / v2 均有效。工具同时提示部分 META-INF 元数据不在 v1 JAR 条目签名保护内；APK 整体另有 v2 签名。
- 产物：`TvMediaCarousel-1.0.0-debug.apk`，5,942,902 字节，调试签名，可用于安装测试。
- SHA-256：`b00a56fe94b4ed9ab81d518ecba25ae4be198c573bbf435ebbf967b4914b41a7`。

原始证据在 [verification](verification/) 中：构建日志、单元测试 XML、Lint XML / HTML、APK 签名和 badging 输出。

## 未执行的验收

尚未执行 Android 6 / Android 9+ 真机安装、实际硬件解码、四方向视觉检查、实体遥控器、U 盘热插拔、浏览器端 2 GiB 上传、重启自启动、24 小时 / 168 小时长稳测试。请按 [验收清单](ACCEPTANCE.md) 在目标设备验证；不能将编译和静态检查通过当作这些测试通过。

Android 后台限制仍可能阻止开机广播直接打开界面；长期部署优先使用默认桌面模式及厂商自启动白名单。SAF 需要首次授权，系统文件选择器是否存在取决于固件。
