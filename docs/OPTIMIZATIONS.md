# 优化记录与后续建议

## 本次落地

- 移除两处密码及旧密码偏好，不再有密码门禁或密码输入页。
- 系统 SAF + AndroidX DocumentFile 处理目录授权，支持本机和可移动存储、持久授权、撤销目录，避免手写不兼容 Android 受限存储的文件管理器。
- 选择目录后关闭整盘发现；嵌套的 SAF 授权在一次扫描中按文档 ID 去重。
- 保留 Media3 视频解码、Glide 封面和图片加载、ZXing 二维码；预览尺寸采样，每页最多 24 项，页面切换清理 Glide 请求。
- 增加触控播放控制和媒体工作台，保留旋转与遥控器焦点；设置数值保留无输入法时可用的屏幕数字键盘。
- 无密码 API 保留 Host / Origin 检查，新增非秘密自定义请求头，拒绝浏览器跨站简单请求；请求连接从客户端集合移除使用 finally，避免异常路径残留。
- 上传页支持取消、总字节进度和当前页面内继续剩余文件，已确认成功的文件不会重复上传。若响应在提交成功后丢失，该文件仍可能重复，未声称具备幂等或断点续传。
- 构建时发现并验证了 Windows 中文路径下的 AAPT2 链接失败，使用临时英文盘符构建；没有在源码中关闭 AGP 路径检查，也未将本机 SDK、代理或工具缓存写进可分发源码。

## 仍值得做，但需要独立验证

- 超大库可迁移 RecyclerView + Room 分页；当前播放器列表仍整体加载，2 万项上限不等同于任意规模。
- 长期部署可为文件复制增加任务进度、取消和缓存清理策略；当前本地副本不会随源文件删除，以保护离线播放。
- 不稳定 WiFi 的大文件上传可使用成熟的 tus 协议实现单文件续传、校验和幂等。当前不引入未验证的新协议库。
- 真机补测 Android 6 / 9、电视厂商 SAF、U 盘热拔出、硬件视频解码，以及 24 / 168 小时内存和播放器稳定性。

## 复用的开源组件

| 组件 | 用途 | 上游 |
|---|---|---|
| AndroidX Media3 1.3.1 | ExoPlayer 和视频渲染 | https://github.com/androidx/media |
| Glide 4.16.0 | 图片解码、封面采样和缓存 | https://github.com/bumptech/glide |
| AndroidX DocumentFile 1.0.1 | SAF 目录遍历 | https://developer.android.com/training/data-storage/shared/documents-files |
| ZXing core 3.5.3 | 离线上传二维码 | https://github.com/zxing/zxing |

沿用工程已有固定版本，避免在界面重构时同时变更解码器。Media3、DocumentFile、ZXing 使用 Apache-2.0；Glide 包含多种许可证，见上游 LICENSE。未复制第三方应用界面或未审查的项目源码。
