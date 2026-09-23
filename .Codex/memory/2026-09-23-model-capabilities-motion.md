# 思考面板动效与模型能力声明

- 恢复思考滑块与详细档位列表的双向淡入、滑动和尺寸过渡；仍在同一个受系统边距约束的 ModalBottomSheet 中。退出内容立即 inert，详情点选仍应用后返回滑块，无底部返回按钮。
- 本机 5580 服务实测响应提供 `modalities.input`、`capabilities.input` 和 `inputTypes`；原解析器只识别 architecture/root modalities 与 Anthropic 字段，导致发现结果缺失。本次补齐这些字段以及 Mistral `capabilities.vision`，兼容 data 包装及裸数组目录。
- 明确 false 优先于正向数组别名；输出模态、attachment、tool_call 等不会开启原生附件能力。缺少声明保持未知，手动覆盖优先；不改变存储格式、协议发送能力或思考档位推断。
- 模型子页区分手动覆盖、接口声明、未提供可识别声明。接口重新获取并在供应商页保存后更新已有配置。
- 供应商接口调查见 `.Codex/test-fixtures/model-capability-sources.md`；接口返回 token 限制不等于本轮已新增上下文自动配置，当前仍保留手动覆盖。
- 验证：250 项单测中 247 通过、3 跳过；assembleDebug/lintDebug 成功，lint 39 warnings/3 hints，git diff --check 通过。模拟器本地服务目录读取 24 个模型，claude-opus-5 子页显示接口来源且图片自动勾选；详情选择“高”后回到滑块显示 high。已保存并更新忽略目录中的配置备份。
- APK 已覆盖安装手机与模拟器，保留数据。SHA256：`8640F7219803C9940DC752C95A80939DE7AFE87A103525F7FF35CFA267134EE4`。动画主观流畅度、快速反向切换和减少动效仍待手机体验验收；未提交或推送。
