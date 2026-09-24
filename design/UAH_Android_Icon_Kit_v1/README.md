# Used AI Harness · UAH

这是实际图标资源包，不是展示海报。所有图标都是独立文件；SVG 与 Android XML 使用同一组几何路径，PNG 从这些矢量路径直接导出。

## 直接拿文件

| 用途 | 文件 |
| --- | --- |
| 默认圆角方形，暖白底，1024px | `icons/uah-icon-1024.png` |
| 默认图标，512px | `icons/uah-icon-512.png` |
| 仅标志、透明背景，1024px | `icons/uah-mark-transparent-1024.png` |
| 可编辑、无字体依赖的 SVG | `artwork/uah-mark.svg` |
| 含圆角底的完整 SVG | `artwork/uah-icon.svg` |
| 圆形图标，512px | `icons/uah-icon-round-512.png` |
| 正方形、无外阴影、512px 商店备用图 | `store/uah-play-512.png` |
| 黑/白透明标志 | `icons/uah-mark-charcoal-512.png` / `icons/uah-mark-white-512.png` |
| 深色视觉备选 | `icons/uah-icon-dark-512.png` |

打开 `preview.html` 可以直接浏览和点开单个文件；`previews/actual-resources.png` 是由实际文件合成的检查图，不是另一套绘稿。

## 品牌与外观

- 正式名称：**Used AI Harness**，拼写严格按用户命名。
- 简称：**UAH**。
- 主图形采用 U 形承托框、中央 A 与贯穿横梁的合字；U 的两竖与横梁也呼应 H。
- 主色 `#BE674B`；背景 `#FAF9F5`。纯平面、单色实心，无渐变、投影、纹理、旧轨道或星芒。
- 图标不塞完整名称；全称用于应用名、关于页，短名用于紧凑界面。
- 深色 PNG 是独立视觉备选，**不代表 Android 会随着应用内部深色主题自动切换桌面图标**。正常接入使用默认暖白底；主题化图标由启动器处理 monochrome 层。

## Android 资源

`android/app/src/main/res/` 内有 19 个资源文件，保持当前 Manifest 的 `@mipmap/ic_launcher`、`@mipmap/ic_launcher_round` 引用。

| 目录 | 图像尺寸 / 内容 |
| --- | --- |
| `mipmap-mdpi` | 48×48 PNG，普通 + 圆形 |
| `mipmap-hdpi` | 72×72 PNG，普通 + 圆形 |
| `mipmap-xhdpi` | 96×96 PNG，普通 + 圆形 |
| `mipmap-xxhdpi` | 144×144 PNG，普通 + 圆形 |
| `mipmap-xxxhdpi` | 192×192 PNG，普通 + 圆形 |
| `mipmap-anydpi-v26` | 自适应图标前景/背景声明 |
| `mipmap-anydpi-v33` | 自适应图标 + monochrome 声明 |
| `drawable` | 前景、背景、单色层、应用内标志、备用通知小图标 |

自适应层均为 108×108dp，未预裁圆角、未加阴影；主体留在中心安全区。提供的分层 PNG 在 `adaptive-png/` 中，分别是 108、162、216、324、432px。它们是可选导出，不要把这几档图像当成 48dp 的旧版 launcher PNG 使用。

`ic_uah_mark` 是 24dp 应用内图标；`ic_stat_uah` 是白色透明通知备用图标。二者不替代 108dp 自适应前景，也不需要因此新增通知服务或权限。

## 集成（不会自动修改仓库）

将本包放在项目的 `design/uah-icon-v1/`。Python 3.10+，安装脚本只使用标准库：

```powershell
# 仅预览：包括应用可见名称的修改
python design/uah-icon-v1/tools/install_icons.py --project . --rename

# 看过计划后执行：先备份，再替换图标及名称
python design/uah-icon-v1/tools/install_icons.py --project . --rename --apply
```

脚本会将当前 `app_name` 修改为 `Used AI Harness`，补齐 `app_name_full` 和 `app_name_short`；不会直接覆盖整份 strings.xml。更多见 `docs/RENAMING.md`。

默认 dry-run 不写任何项目文件。明确 `--apply` 后才把旧资源备份到 `.uah-icon-backups/`，移除同 qualifier 下的旧同名 PNG/WebP/XML 变体，写入新资源。其他 source set 的覆盖项只提示，不擅自修改。

**不要改变 applicationId、namespace、签名、Application/Activity 类名、持久化协议或数据目录。改可见品牌不是创建另一款 Android 应用。** 不做全仓库 `AgentApp`/`Actant` 字符串替换；旧品牌资源使用点由 Codex 定向检查并改用 `ic_uah_mark`。

## 验证边界

已执行的检查见 `VALIDATION.json`，包括尺寸、透明通道、安全区域、SVG/XML 一致性、资源引用和安装脚本临时工程测试。**没有编译或安装你的 APK，没有 Android 启动器实机截图，也没有修改 GitHub 仓库。** `previews/` 中的形状和主题是本地模拟预览，不冒充设备截图。

SVG 中不含字体、位图、脚本或外链；本包不包含字体文件。
