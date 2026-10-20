# 调研与设计整理

## 1. 调研范围

开发前检索并比较了开源 Android 文件管理器、WebDAV 协议库、SAF 持久授权、后台同步与媒体播放的公开实现：

- **Voyager**：Jetpack Compose + Material 3 的 Android 文件管理器，内置 WebDAV/SFTP/SMB；对本地/远端复制、局域网服务端、音视频流和 Material You 的适配可参考。
- **sardine-android**：基于 OkHttp 的 WebDAV 客户端，用 PROPFIND 列出资源，提供 MKCOL/MOVE/DELETE/PUT/GET；说明 Android 端使用 WebDAV 的基本协议路径与目录 URL 需要尾斜杠等细节。
- **dav4jvm / DAVx⁵**：JVM 上的 WebDAV/CalDAV/CardDAV 库，采用类型化 PROPFIND 属性与 OkHttp，是成熟客户端如何组织 DAV 属性的参考。
- **Nextcloud Android / OwnCloud Android**：成熟的离线优先同步、账户、后台任务与冲突处理参考。
- **Orgzly**：基于 Sardine 的双向同步、版本跟踪与冲突状态设计的轻量参考。
- **Mindwtr**：WebDAV 条件写入（ETag）与按设备配置后台同步周期的实践参考。
- **Android Storage Access Framework**：`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` + `DocumentFile`，不依赖广泛存储权限，兼容 Android 14 的分区存储。
- **WorkManager**：`CoroutineWorker` + 唯一周期任务 + 网络约束；最小周期 15 分钟，系统可能因省电策略延迟。

## 2. 关键结论

1. **协议层自己实现更可控**：本应用只需要 RFC 4918 的 PROPFIND/GET/PUT/MKCOL/DELETE/MOVE 子集。直接使用 OkHttp + Android 的 XmlPullParser，能避免额外 XML/JAXB 依赖，并可针对超时、自签名证书和大文件进度做统一处理。
2. **目录 URL 必须规范**：WebDAV 集合 URL 以 `/` 结尾；路径中的空格和中文由 OkHttp 的 `HttpUrl.Builder` 逐段编码，`Destination` 头使用完整编码后的绝对 URL。
3. **本地目录使用 SAF，而不是全盘文件权限**：用户可以为每个挂载选择一个可持久写入的目录；未选择时退回应用私有存储。所有 UI 文件读取最终都落到该目录，符合 Android 10+ 的分区存储模型。
4. **同步需要基线**：仅比较 mtime 在 WebDAV / SAF 代理不提供精确时间时会误判；以 ETag（优先）+ 大小 + mtime 组成每文件的同步基线，并持久化到应用私有目录。
5. **非破坏性合并更安全**：自动同步只补齐缺失文件和更新较新版本，不隐式删除任一端；应用内显式删除才同时删除云端与缓存。对于两端都变化的文件，选择更晚修改的一端并记录冲突。
6. **后台同步用 WorkManager**：每个账户一个唯一周期任务，支持 15 分钟到 24 小时的周期；打开账户和保存文本后额外触发同步。
7. **媒体与图片本地化**：为保证 SAF 目录是一致的离线缓存，打开文件时先确保本地有文件，再使用 Media3/Coil；不支持内置预览的类型通过 `content://` URI 授权给外部应用。
8. **自适应布局不必引入重型导航库**：使用 `BoxWithConstraints` 在手机端显示可滑出的 `ModalNavigationDrawer`，在平板宽度下显示可折叠的常驻侧边栏，文件区在列表/自适应网格之间切换。

## 3. 参考链接

- Voyager — https://github.com/AlanHuang99/Voyager ，F-Droid: https://f-droid.org/packages/com.voyagerfiles/
- sardine-android — https://github.com/thegrizzlylabs/sardine-android
- dav4jvm — https://github.com/bitfireAT/dav4jvm
- DAVx⁵ OSE — https://github.com/hannesa2/davx5-ose
- Nextcloud Android — https://github.com/nextcloud/android
- Orgzly WebDAV sync — https://github.com/orgzly/orgzly-android
- RFC 4918 (WebDAV) — https://www.rfc-editor.org/rfc/rfc4918
- Android Storage Access Framework — https://developer.android.com/guide/topics/providers/document-provider
- Android 持久 URI 权限 — https://developer.android.com/guide/topics/providers/document-provider#persist
- WorkManager 周期任务 — https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work#schedule_periodic_work
- ExistingPeriodicWorkPolicy — https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy
- Jetpack Compose Material 3 — https://developer.android.com/develop/ui/compose/designsystems/material3
- Media3 ExoPlayer — https://developer.android.com/media/media3/exoplayer
- Coil — https://coil-kt.github.io/coil/

## 4. 本实现的取舍

- 认证仅支持 HTTP Basic（并在设置中说明）。没有实现 Digest/OAuth，以保持连接配置简单可靠。
- 允许用户显式开启“接受不受信任 TLS 证书”，用于自建 NAS 的自签名证书；默认关闭。
- 自动同步采用“最新修改优先、非破坏性补齐”；如需严格的镜像删除语义，需要后续引入持久删除墓碑（tombstone）。
- Markdown 渲染覆盖标题、段落、强调、行内代码、围栏代码、列表、引用、分隔线、链接与表格，未实现完整 CommonMark。
- 音频/视频使用内置 Media3 播放器；通过本地缓存播放，不做边下边播。
