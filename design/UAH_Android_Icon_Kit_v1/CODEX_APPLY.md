# 给 Codex：接入 Used AI Harness / UAH 图标与品牌名称

用户已经选定扁平风格，并命名为 Used AI Harness，简称 UAH。本包是成品资源，不要生成海报，不要从预览裁图，不要另画一个类似符号。

1. 先读当前仓库 AGENTS.md、相关 CLAUDE.md、.Codex/memory/，再读本包 README.md、docs/RENAMING.md、RESOURCE_MANIFEST.json。
2. 视觉标准：先打开 `icons/uah-icon-1024.png`、`icons/uah-mark-transparent-1024.png` 和 `previews/actual-resources.png`。只使用这些实际资源对应的路径。SVG 母版在 `artwork/`。
3. 将 `android/app/src/main/res/` 资源安全合并。优先先运行 `python design/uah-icon-v1/tools/install_icons.py --project . --rename` 查看计划，确认后加 `--apply`。脚本会备份，并处理同 qualifier 下旧同名 .webp/.png 冲突。不要删整个 res/mipmap 目录。
4. 保持 Manifest 的 @mipmap/ic_launcher 与 @mipmap/ic_launcher_round。API26前景/背景、API33单色层、mdpi→xxxhdpi 普通/圆形图一并替换。API33资源不能保留旧单色图。
5. 自适应前景是108dp，不要 trim、额外 inset、套圆角或缩成24dp图标；由启动器蒙版裁切。24dp应用内标志使用 ic_uah_mark。不要让深色设置改写桌面图标。
6. 修改可见名称：app_name/app_name_full=Used AI Harness，app_name_short=UAH。原 strings.xml 中的 app_name 要定向修改，不能在另一 XML 重复定义。检查其他 language/source-set 覆盖；UI中可见的旧名与旧A标志定向迁移为新名/新标志。
7. 不改 applicationId、namespace、Kotlin包、.AgentApp、.MainActivity、签名、provider authority、备份协议标识和存档目录。旧版说明中的“保持构元 Actant名称”已被这次用户改名要求取代，不能继续沿用。
8. 构建 assembleDebug、lintDebug，有业务改动再按规范做相关测试。输出 APK、改动清单、构建结果及简短实机步骤。用户负责实机测试，未授权不得操作手机。不自动push或发布release。
9. 验收：完整显示UAH合字、比例与文件一致；普通/圆形/主题化图标都无旧标；原安装可保留数据更新；应用名称正确。启动器缓存不是修改应用身份/清空数据的理由。

本包只做了离线资源与脚本测试，没有编译或安装当前仓库 APK；不要把 VALIDATION.json 描述成 Android 实机验收。
