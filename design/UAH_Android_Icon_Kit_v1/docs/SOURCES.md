# Sources checked for this delivery

检索日期：2026-09-24。以下用于工程规格，不是图形形状的来源；UAH几何路径在本包中直接定义。

1. Android Developers — Adaptive icons
   https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
   前景/背景；108×108dp层；中心48–66dp主体；系统蒙版；API33主题化单色层。资源不预裁自适应背景。
2. Android Developers — Create app icons
   https://developer.android.com/studio/write/create-app-icons
   legacy/adaptive资源区分、mipmap目录、Image Asset Studio前景/背景/单色层、资源引用。
3. Android Developers — Support different pixel densities
   https://developer.android.com/training/multiscreen/screendensities
   mdpi到xxxhdpi的密度倍率；按48dp导出48/72/96/144/192px。
4. Android Developers — Google Play icon design specifications
   https://developer.android.com/distribute/google-play/resources/icon-design-specifications
   512×512、32-bitPNG、sRGB、≤1024KB、完整方形、不要预烘焙外圆角或外阴影。本包store目录只作备用，不表明应用准备上架。
5. Android Developers — application element
   https://developer.android.com/guide/topics/manifest/application-element#label
   android:label为可见名称，可由组件覆盖；并不是Application实现类名。
6. User repository, read via GitHub connector
   https://github.com/LingYzh/AgentApp/blob/master/app/src/main/res/values/strings.xml
   本次读取仍是构元 Actant；脚本/交接按最新本地文件合并，不替换其余资源。

模拟预览不保证每一OEM启动器采用相同mask；最后效果要在用户的Android设备上确认。
