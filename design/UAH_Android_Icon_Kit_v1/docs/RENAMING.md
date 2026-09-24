# 改名范围：Used AI Harness / UAH

用户指定正式名称 **Used AI Harness**，简称 **UAH**。不擅自改成 User AI Harness 或其他拼写。

## 只改可见品牌

当前读取到的仓库 `app/src/main/res/values/strings.xml` 是：

```xml
<string name="app_name">构元 Actant</string>
```

默认合并为：

```xml
<string name="app_name">Used AI Harness</string>
<string name="app_name_full" translatable="false">Used AI Harness</string>
<string name="app_name_short" translatable="false">UAH</string>
```

不要把片段文件整份覆盖到已有 strings.xml，也不要在另一份 XML 中重复定义同 qualifier 的 app_name。安装脚本 `--rename` 定向替换现有定义，并保留其他资源项。

应用 `android:label="@string/app_name"` 保持原样，因此默认系统名称是全称。紧凑顶栏、头像替代、抽屉小标题可使用 `app_name_short`；关于页显示全称。

若桌面上最终也决定用 UAH，单独审阅 MAIN/LAUNCHER Activity 或 activity-alias 的 label，将它设为 `@string/app_name_short`。这一步会影响对应组件的可见标题，因此不是本安装脚本的隐式操作。

检查 `values-zh*`、其他 values 限定目录，以及 src/debug、src/release 等 source set 中的品牌覆盖；脚本只修改 src/main 下已有的三个品牌字符串，在其他 source set 发现覆盖时打印警告。

## 不改这些内容

- applicationId `com.Ling.actant`：保留当前实际值，不因命名变化改安装身份。
- namespace / Kotlin 包：保留当前实际结构。
- `.AgentApp` Application 类、`.MainActivity` Activity 类：不是显示名称，不重命名。
- Provider、backup package format（如 actant.*）、工作区、存档目录、URI/authority 和序列化 key：不是纯品牌，不批量替换。
- 签名、数据库、版本策略、运行权限：本改名不涉及。
- 不默认清空应用数据解决启动器图标缓存问题。

## UI 中残留标志

新资源 `@drawable/ic_uah_mark` 用于应用内标志。已有 `ic_actant_mark` 或硬编码 A 字样不由资源安装脚本盲删；Codex 定位真实调用点后换成新资源，未引用的旧资源才允许清理。桌面 launcher 则已经使用新的自适应/legacy 资源。

## 来源

- 已读取：`https://github.com/LingYzh/AgentApp/blob/master/app/src/main/res/values/strings.xml`。
- Android label 定义：`https://developer.android.com/guide/topics/manifest/application-element#label`。
