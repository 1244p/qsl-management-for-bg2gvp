# QSL 卡片管理系统产品定义

更新时间：2026-07-16
代码核验范围：`插件工程根目录` 后端、控制台前端、RBAC 模板与 `tools/CardPrint` 在线打印桥接
Halo 官方资料核验日期：2026-07-16

## 1. 产品一句话定义

面向业余无线电场景的 QSO/SWL、实体 QSL 卡申请、线上换卡、线下换卡与收卡闭环管理产品，覆盖“记录、申请、审核、制卡、发卡、送达确认、收卡编号、查询、审计、统计、打印桥接”的可持久化业务流，并提供公开查询、实体卡申请、换卡与签收页面。

## 2. 菜单组织

零级菜单分组：`QSL管理`（左侧菜单分组名，不是可点击功能页面）。

入口路由：`/qsl`，当前代码会重定向到第一个可用模块。

当前一级菜单按 `ui/src/index.ts` 与 `ui/src/constants/menu-modules.ts` 生效：

1. 总览
2. 配置
3. 通联业务
4. 线上换卡业务
5. 线下换卡业务
6. 收卡业务
7. 审计
8. 数据

### 2.0.1 页面展示约定

1. 后台功能页不展示“前端权限定义”卡片与“页面状态”卡片。
2. 权限定义、依赖关系与路由映射以本文件和 `qsl-menu-role-templates.yaml` 为准。
3. 页面状态与实现进度通过输出性文档记录，不在页面内重复展示。
4. 支持详情展开的表格页面统一采用整行点击展开；行内按钮需阻止冒泡。
5. 同一业务组件可按 `sceneTypes/defaultSceneType/defaultCardType` 复用到不同菜单场景。
6. 按钮颜色按操作风险分级：普通查询、保存与单条状态推进使用中性按钮，编辑/修改类按钮统一使用白底中性样式；显式创建卡片、邮件外发、批量导入、批量创建、AI 结果应用、卡片重发、发卡异常、收卡编号迁移等中风险操作使用 warning 颜色；删除、拒绝、破坏性迁移等负向高风险操作使用 danger 颜色；本台设备中天线、功率、模式等局部候选项的“移除”按钮使用透明底无边框红字样式。

7. 除“配置 → 通信地址”页面自身的示例外，后台、公开页面和 CardPrint 中的呼号示例采用已持久化的 `StationProfile.spec.myCallSign（本台呼号）`；不存在或为空白时保留默认示例 `BI1KBU`。仅更新占位文字与示例说明，不作为表单输入，不修改实际业务呼号。通信地址保存后后台示例同步更新，公开页面重新加载时读取最新值。公开示例仅披露呼号，不能附带地址、姓名、电话、邮箱等完整通信信息。

8. 审核提示不得重复拼接“已”；手动邮件策略的待处理结果显示“等待手动发送”，只有持久化邮件状态为 `SKIPPED` 才表示该场景已跳过。线上换卡提交成功页分项展示申请编号、审核状态、下一步和本站通信地址；待审核时提示等待管理员审核、通过后手动建卡，已通过时提示等待建卡和寄送。`StationProfile.spec.myAddress（本站通信地址）` 未配置或为空白时明确提示联系管理员确认寄送信息，不阻断申请提交。

### 2.0.2 菜单与路由映射

| 一级菜单 | 二级菜单 | 控制台路由 | 权限节点 |
| --- | --- | --- | --- |
| 总览 | 总览看板 | `/qsl/overview/overview-dashboard` | `overview-dashboard` |
| 配置 | 系统配置 | `/qsl/settings/system-settings` | `system-settings` |
| 配置 | 通信地址 | `/qsl/settings/station-profile` | `station-profile` |
| 配置 | 本台设备 | `/qsl/settings/station-equipment` | `station-profile` |
| 配置 | 本台卡片 | `/qsl/settings/station-card` | `station-profile` |
| 通联业务 | 通联日志 | `/qsl/traffic-business/qso-record` | `qso-record` |
| 通联业务 | 创建卡片 | `/qsl/traffic-business/card-record` | `card-record` |
| 通联业务 | 实体卡申请审核 | `/qsl/traffic-business/qsl-card-request` | `qsl-card-request` |
| 通联业务 | 制卡签发 | `/qsl/traffic-business/card-issue` | `card-issue` |
| 通联业务 | 发信确认 | `/qsl/traffic-business/mail-send-confirm` | `mail-send-confirm` |
| 通联业务 | 送达确认 | `/qsl/traffic-business/mail-receive-confirm` | `mail-receive-confirm` |
| 线上换卡业务 | 换卡申请审核 | `/qsl/online-exchange-business/online-exchange-request-review` | `exchange-request-review` |
| 线上换卡业务 | 导入数据 | `/qsl/online-exchange-business/online-bh6syx-import` | `online-bh6syx-import` |
| 线上换卡业务 | 创建卡片 | `/qsl/online-exchange-business/online-card-record` | `card-record` |
| 线上换卡业务 | 制卡签发 | `/qsl/online-exchange-business/online-card-issue` | `card-issue` |
| 线上换卡业务 | 发信确认 | `/qsl/online-exchange-business/online-mail-send-confirm` | `mail-send-confirm` |
| 线上换卡业务 | 签收确认 | `/qsl/online-exchange-business/online-delivery-confirm` | `mail-receive-confirm` |
| 线下换卡业务 | 创建活动 | `/qsl/offline-exchange-business/offline-activity` | `exchange-request-review` |
| 线下换卡业务 | 创建卡片 | `/qsl/offline-exchange-business/offline-card-record` | `card-record` |
| 线下换卡业务 | 制卡签发 | `/qsl/offline-exchange-business/offline-card-issue` | `card-issue` |
| 线下换卡业务 | 送达确认 | `/qsl/offline-exchange-business/offline-delivery-confirm` | `mail-receive-confirm` |
| 收卡业务 | 通联收卡 | `/qsl/receive-business/receive-qso` | `mail-receive-confirm` |
| 收卡业务 | 线上换卡收卡 | `/qsl/receive-business/receive-online-eyeball` | `mail-receive-confirm` |
| 收卡业务 | 线下换卡收卡 | `/qsl/receive-business/receive-eyeball` | `mail-receive-confirm` |
| 审计 | 通联记录查询 | `/qsl/audit/qso-query` | `qso-query` |
| 审计 | 卡片记录查询 | `/qsl/audit/card-query` | `card-query` |
| 审计 | 收卡记录查询 | `/qsl/audit/receive-record-query` | `card-query` |
| 审计 | 统计报表 | `/qsl/audit/report-auditlog` | `report-auditlog` |
| 审计 | 审计日志 | `/qsl/audit/audit-log` | `report-auditlog` |
| 数据 | 卡片异动 | `/qsl/data/card-mutation` | `card-mutation` |
| 数据 | 地址管理 | `/qsl/data/address-management` | `address-bureau` |
| 数据 | 卡片局管理 | `/qsl/data/bureau-management` | `address-bureau` |
| 数据 | 设备库维护 | `/qsl/data/equipment-catalog` | `equipment-catalog` |
| 数据 | 导入导出 | `/qsl/data/import-export` | `import-export` |
| 数据 | 外部日志同步 | `/qsl/data/log-sync` | `log-sync` |

权限节点实际 UI 权限名统一为 `plugin:qsl-management:<权限节点>:view` 与 `plugin:qsl-management:<权限节点>:edit`。

## 3. 业务场景

### 3.1 场景主字段

当前持久化模型使用 `sceneType` 区分业务场景：

1. `QSO`：通联业务 QSO。
2. `SWL`：通联业务 SWL。
3. `ONLINE_EYEBALL`：线上换卡。
4. `EYEBALL`：线下换卡。

`QsoRecord.spec.sceneType`、`CardRecord.spec.sceneType`、`ExchangeRequest.spec.sceneType` 已按当前代码落地。

### 3.2 总览

总览看板通过控制台接口聚合统计，覆盖 QSO、卡片、待发、已发、签收、已收等指标。公开侧也提供匿名总览读取接口。

### 3.3 配置

系统参数当前包括：

1. `guestQueryPerMinute`：匿名公开接口每分钟限流阈值。
2. `onlineExchangeRequestPolicy`：线上换卡页面收到表单的处理策略，取值为 `DISABLED`、`MANUAL`、`AUTO_APPROVE`，分别对应关闭线上换卡、手动审核、自动通过。`onlineExchangeRequestCooldownMinutes` 控制同一呼号重复提交线上换卡申请的冷却时间，默认 5 分钟，填 0 表示关闭冷却，已通过或已拒绝的上一条申请也必须遵循冷却期。`onlineAutoApprovedRequestMailPolicy` 控制系统自动审批通过后是否向 `StationProfile.spec.myEmail` 发送本台通知，取值为 `AUTO_SKIP` 或 `AUTO_SEND`。`requiresExchangeReview` 作为历史兼容字段保留，未配置新字段时 `false` 映射为自动通过，其他情况映射为手动审核。
3. `qsoCardCreatedMailPolicy/qsoCardSentMailPolicy/qsoCardReceivedMailPolicy`、`onlineCardCreatedMailPolicy/onlineCardSentMailPolicy/onlineCardReceivedMailPolicy/onlineExchangeReviewedMailPolicy`：按通联、线上换卡拆分的邮件发送策略，取值为 `AUTO_SKIP`、`MANUAL`、`AUTO_SEND`，分别对应自动不发送、手动、自动发送。业务动作触发自动策略时，`AUTO_SKIP` 只写入对应邮件状态 `SKIPPED` 且不触发 Halo 通知，`AUTO_SEND` 才触发邮件发送，`MANUAL` 保持待人工处理。`offlineAutoNotifyOnCardReceived` 作为历史配置字段保留但不再使用，线下换卡收卡与送达确认默认不发送邮件。旧的 `autoNotifyOnCardCreated/autoNotifyOnCardSent/autoNotifyOnCardReceived/autoNotifyOnExchangeReviewed` 与场景布尔字段仅作为已有配置读取兜底。邮件通知策略支持向 `StationProfile.spec.myEmail` 发送测试邮件，测试数据中对方呼号等字段临时使用本台资料，卡片类型固定为 `EYEBALL`，卡片编号固定为 `C0001`。通知备注来源为：制卡/发卡使用卡片备注，收卡回执使用签收备注，线上换卡审核使用审核说明。
4. `cardRecordSequence`：卡片编号序列。
5. `receiveRecordSequence`：收卡编号序列。
6. AI 配置字段：`aiEnabled/aiProvider/aiBaseUrl/aiModel/aiSecretName/aiTemperature/aiTimeoutSeconds/aiMaxConcurrentRequests/aiMaxInputCharacters/aiOnlineImportParseEnabled/aiAddressCleanupEnabled/aiSystemPrompt/aiOnlineImportPrompt/aiAddressCleanupPrompt/aiCallbookAddressPrompt`。AI API Key 使用 Halo `Secret` 存储，系统参数只保存 Secret 引用和非敏感配置；AI 功能默认关闭。`aiMaxConcurrentRequests` 控制服务端同时调用第三方 AI 的请求数，默认 1，最大 10；超过并发上限的请求在服务端排队，429 限流错误会短延迟重试。配置页展示完整提示词模板，支持编辑并按项重置为内置默认值。自定义提示词必须保持系统指定的 JSON 返回结构：线上换卡导入顶层字段为 `rows`，地址整理顶层字段为 `items`，呼号地址获取返回 `callSign/recipientName/telephone/postalCode/address/email/confidence/message`，不得修改字段名、字段类型和状态枚举值；线上换卡导入提示词需保留 `{defaultCardVersion}`、`{mode}`、`{text}` 占位符，地址整理提示词需保留 `{rows}` 占位符，呼号地址获取提示词需保留 `{provider}`、`{callSign}`、`{features}` 占位符。呼号地址获取默认提示词必须明确各返回字段含义，要求同一资料同时出现中文地址和英文翻译地址时优先返回中文地址；QRZ.COM XML 优先使用 `addr1/addr2/state/country/zip/email` 等字段，但当 XML 只有 `state/country` 或英文行政区而资料页正文存在中文地址时，必须以资料页中文地址为准。线上换卡导入 AI 解析、地址管理 AI 地址整理和 QRZ 地址预览均必须在服务端鉴权后调用，AI 结果只作为预览或建议，不绕过现有导入和地址写入校验。
7. QRZ 地址获取配置字段：`qrzComEnabled/qrzComUsername/qrzComSecretName/qrzComXmlBaseUrl/qrzCnEnabled/qrzCnUsername/qrzCnSecretName/qrzCnLookupUrlTemplate/qrzTimeoutSeconds`。QRZ.COM 查询默认同时获取官方 XML 与 `qrz.com/db/{callSign}` 官方资料页；只要资料页 Biography/Detail 主体可用，就以资料页正文为主、XML 为辅交给 AI 解析，避免很多用户把真实通信地址写在 Biography 而非官方地址字段时被 XML 覆盖。QRZ.COM 资料页会抽取主体文本、Base64 动态 Biography、图片文件名线索；资料页不可用、抓取失败或只返回“需要 JavaScript/登录”等不可用提示时，才回退 XML 字段 `call/name_fmt/fname/name/addr1/addr2/state/country/zip/email` 解析；XML 只有国家、省份、州等宽泛行政区时仍会交给 AI 兜底，不直接作为有效地址。QRZ.CN 采用可配置查询 URL 模板，但服务端只允许访问 QRZ.COM / QRZ.CN 官方域名，禁止将配置项作为任意服务端请求入口。QRZ 凭据测试需先用临时配置完成远程校验，通过后才写入系统参数与 Secret。服务端优先用用户名和密码登录获取 Cookie，再用 Cookie 抓取查询页面，保留手工 Cookie 作为备用登录态。服务端识别 QRZ.CN `/call/` 查询页面时，先按配置 URL 原样请求，再按站内表单提交呼号，兼容 `q` 与 `callsign` 两类字段；由于 QRZ.CN 旧站存在概率性返回呼号搜索页的问题，每次请求结果都必须校验是否包含目标呼号，有效结果才允许交给 AI 解析；若页面明确提示呼号没有在数据库中，则立即返回资料不存在，不进入重试和 AI 解析；其他无效结果自动重试并在最终失败时明确报错，不返回空成功。抓取结果按响应编码解码，并在调用 AI 前抽取主体内容、呼号附近内容和图片文件名线索。QRZ.COM 密码、QRZ.CN 密码与 QRZ.CN Cookie 仅写入 Halo `Secret`，不写入 `SystemSetting.spec`，不进入导入导出数据包。

通信地址、本台设备、本台卡片分别落在 `StationProfile`、`StationEquipment`、`StationCard` 扩展资源中。本台卡片图案不直接写入 `StationCard`，必须通过后台图片选择入口打开 Halo 附件库弹窗，在弹窗内上传或选择图片后写入附件引用；`StationCard` 仅保存附件名称、访问地址与缩略图地址等引用信息；旧版 `imageUrl` base64 字段不再兼容，后台不再提供旧图片字段清理入口。配置-本台卡片为每个版本提供“仅支持实体 QSL 通联卡”开关，持久化到 `StationCard.status.active`：`true` 表示该版本仅允许实体 QSL 卡申请、普通通联和线下换卡使用，线上换卡页面仍展示但不可选择，线上换卡提交服务也必须拒绝；`false/null` 表示全部业务均可使用。新建版本默认关闭该限制。系统内置卡片版本“不发卡”不保存为 `StationCard`，不显示在配置-本台卡片中，库存视为无限，仅用于 `CardRecord.spec.cardVersion` 标记该卡片无需进入制卡、发信、送达确认和待发统计链路；收卡业务仍必须显示并允许关联“不发卡”记录，用于记录对方来卡事实。备份导入导出必须覆盖上述配置资源，`all` 导出需同时包含业务数据与配置数据。

### 3.4 通联业务

通联业务包含通联日志、创建卡片、实体卡申请审核、制卡签发、发信确认、送达确认。

1. 通联日志支持 `QSO/SWL` 场景切换，字段落在 `QsoRecord.spec`。通联日志清单支持按呼号和通联日期范围筛选，日期范围按 `QsoRecord.spec.date` 做闭区间匹配，并影响当前筛选导出范围。通联日志提供 `ADIF格式导入`、`ADIF格式导出`、`Cabrillo格式导入` 与 `Cabrillo格式导出` 功能页签；手工新增记录默认使用 `UTC+8`，启用 UTC 实时填充、ADIF 导入与 Cabrillo 导入保留 `UTC`。ADIF 导入先解析预览，再通过现有通联记录保存能力创建 `QsoRecord`，`CALL/QSO_DATE/TIME_ON/MODE/FREQ/BAND/RST/QTH/COMMENT/MY_RIG/MY_ANTENNA/TX_PWR/OPERATOR/MY_SIG/MY_SIG_INFO/SWL` 等可落入现有模型的字段直接写入；当 `FREQ` 为空且 `BAND` 有值时，将频率写为 `{BAND} BAND`；当 `QTH` 缺失且 `GRIDSQUARE` 有值时，将 `GRIDSQUARE` 兼容写入对方位置；当 `MY_SIG=POTA` 时，将 `MY_SIG_INFO` 视为本台 QTH，若本台 QTH 仍为空则兼容使用 `MY_GRIDSQUARE`；`TX_PWR` 作为我台发射功率写入，缺失时才回落到设备默认功率。其余 ADIF 字段按 `字段名称：字段内容；` 追加到备注，避免导入时丢失标准内容之外的数据。ADIF 导入预览默认跳过重复记录：本站导出的 `APP_QSLMS_RECORD_ID` 已存在时按本站记录编号判重，外部 ADIF 或缺少本站记录编号时按 `sceneType/callSign/date/time/timezone/myRigMode/freq` 组成业务键判重；文件内部重复和与现有通联日志重复均不写入。ADIF 导入页提供“导入可用记录”和“覆盖导入”：前者只创建非重复记录，后者需二次确认，仅对已匹配到现有资源名或业务判重键的重复记录执行批量更新；同一业务判重键下存在多条现有记录时全部覆盖更新，不处理文件内部重复和无效记录。ADIF 导出范围优先使用已勾选记录，未勾选时导出当前筛选结果；导出页分别提供符合 ADIF 3.1.7 的 `ADI` 与 `ADX` 文件：`ADI` Header 必须以普通 ASCII 说明文本开头，所有字段只允许可打印 ASCII，字段值含中文或其他非 ASCII 字符时整字段不导出，多行值压平为单行；`ADX` 使用 UTF-8 XML，并通过 `QTH_INTL/COMMENT_INTL/MY_RIG_INTL/MY_ANTENNA_INTL/MY_SIG_INTL/MY_SIG_INFO_INTL` 保留国际化文本。两种格式均按记录时区换算为 UTC 后写入 `QSO_DATE` 与 `TIME_ON`，并兼容 `UTC+8/UTC+08:00/GMT+8/北京时间/Asia/Shanghai` 等等价时区写法；`USB/LSB` 导出为 `MODE=SSB` 并写入对应 `SUBMODE`；`FREQ` 仅在存在 ADIF `Number` 类型的 MHz 数值时导出，`{BAND} BAND` 占位按 ADIF 3.1.7 波段枚举导出为 `BAND` 字段，不写入非数值 `FREQ`，备注中由导入保留的 `MY_STATE/STATE/GRIDSQUARE/MY_GRIDSQUARE/SIG/SIG_INFO/QSO_DATE_OFF/TIME_OFF` 等标准 ADIF 字段会恢复为独立字段；`TX_PWR` 导出时按 ADIF `Number` 类型规范化为瓦特数值。导出页支持 `MY_SIG` 下拉与 `MY_SIG_INFO` 输入，`MY_SIG` 为空时表示“不添加”，且 `OPERATOR` 仅导出呼号，不再回退为姓名。Cabrillo 导入先解析 `START-OF-LOG/CALLSIGN/CONTEST/OPERATORS/CATEGORY-*` 等头字段与 `QSO:` 行，再预览确认后写入 `QsoRecord`；频率、模式、日期、时间、本台呼号、对方呼号和信号报告直接落入模型，比赛交换信息、未映射头字段和 QSO 行尾扩展按 `字段名称：字段内容；` 追加到备注。Cabrillo 导出使用 Cabrillo 3.0 文本格式，导出日期和时间按记录时区换算为 UTC，支持配置 `CONTEST/OPERATORS/CATEGORY-OPERATOR/CATEGORY-BAND/CATEGORY-MODE/CATEGORY-POWER`，范围同样优先使用已勾选记录，未勾选时导出当前筛选结果。
2. 创建卡片必须关联 QSO/SWL 记录，候选列表排除已写入 `CardRecord.spec.qsoRecordName` 的记录；若选择“不创建卡片”，写入不占用 `C{序号}` 的占位 `CardRecord`，该记录后续不再出现在候选列表中。通联业务创建卡片页下方展示“待创建记录”，数据来源为尚未创建卡片且未标记“不创建卡片”的 QSO/SWL 记录；页面默认不选中卡片版本，操作栏提供“创建卡片”按钮，一键生成正式 `CardRecord` 时必须先人工选择卡片版本，用户可手动选择系统内置“不发卡”，业务备注为空。通联业务创建卡片默认 `CardRecord.spec.cardRemarks` 为“通联愉快，期待空中常见。\nNice QSO，Hope to catch you on the air often.”。
3. 通联日志清单支持对单条 QSO 一键创建卡片，卡片版本必须由用户在通联业务创建卡片页手动选择，写入空业务备注与通联业务默认卡片备注；已正式创建卡片或已标记“不创建卡片”的通联记录不允许再次一键创建。
4. 制卡签发只处理正式卡片编号为 `C{序号}` 的 `CardRecord`，不显示“不创建卡片”的无编号占位记录；确认制卡更新 `CardRecord.spec.cardIssued/cardIssuedAt`，信封打印或打包状态使用 `envelopePrinted`。“确认打包”保留当前卡片的 `addressEntryName（绑定地址编号）` 与 `mailTargetEmail（邮件目标邮箱）`，包括空值和卡片局地址，不使用页面候选地址覆盖，也不自动推测默认地址。选定地址仅作为候选，页面显示本次目标卡片编号，点击“确认绑定地址”才持久化；目标限定为查询呼号精确匹配且尚未打包、发出或签收的卡片，历史卡片修正通过卡片异动处理。显式绑定保留已有流转与邮件处理事实，审计记录原地址、新地址和目标卡片列表。修改查询呼号或切换卡片清空候选，写入期间禁止并发提交与切换；批次失败停止后续写入并显示已保存数量。制卡签发完成打包后按当前制卡邮件策略自动处理邮件状态：自动不发送写入 `createdMailStatus=SKIPPED`，自动发送调用通知接口，手动则保留按钮。
5. 发信确认只处理正式卡片编号为 `C{序号}` 的 `CardRecord`，调用控制台接口更新 `cardSent/sentAt`，并可触发发卡邮件通知；通联业务只要 `cardSent=true`，系统会联动补齐 `cardIssued/cardIssuedAt/envelopePrinted`。
6. 送达确认只处理正式卡片编号为 `C{序号}` 的 `CardRecord`；2.0.0 起调用控制台接口创建 `ReceiveRecord` 并尝试关联已有发卡记录，未匹配时保留未匹配收卡事实，不再自动补建发卡记录。同一发卡卡片 ID 可连续关联多条收卡事实，确认收卡只新增 `ReceiveRecord` 并保持本轮收卡打开，不触发收卡邮件自动策略；只有人工点击“结束收卡”进入回执处理状态后才关闭该发卡记录的继续收卡入口。
7. 实体 QSL 卡申请使用独立 `QslCardRequest（实体卡申请）`，不复用 `ExchangeRequest（线上换卡申请）`。匿名用户通过 `/qsl_card` 输入呼号查询 QSO；公开接口实时聚合申请与关联 `CardRecord（卡片记录）`，返回 `AVAILABLE/REJECTED_REAPPLY/REVIEW_PENDING/CARD_CREATING/CARD_CREATION_FAILED/CARD_PENDING_ISSUE/CARD_ISSUED/CARD_PACKED/CARD_SENT/CARD_SIGNED` 状态码及中英文字，分别展示可申请、申请未通过可重新申请、审核中、审核通过正在建卡、审核通过建卡异常、审核通过待制卡、已制卡待打包、已打包待寄出、已发卡和已签收。后台直接创建或旧数据无法关联实体卡申请时，待制卡状态显示“已有卡片，待制卡”，不得错误宣称审核通过；“已收卡片”属于本站收卡事实，不进入申请寄送进度。除可申请和可重新申请外均显示但禁选；`unselectableReason（不可选择原因）` 保留为兼容字段，不新增重复持久化状态。每条已选 QSO 单独选择卡片版本。QSO 表格最右侧提供“申请此记录的卡片”按钮，可申请记录点击后只激活同一行左侧复选框并展开卡片版本候选区，不直接提交申请；勾选后按钮显示“已选择此记录”并禁用，取消复选框后恢复，不可申请记录的按钮保持禁用。勾选 QSO 后在该记录下方展开可视化卡片版本候选区，每个候选项左侧使用固定正方形图片框并以 `object-fit: contain` 完整显示横版或竖版卡面，右侧显示卡片版本名称和剩余可用数量；取消勾选时收起候选区并清除该 QSO 的版本选择。页面全部面向用户的标题、说明、表头、字段标签、按钮、状态、校验、加载、错误和结果文案使用中英双语，不展示内部字段名，并按中文在上、英文在下布局；仅原生下拉选项允许受浏览器限制采用“中文 / English”单行形式。个人地址每次重新填写，公开页面不得读取历史个人地址；其中邮政编码、通信地址和通知电子邮箱必填，收件人姓名、联系电话和备注选填，前端与服务端执行一致校验。提交后服务端按完整联系方式精确复用或创建 `AddressBookEntry（地址簿条目）`，通知邮箱同时保存在申请中。卡片局仅允许选择公开的现有 `BureauEntry（卡片局条目）`，每个候选按“去向国｜卡片局名称｜邮编｜地址”显示，未配置去向国时明确显示未设置；页面每次切换到卡片局收件方式时自动刷新公开清单，不提供手动刷新按钮，请求失败时在表单底部显示错误，已成功加载的清单不因后续刷新失败而清空。新增卡片局通过页面返回的本台邮箱由用户主动邮件联系。审核通过后立即为全部申请项目创建一对一 `CardRecord（卡片记录）`，部分失败保留逐条结果并支持幂等重试；插件启动时修复孤立 QSO 占用。

### 3.5 线上换卡业务

线上换卡业务包含换卡申请审核、导入数据、创建卡片、制卡签发、发信确认、签收确认。

1. 前台匿名提交线上换卡申请写入 `ExchangeRequest`，`sceneType=ONLINE_EYEBALL`；公开表单先填写呼号，再从本台卡片版本列表选择最多两张卡片，页面按 `StationCard.spec.sortOrder` 展示卡片预览缩略图、版本号、版本总量和库存余量，公开卡片版本列表使用短缓存并在 Extension 查询短暂失败时回退到上一次成功结果；`StationCard.status.active=true` 的版本仍展示，但标记“仅支持实体 QSL 通联卡”并禁选，服务端同时拒绝绕过前端提交该版本；随后选择“个人地址”或“卡片局地址”，地址类型默认不选。
2. 个人地址模式填写姓名、电话、邮编、通信地址，电子邮箱可选；卡片局地址模式从 `BureauEntry` 候选选择已有卡片局，或填写新的卡片局名称、邮编、地址。新的卡片局仅随本次申请保存，不由匿名接口写入卡片局管理主数据。
3. 同一呼号存在 `ONLINE_EYEBALL` 待审核换卡申请时，公开提交接口拒绝再次提交；上一条同呼号申请即使已经审批通过或拒绝，也必须等待 `SystemSetting.spec.onlineExchangeRequestCooldownMinutes` 指定的冷却期结束后才允许再次提交，默认冷却时间为 5 分钟。当 `SystemSetting.spec.onlineExchangeRequestPolicy=AUTO_APPROVE` 时，公开提交成功后立即执行系统自动审批，审核说明固定为“系统自动审批通过”；自动审批只更新申请状态，不自动创建卡片。若 `onlineAutoApprovedRequestMailPolicy=AUTO_SEND`，自动审批通过后向本台通信地址中的电子邮件发送本台通知。当策略为 `DISABLED` 时，公开提交接口拒绝接收新申请。
4. 线上换卡提交校验通过并成功写入 `ExchangeRequest` 后，提交接口返回本站通信地址，前台在成功提示中展示寄送信息。
5. 后台审核通过后不自动创建卡片；审核页操作栏提供“创建卡片”按钮，点击后显式创建 `ONLINE_EYEBALL` 场景卡片，并把 `ExchangeRequest.spec.cardVersion` 写入新建 `CardRecord.spec.cardVersion`；同时根据申请中的个人地址或卡片局地址复用/创建地址资源，并写入 `CardRecord.spec.addressEntryName`。审核通过只代表申请通过与我方待发卡，不代表我方已收到对方卡片，新建卡片的 `cardReceived=false`。线上换卡审核页建卡、导入建卡和手工创建卡片的默认 `CardRecord.spec.cardRemarks` 为“期待与您空中相遇。\nLooking forward to meeting you on the air.”。
6. 换卡申请审核在同意或拒绝后显示“发送邮件通知”“不发邮件”“创建卡片”和“修改”操作；操作区提供“审核说明”按钮，审核前后均可编辑说明。“不发邮件”会将 `ExchangeRequest.status.reviewMailStatus` 写为 `SKIPPED`，状态为 `SENT/SKIPPED` 时邮件操作禁用。“创建卡片”仅对已通过且尚未创建卡片的申请可用，由服务端校验申请状态、场景和呼号并防止重复创建；创建或复用卡片后同步写入 `ExchangeRequest.status.cardCreated/cardCreatedAt/cardCreatedBy/createdCardRecordName`。编辑页提供“标记已发卡”按钮，用于历史数据纠偏，只写入已创建卡片状态，不创建新的 `CardRecord`。前端优先读取申请状态字段，兼容通过已创建卡片业务备注中的申请编号反向识别建卡状态，建卡或手动标记后移除创建按钮并在状态列显示“已创建卡片”标记。邮件发送状态也在状态列展示，操作列仅保留可点击动作。手动同意时审核说明为空则保持为空，已有说明时以人工填写内容为准；手动拒绝时说明为空则默认“审批拒绝”。当 `SystemSetting.spec.onlineExchangeReviewedMailPolicy=AUTO_SEND` 时，审核同意或拒绝后自动发送审核结果邮件。邮件通知面向 `ExchangeRequest.spec.email`，发送结果写入 `ExchangeRequest.status.reviewMailStatus/reviewMailSentAt/reviewMailLastError/reviewMailTargetEmail`；状态为 `SENT` 时，服务端跳过重复发送，后台按钮禁用。无邮箱时按跳过处理；修改操作可调整申请信息与审核状态，并可删除本条换卡申请记录。
7. 导入数据菜单包含单条导入、批量导入、BH6SYX卡片广场导入三个 tab。单条导入和批量导入支持粘贴 `收件人/电话/邮箱/地址/邮编/卡片版本` 文本，`呼号` 未单独填写时使用 `收件人` 作为对方呼号；多条导入按重复出现的 `收件人` 或 `呼号` 分段。未填写卡片版本时，默认使用按本台卡片排序中最早且仍有余量的一版卡片。若 AI 功能与线上换卡导入 AI 解析开关启用，解析预览优先调用服务端 AI 解析接口；AI 不可用、失败或返回空结果时回退本地解析。BH6SYX卡片广场导入支持上传 `.xls/.xlsx` 文件，自动识别表头行，按“状态”仅保留 `对方已寄出，待我签收` 与 `待双方寄出`，字段缺失时留空。页面可批量设置默认卡片版本，也可逐行选择卡片版本；默认卡片版本下拉按 `station-cards.availableInventory - card-records` 已用数量显示剩余量，不显示库存量。若源表“对方地址”开头重复包含“对方QTH”，导入预览与提交会去掉重复 QTH 前缀，仅保留通信地址。导入提交到服务端后直接创建 `ONLINE_EYEBALL` 场景 `CardRecord`，并按收件人、电话、地址、邮编和邮箱复用或创建 `AddressBookEntry`。该导入不创建 `ExchangeRequest`；BH6SYX 表格中的“交换ID”与“对方备注”仅用于源表识别或导入清单预览，不写入数据，后续流程统一围绕本站卡片编号处理。
8. 后续流程复用创建卡片、制卡签发、发信确认组件；签收确认使用独立控制台页面。线上换卡“签收确认”用于记录对方是否签收我方寄出的卡片；签收事实落点为 `CardRecord.spec.receiptConfirmed`，不作为收卡编号或我方收到对方卡片的主数据。

### 3.6 线下换卡业务

线下换卡业务包含创建活动、创建卡片、制卡签发、送达确认。

1. 创建活动写入 `OfflineActivity`。
2. 线下创建卡片可关联 `offlineActivityName`，允许先生成待填写呼号的活动卡片。线下换卡卡片不绑定通信地址或卡片局地址，不写入 `CardRecord.spec.addressEntryName`，打印与后台清单不展示收件地址字段。
3. 线下送达确认允许补录呼号并触发对应状态变化；前台线下换卡确认提交成功后同步写入 `cardSent=true`，使线下换卡业务送达确认中的发卡状态显示为“是”。线下换卡业务收到对方“已签收”动作时，如果尚未发卡，会联动补齐 `cardSent/sentAt`。线下换卡活动内置“日常线下换卡”，不保存为 `OfflineActivity`，不约束活动地点、活动日期和活动时间，创建卡片时使用实时日期时间；后台活动清单、线下卡片创建、收卡活动选择和公开线下换卡确认活动列表均展示该内置活动。2.0.0 起，线下换卡收卡基本功能必须选择收卡归属活动，服务端按活动、呼号和卡片类型优先绑定已有线下发卡记录；若只找到同活动空呼号卡片，则补写呼号并关联该卡；找不到时只创建 `ReceiveRecord`，不再为收卡新建 `CardRecord` 和发卡编号。
4. 线下换卡公开页面不在 HTML 中直接暴露本站通信地址、收件人、电话或邮箱；匿名提交校验通过并成功写入后，提交接口才返回本站通信地址用于寄送提示。前台线下换卡确认备注写入 `CardRecord.spec.publicReceiptRemarks`，后台线下换卡制卡签发、创建卡片历史详情、送达确认清单等备注查看位置必须展示该字段。

### 3.7 收卡业务

收卡业务包含通联收卡、线上换卡收卡、线下换卡收卡。

1. 三类收卡菜单复用收卡确认组件，以 `sceneType` 限定业务范围。
2. 2.0.0 起，收卡编号来源统一为 `ReceiveRecord.metadata.name`；收卡业务页面、查询、统计与导入导出均以 `ReceiveRecord` 作为收卡编号主数据来源。2.0.7 起 `CardRecord.spec.receivedRecordCodes` 仅作为待 2.1.x 移除的历史字段保留，普通业务、查询、统计、导入导出和前端展示均不再读写该字段。2.0.13 起，普通业务、公开查询、审计页面与统计中的“已收”展示、筛选和排序以 `CardRecord.status.flowStatus=已收卡片` 为依据，`CardRecord.spec.cardReceived` 仅作为后台状态维护字段，不作为展示判断；`ReceiveRecord.spec.outboundCardNames` 是状态机输入事实，只要卡片存在收卡记录关联，即必须刷新为“已收卡片”。导入会忽略外部 `flowStatus` 并按卡片状态字段与 `ReceiveRecord.spec.outboundCardNames` 重建状态机，导出输出后端派生后的 `flowStatus`。
3. 收卡业务提供基本确认、已收卡片、批量编辑与单条编辑能力；基本功能的“确认收信”和清单内“确认收卡”必须使用页面上方填写的收卡日期，未填写日期时前端弹窗提示且服务端拒绝提交，不再默认使用当前日期；该日期在本次浏览会话内按收卡子菜单记忆，提交后不清空。2.0.0 起，收卡确认先创建 `ReceiveRecord`，再把可匹配的发卡记录写入 `ReceiveRecord.spec.outboundCardNames`；无法匹配时 `matchStatus=未匹配`，不创建新的 `CardRecord`。收卡业务基本清单必须保留已结束收卡的发卡记录可见，但隐藏“继续收卡/结束收卡”等继续收卡入口，仅保留查看、回执发送状态或批量编辑等适用能力。收卡业务三个子菜单的“已收卡片”页签在操作列提供“关联卡片”，用于把未关联发卡编号的收卡记录人工关联到同呼号、同卡片类型、同业务场景的正式 `CardRecord`；线下换卡还需同活动，提交前必须弹窗二次确认，关联后以 `ReceiveRecord.spec.outboundCardNames` 作为状态机输入刷新目标卡片为已收卡片。线上换卡收卡的“已收卡片”页签对 `matchStatus=未匹配` 且未关联发卡记录的收卡记录提供“创建卡片”操作，点击后服务端创建线上换卡发卡记录并写回 `ReceiveRecord.spec.outboundCardNames/matchStatus`。收卡业务的发卡记录候选包含系统内置卡片版本“不发卡”的正式卡片，用于把对方来卡关联到已明确无需我方发卡的通联记录。收卡业务的“迁移收卡编号”入口仅保留在收卡业务页面的“批量编辑”页签中，用于将该卡片已关联的 `ReceiveRecord` 迁移到同呼号、同卡片类型、同业务场景的目标发卡记录；提交前必须弹窗二次确认，迁移后刷新源卡片与目标卡片的状态机。
4. 线上换卡业务收到对方“已签收”动作时，如果未制卡、未打包或未发卡，会联动补齐 `cardIssued/cardIssuedAt/envelopePrinted/cardSent/sentAt`；通联业务和线上换卡业务只要 `cardSent=true`，也会联动补齐制卡和打包状态。状态机被批量编辑为否或空时，对应时间、邮件状态、邮件时间和邮件错误同步置空。

### 3.8 审计与数据

审计包含通联记录查询、卡片记录查询、收卡记录查询、统计报表、审计日志。统计报表除汇总指标外展示月度收发卡片数量折线图。写操作通过 `QslAuditLog` 追加记录。审计中的卡片记录查询和收卡记录查询按业务场景 tab 展示，至少区分通联业务、线上换卡业务、线下换卡业务；收卡记录查询的数据来源为 `ReceiveRecord`，卡片记录查询的数据来源为 `CardRecord`。审计菜单不做业务状态迁移：不提供人工关联、创建、迁移等业务写操作。2.3.48 起，收卡记录查询的操作列提供“删除”，用于清理录错的收卡记录：提交前必须二次确认，删除成功后追加“删除收卡记录”审计日志（资源类型 `receive-record`）；删除只移除 `ReceiveRecord` 本身，不回滚已关联卡片的“已收卡片”状态，需要回滚时改走卡片记录或卡片异动。标记“不创建卡片”时使用后台占位 `CardRecord`，资源名为 `NC0001` 递增形式；该编号不是正式卡片 ID，前台查询与业务页面保持空白展示。
总览看板与统计报表中的卡片类统计项（卡片总数、眼球总数、待发卡片、已发卡片、发卡签收）只统计资源名为 `C{序号}` 且存在呼号的正式 `CardRecord` 记录，避免将线下换卡空呼号占位卡、`NC{序号}` 不创建卡片占位记录或其他后台记录纳入统计。待发卡片不再按裸 `cardSent=false` 统计，而是排除线上换卡签收链路记录、已签收、已收卡、`cardVersion=不发卡` 以及已经被 `ReceiveRecord.spec.outboundCardNames` 闭环关联的记录，仅保留仍需发卡处理的非线上换卡积压。已发卡片统计明确已发卡、已签收、有发卡时间的记录；`cardVersion=不发卡` 不纳入已发卡片和发卡签收统计。线上换卡场景中，若 `cardIssued=true`、`envelopePrinted=true` 且 `createdMailStatus=SENT`，也视为已完成发出链路，避免历史记录未回写 `cardSent` 导致统计偏小。2.0.0 起，已收卡片优先按 `ReceiveRecord.spec.callSign` 去重统计；呼号为空时按 `ReceiveRecord.spec.outboundCardNames` 中的卡片 ID 去重统计；当不存在新收卡记录时，才回退使用 `CardRecord.status.flowStatus=已收卡片` 并按呼号去重的口径。

数据包含卡片异动、地址管理、卡片局管理、设备库维护、导入导出。卡片异动提供卡片记录修正能力，权限节点为 `card-mutation`；其中“卡片重发”用于接收方长期未收到时清理制卡、打包、发卡及相关邮件状态并保留收卡事实，页面上方按卡片ID或呼号筛选并提交重发，下方只展示已处于重发待处理状态的卡片；“发卡异常”用于邮局退回等异常场景，将卡片类型标记为 `原类型（ERROR）` 并在独立清单显示，正常清单不再显示异常卡片；发卡异常清单中的“标记重发”先解除异常状态，再调用卡片重发动作完成状态清理。地址管理包含“AI地址整理”“从QRZ.COM获取地址”“从QRZ.CN获取地址”页签，并在 `AddressBookEntry.spec` 中保存呼号、姓名、电话、邮编、去向国、收件地址、邮箱与地址备注；卡片局管理在 `BureauEntry.spec` 中保存卡片局名称、电话、邮编、去向国、地址与备注；其中去向国均为非必填字段，可用于国际信封打印和导入导出备份。待绑定地址呼号清单只统计仍需发卡且未绑定地址的卡片；若某呼号对应的未绑定正式卡片全部为系统内置“不发卡”，则无需绑定地址，也不显示在待绑定清单中，自动绑定地址时同样跳过“不发卡”卡片。卡片异动中把卡片版本从“不发卡”改回实际卡片版本时，若该卡片尚未绑定地址且同呼号地址簿存在可用地址，系统自动回填第一条同呼号地址。AI地址整理支持勾选或全选地址记录，调用服务端 AI 地址整理生成预览，并在二次确认后仅更新 `AddressBookEntry.spec.address`；QRZ 获取地址按呼号查询第三方资料并由 AI 解析为预览，QRZ.CN 会先进行页面主体抽取和图片文件名线索抽取，必须人工确认填入基础表单后再点击保存，不自动写库。导入导出当前由服务端执行预检、导入、导出任务，并持久化 `ImportExportJob`；备份范围包含线下换卡活动清单，以及系统参数、通知策略、通信地址、本台设备、本台卡片等配置数据，但不包含 AI API Key、QRZ.COM 密码、QRZ.CN Cookie 等 Secret 明文。导入导出前端识别 ZIP 内 CSV 时必须优先按 `id#数据集` 与文件名精确匹配，避免 `station-card.csv` 被宽泛 `card` 关键词误判为 `card-record`；服务端导入 `card-record` 时遇到 `qsl-station-card-*` 或 `station-card-*` 资源名必须跳过，不创建 `CardRecord`，服务端导出 `card-record` 时也必须过滤此类历史脏记录，避免污染继续传播。`QslMigrationState` 作为插件数据版本与迁移状态单例，记录当前运行插件版本、最后一次数据状态确认成功的插件版本、数据模型版本、已执行迁移清单、待迁移清单和最近迁移结果；插件启动时初始化或刷新该状态，首次安装只建立基线，不执行历史迁移。未来跨大版本迁移必须按迁移项 ID 幂等执行，大版本或破坏性迁移未完成时，后续小版本迁移必须保持阻塞。2.0.0 起新增 `ReceiveRecord` 和 `OfflineExchangeCard`，用于收卡事实与线下换卡活动卡解耦；旧导出包导入前必须通过 `tools/convert_legacy_export_to_qsl2.py` 聚合迁移收卡关联与线下活动卡关系。导入导出页面提供“旧版本迁移”能力，用于处理 Halo 卸载插件后 Extension 数据仍保留、无法通过重装后重新导入完成迁移的场景；该能力直接读取当前 Halo 存储中的旧版 `CardRecord`，原地拆分为 `ReceiveRecord` 与 `OfflineExchangeCard`，清理旧版自动收卡临时卡片和本台卡片版本占位记录，并修正系统序列号。执行前必须先完成全量导出备份，执行时必须输入确认文字“确认迁移旧版本数据”。重复执行时跳过已存在的同名收卡记录和线下换卡卡片。

### 3.9 外部日志同步

外部日志同步用于按本台呼号自动从外部日志服务拉取通联记录并写入通联记录列表，菜单入口在“数据 → 外部日志同步”，权限节点为 `log-sync`。数据源当前支持 ARRL LoTW（ARRL 未提供编程接口，采用带查询参数的 ADIF 下载地址抓取）与 QRZ Logbook（官方 Logbook API 的 `FETCH` 动作，需要 Key 与订阅）。功能命名不包含 LoTW 商标，页面标题统一写“外部日志同步”。

同步配置持久化为 `LogSyncProfile`，字段包括数据源类型、本台呼号、账号名、凭据 Secret 名、是否启用、是否自动同步、同步间隔分钟、默认场景类型、重复记录处理策略与单次最多拉取条数；状态字段记录最近同步时间、最近游标、最近错误与连续失败次数。每次同步生成一条 `LogSyncJob`，记录触发方式、游标区间、拉取/新建/更新/跳过/失败条数与失败明细。写入通联记录时不新增 `QsoRecord.spec` 字段，来源通过 `QsoRecord.status.source` 与 `status.autoCreated` 标记。

账号与密码必须由页面表单填写后写入 Halo `Secret`，不写入 `LogSyncProfile.spec`、不写入导出包、不回显、不写进错误信息与审计日志；错误信息只允许包含异常类型与安全描述，禁止拼接含密码的请求地址；但必须带上排障证据——上游返回的状态码、内容类型、字节数与正文开头片段（LoTW 认证失败时返回的是 200 的登录页 HTML，不能把它一律说成「密码错误」）。凭据要求按数据源区分：ARRL LoTW 的“账号”是 LoTW 用户名（多数人是主呼号但不总是），密码是登录 `lotw.arrl.org` 的密码，可能与签署 ADIF 用的证书密码不同，两者都必填（口径参照 QRZ 官方 LoTW 导入说明：QRZ 也只记住用户名，密码每次交给 LoTW）；QRZ Logbook 只使用一长串 Key 认证，账号填 Key、密码可以留空，保存与同步都不强制密码。密码已存在时再次保存凭据可留空表示不修改，首次保存必须填写（QRZ 除外）。页面在凭据不完整时给出醒目提示；「测试连接」与「立即同步」始终可点击，若「账号凭据」里刚填了密码或 Key 则先自动保存成 Secret 再执行，若既没有待保存内容也没有已保存凭据则直接提示去填写。禁用按钮的做法不可取：禁用条件只能读到已保存状态，用户填完输入框仍然点不动，而灰按钮没有任何反馈，等于把用户卡死在原地。「测试连接」的返回必须带记录样例：显示上游返回的总条数与最多 20 条形如 `BH1ABC · 20261030 1234 · SSB · 14.074` 的样例行，让人一眼看出上游到底返回了谁的记录、时间范围对不对；只有总数没有样例时，用户无法判断“只拉到一条”是自己日志少还是程序有 bug。样例只用于诊断，不落库。游标必须可重置：游标一旦写成错误起点（例如早期版本只拉到最新一条却把那条当成「已同步到此」），后续增量永远补不回历史记录，用户没有任何自救手段。因此页面提供「重置游标」，清空游标与最近错误、把连续失败计数归零，下一次同步按全量重新拉取；它不删除任何已同步的通联记录，同步进行中时拒绝执行。LoTW 的下载地址必须显式带 `qso_qsorxsince`：不带这个参数时 LoTW 只回最近一条记录（2026-10-31 实测只拿到 1 条），首次同步一律用 `1900-01-01` 作起点，之后用报表头部 `APP_LoTW_LASTQSORX` 作增量起点。LoTW 的报表是一次性整份下载、没有分页参数，因此同步循环对 LoTW 只取一次；QRZ Logbook 用 `AFTERLOGID` 游标分页，每页 250 条。重复记录按 `sceneType|呼号|日期|时间|时区|模式|频率` 判定，与前端 ADIF 导入使用同一规则；“保留已有”跳过重复记录，“覆盖已有”只覆盖通联事实字段，不覆盖备注与人工维护的设备、天线信息。单条记录缺少呼号、日期或时间非法时不阻断整批，计入失败明细并最多保留 200 条错误行。

两个数据源的关键参数按各自官方文档实现：LoTW 的下载地址必须用 `https`，必须带 `login`、`password`、`qso_query=1`，且取通联记录时必须 `qso_qsl=no`（否则只返回 QSL 记录，且 `qso_qsorxsince` 会被忽略）；本台站址字段（如本台网格）只有带 `qso_mydetail=yes` 才返回；增量游标取响应头部字段 `APP_LoTW_LASTQSORX`（取不到时回退扫描最大 `QSO_DATE`）；模式字段在无法用 ADIF 标准值表达时会缺失，此时回退读取 `APP_LOTW_MODE`。QRZ Logbook 使用官方 `FETCH` 动作，首次请求携带 `ALL`，增量请求必须去掉 `ALL` 并改用 `AFTERLOGID`；`AFTERLOGID` 的口径是“大于等于”，因此游标必须取上一批最大 `APP_QRZLOG_LOGID` 加一，否则每轮会重复拉取最后一条。两项服务都要求可识别的 `User-Agent`，插件使用固定产品标识。

同步作业不允许永久停在「进行中」：拉取不能挂在请求上——LoTW 整份报表可能耗时数十秒，页面刷新或关闭会让请求被取消，链一取消作业就永远停在 `RUNNING`，页面一直显示进行中，而并发保护又会把「重置游标」「删除配置」一并拒掉。因此「立即同步」只等到作业创建成功就返回，实际拉取在服务端后台执行，页面用作业表轮询结果。另外三条兜底必须同时存在：①作业收尾写入遇到乐观锁冲突时重新取最新作业再写，避免收尾本身失败反而制造卡死；②页面在存在进行中作业时露出「强制停止」，把作业收尾为失败并解除占用，这是用户唯一的自救入口，不必等十分钟的陈旧判定或重启插件；③插件启动时把所有遗留的 `RUNNING` 作业收尾为失败——进程重启后不可能还有作业在跑，持久化状态却不会自己变。

定时同步由插件内 Spring 调度驱动：启动类开启调度能力后，一个每分钟触发的调度器检查哪些配置已到期，只有同时满足“已启用”“已开启自动同步”“距上次同步超过同步间隔（下限 60 分钟）”三个条件才执行，首次配置尚未同步过的立即执行一次。调度器不自行注册固定周期任务，因此修改同步间隔无需重启插件。同一配置存在未结束的同步作业时跳过本轮，避免多副本部署或手动同步与定时同步重叠时重复拉取。定时同步失败时同样写回最近错误与连续失败次数，并把最近同步时间推进到当前时间，使失败也遵守间隔，不在上游故障时连续冲击。定时触发的作业在作业记录中标记为定时触发，操作者记为“外部日志同步（定时）”。

同步失败的原因必须同时写入作业的失败明细与配置的“最近一次错误”，页面配置区直接显示最近错误；作业表每行可点击展开，展开后显示该作业的状态说明（`message`）与逐条失败明细。此前失败作业只置为 `FAILED` 且不写回配置，页面只能看到一个状态，无法判断是凭据缺失、上游拒绝还是参数错误，属于必须修掉的可用性缺陷。

配置支持删除：删除时一并删除该配置的凭据 Secret 与历史同步作业。删除前必须拦截“同步进行中”的状态——同步过程中删掉配置，作业会写回一个已不存在的配置，部分更新还会把已删资源复活成半成品；历史作业必须一并删除，否则作业表会残留一堆指向不存在配置的孤儿记录。删除按钮采用“点一次进入确认、再点确认才执行”的两段式，不弹浏览器原生确认框。

凭据写入状态必须在页面可见：读、写、清理三处的 Secret 名一律由配置名派生（`qsl-log-sync-credential-<配置名>`），不再由 `LogSyncProfile.spec.credentialSecretName` 决定。此前把 Secret 名写进配置、而读取时又依赖该字段，一旦字段没落库就出现“保存成功但页面一直显示未配置”的假故障。页面用两个标记分别显示“账号已保存/未保存”与“密码已保存/未保存”，密码不需要的数据源显示“无需密码”，避免用户只能看到一个笼统的“未配置”而不知道该补哪一项。

配置卡片、凭据卡片、同步操作卡片三段分开呈现；表单限宽后在宽屏上按三列排布，不再把七个字段拉成一整行；开关项单独成行并带说明；按钮组右对齐，不用按钮撑满整行。按钮整行铺满会把次要动作渲染成主视觉，属于排版缺陷。

#### 3.9.1 上游限流

- **documented**（实测）：LoTW 对同一用户限制「一分钟一次」报表下载，超限返回 503 的 `Page Request Limit` 页面（正文为 `Only one page request per user at a time`）。这类响应既不是凭据错误也不是网络故障，必须单独识别，否则用户会反复去改密码。
- 识别顺序：先判 429/503 与 `Page Request Limit` 正文，再判 401/403 与登录页，最后判「不是 ADIF」。命中限流报 `QSL-429-LS-0001`，并把上游页面原文带出来。
- 命中限流后自动等待 65 秒重试一次（上游自己写的是「等一分钟再试」），仍失败才上报。
- 同一账号的最小请求间隔由适配器声明（`QslLogSyncSource.minRequestIntervalSeconds()`，LoTW 为 60 秒，QRZ 为 0），同步、连通性测试、定时任务共用同一道闸，间隔未到直接报 `QSL-429-LS-0002` 并说明还需等几秒。**不要在闸门里 sleep**：连通性测试是用户点击触发的，卡六十秒只会像页面坏了。
- LoTW 的登录名取配置里的「账号」，留空才回落到本台呼号——官方明确「用户名多数等于主呼号但不总是如此，不应假定」。

## 4. 前台公开能力

公开接口 API Group：`api.qsl-management.bi1kbu.com/v1alpha1`。

当前公开页面与短码：

| 能力 | 公开页面 | 短码 |
| --- | --- | --- |
| 公开查询 | `/apis/api.qsl-management.bi1kbu.com/v1alpha1/cards/page` | `[qsl-card]` |
| 实体 QSL 卡申请 | `/qsl_card` | 无 |
| 线上换卡申请 | `/online_eyeball`、`/online_eyeball/{cardId}` | `[qsl-online-exchange-card]` |
| 线下换卡确认 | `/eyeball`、`/eyeball/{cardId}` | `[qsl-offline-exchange-card]` |
| 公开签收 | `/receipt_public`、`/receipt_public/{cardId}` | `[qsl-receipt-card]` |

线上换卡与线下换卡公开页面的服务端 endpoint 与渲染服务必须分离实现；页面字段、脚本、提交逻辑不再通过同一个模板的场景开关复用。根路径通过 Halo `AdditionalWebFilter` 内部改写到原有 `api.qsl-management.bi1kbu.com/v1alpha1` 页面 Endpoint，不使用 HTTP 重定向，不复制页面源码。实体卡申请 `/qsl_card` 由独立 `AdditionalWebFilter` 与渲染服务直接输出 HTML，不创建对应 `/apis` HTML 页面路由，也不提供极简别名。上述根路径属于非资源型 API，只允许匿名 `GET`，权限必须使用精确 `nonResourceURLs` 配置。

极简别名 `/eb`、`/oe`、`/rp` 分别等价于 `/eyeball`、`/online_eyeball`、`/receipt_public`，并支持单段 `/{cardId}`。极简路径用于手工分享、长度敏感的二维码等场景；短码继续生成语义清晰的推荐路径，CardPrint 的收卡回执二维码默认使用 `/rp` 以缩短二维码内容，其他页面映射仍默认使用推荐路径。

自插件 2.4.0 起，`/eyeball`、`/online_eyeball`、`/receipt_public` 为推荐公开页面路径；原 `/apis/api.qsl-management.bi1kbu.com/v1alpha1/EYEBALL`、`ONLINE_EYEBALL`、`receipt-public` 页面路由作为兼容入口保留并进入弃用期，计划在插件 3.0.0 移除。移除前必须先将推荐路径和极简路径改为直接调用公共页面处理服务，解除其对原 CustomEndpoint 路由的依赖。原公开提交与查询 API 不在此次页面路由弃用范围内。

当前公开数据接口：

1. `GET /qso-public/records`
2. `GET /qso-public/grids`
3. `GET /overview-public/summary`
4. `GET /exchange-online/-/bureaus`
5. `GET /exchange-online/-/station-cards`
6. `GET /exchange-offline/-/activities`
7. `POST /exchange-online/-/requests`
8. `POST /exchange-offline/-/confirm`
9. `POST /receipt-public/-/confirm`
10. `GET /qsl-card/-/qsos?callSign=...`：返回 QSO 是否可申请及实时聚合的审核、建卡、制卡、打包、发卡和签收公开状态；不得返回审核说明、地址、邮箱或内部建卡错误。
11. `GET /qsl-card/-/station-contact`
12. `POST /qsl-card/-/requests`

`GET /qso-public/grids` 返回通联记录中的公开网格清单，按对方四位 Maidenhead 网格聚合。接口从 `QsoRecord.spec.qth` 读取对方 QTH；仅当 QTH 整体为 4/6/8 位网格编号时纳入清单，6 位或 8 位截取前四位，地区文字、地址或混合文本跳过。同一四位网格下保留多条通联明细，并返回去重后的呼号集合；明细字段仅包含呼号、日期、时间、时区、模式、频率和频段，不返回地址、备注、本台信息等敏感内容。接口支持 `sceneType（场景类型）/dateFrom（开始日期）/dateTo（结束日期）/grid（网格编号）/detailLevel（详情级别）` 查询参数，`sceneType（场景类型）` 仅允许 `QSO/SWL`，`detailLevel（详情级别）` 可选 `full（完整）` 或 `brief（简略）`，不传默认为完整；简略模式只返回网格和呼号清单，不返回通联明细。接口不再设置返回数量上限，历史 `limit（返回数量上限）` 参数会被忽略。

公开接口允许匿名访问，但必须通过输入校验与限流。限流维度为 `endpoint + clientIp`，阈值来自 `SystemSetting.spec.guestQueryPerMinute`，未配置时使用默认值 `30`。`GET /qso-public/grids` 使用 `qso-public-grids` 限流键，`POST /receipt-public/-/confirm` 使用 `receipt-public-confirm` 限流键，`POST /exchange-offline/-/confirm` 使用 `exchange-offline-confirm` 限流键；实体卡申请查询、本台邮箱读取和提交分别使用 `qsl-card-qsos`、`qsl-card-station-contact`、`qsl-card-requests` 限流键。实体卡申请公开接口不得返回个人历史地址或地址内部资源名称。

## 5. 角色模型与权限节点

### 5.1 角色模型

1. 游客：仅可访问聚合到 `anonymous` 的公开页面与公开接口。
2. HAM 用户、操作员：当前仅保留业务规划，不作为一期差异化授权落地。
3. 管理员/超级管理员：当前后台能力的主要使用对象。

### 5.2 权限设计原则

1. 后台每个业务能力至少包含 `view` 与 `edit`。
2. 服务端 RBAC 为安全边界，前端权限隐藏仅作为用户体验。
3. 多菜单可复用同一权限节点，例如 `station-profile`、`card-record`、`mail-receive-confirm`。
4. `edit` 默认依赖同项或相关 `view` 权限。

### 5.3 当前权限节点

| 权限节点 | 复用菜单或能力 | 主要依赖 |
| --- | --- | --- |
| `overview-dashboard` | 总览看板 | 通联、卡片、发信、送达读取 |
| `system-settings` | 系统参数 | 无 |
| `migration-state` | 数据迁移状态 | 导入导出复用，编辑依赖只读 |
| `station-profile` | 通信地址、本台设备、本台卡片 | 设备库读取 |
| `qso-record` | 通联日志 | 设备库、本台配置读取 |
| `card-record` | 各场景创建卡片 | 通联、本台配置读取 |
| `card-issue` | 各场景制卡签发 | 卡片、通联、线下活动、系统参数读取，编辑依赖卡片写入与审计日志写入 |
| `mail-send-confirm` | 发信确认 | 卡片读取 |
| `mail-receive-confirm` | 送达确认、三类收卡 | 卡片、通联读取 |
| `exchange-request-review` | 线上审核、线下活动 | 地址/卡片读取，编辑依赖卡片写入 |
| `card-mutation` | 卡片异动 | 卡片写入、通联与地址读取；Console 动作 `card-mutations/create` |
| `qso-query` | 通联记录查询 | 通联读取 |
| `card-query` | 卡片记录查询、收卡记录查询 | 卡片读取；编辑额外依赖 `receive-records/delete` 与审计日志写入 |
| `report-auditlog` | 统计报表、审计日志 | 查询与业务读取 |
| `address-bureau` | 地址管理、卡片局管理 | 无 |
| `equipment-catalog` | 设备库维护 | 无 |
| `import-export` | 导入导出 | 查询、审核、地址、设备读取；编辑依赖相关写权限 |
| `card-print-tool` | 本地卡片打印工具令牌授权 | 通信地址/本台卡片读取、卡片记录编辑、通联读取、地址/卡片局读取、线下换卡活动读取 |
| `log-sync` | 外部日志同步 | 通联读取；编辑依赖通联写入与审计日志写入 |

RBAC 模板实际落点：`src/main/resources/extensions/qsl-menu-role-templates.yaml`。

除上表的菜单节点外还有两个隐藏模板，不出现在菜单勾选列表，只供其他模板依赖：

- `qsl-management-offline-activity-view`：仅授予 `offline-activities` 的 `get/list`，用于线下换卡活动筛选。
- `qsl-management-audit-log-write`：仅授予 `qsl-audit-logs` 的 `create`（2026-10-31 新增）。控制台前端在制卡签发、卡片记录、卡片异动、发信/送达确认、地址管理、设备库、系统参数等模块内直接写审计日志，缺这条规则时业务写入会先成功、审计写入再返回 403，页面报错但数据已改。所有带写操作的前端模块对应编辑模板都必须依赖该模板。

## 6. 本地打印工具

`tools/CardPrint` 当前作为独立 Python 工具存在，入口为：

```powershell
python -m cardprint.cli ui online
```

当前在线桥接代码事实：

1. 默认地址为 `http://localhost:8090`，但配置中可传入其他 `base_url`。
2. 读取 `card-records` 生成卡片/信封队列，卡片打印会按 `CardRecord.spec.qsoRecordName` 额外读取 `qso-records` 并注入 `qsoInfo`；通联业务制卡的发射地址字段优先使用 `QsoRecord.spec.myQth`，旧预设字段 `qth` 与新预设字段 `my_qth` 均按此规则取值，缺失时才兜底到对方 `qth`；线下换卡制卡仍优先使用关联活动的 `OfflineActivity.spec.activityLocation`；`CardRecord.spec.cardReceived` 为空或缺失时按未收卡处理，打印互斥项默认勾选“请回卡片”。
3. 信封打印纳入未打包（`envelopePrinted=false`）且卡片版本不是“不发卡”的卡片记录，并额外读取 `station-profiles`、`address-book-entries`、`bureau-entries` 做本台地址和收件地址补全；封面打印页提供去向类型下拉筛选，去向国为空按国内卡片处理，存在去向国内容按国际卡片处理；打包确认使用同一未打包且非“不发卡”口径回写 `envelopePrinted`。
4. 补打信封页位于打包确认后，读取 `address-book-entries` 与 `bureau-entries` 生成独立队列，复用信封预设，支持按呼号、地址编号或卡片局编号筛选，支持当前行打印、勾选批量打印与全部打印，不回写业务状态。
5. 补打眼球卡片页位于在线打印工具内，使用 `bridge_config.json` 的 `presets.eyeball_reprint_card` 持久化保存专用补卡预设；手工输入呼号与日期时间，支持实时取当前日期时间，生成打印行时固定 `cardType=EYEBALL`、“发出卡片”状态与“请回卡片”状态，不读取或回写后台业务记录。
6. 卡片版本以 `station-cards` 为准；本地打印工具通过公开接口 `GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/exchange-online/-/station-cards` 拉取版本列表，并按 `sortOrder` 与版本号排序，不从 `card-records` 反推。
7. 卡片二维码拼接支持 `qrcode.path_mappings` 短路径映射，在线打印配置页可在站点地址下方配置线下换卡、线上换卡、签收确认三类短路径；新配置默认将线下换卡、线上换卡、签收确认长路径分别映射为 `/eyeball`、`/online_eyeball`、`/rp` 后再追加卡片 ID 与 `cs` 参数，其中收卡回执使用极简 `/rp` 以缩短二维码内容，已有配置中的自定义映射保持不变。
8. 在线打印工具登录并拉取卡片版本时使用后台线程执行网络请求，只补齐空白本台通信地址字段，不覆盖已有本台姓名、电话、邮编、地址；受保护接口返回登录页或 HTML 时明确提示认证或权限问题，不静默解析为空数据。
9. 打印状态通过人工确认回写 `CardRecord.spec.cardIssued/cardIssuedAt` 或 `envelopePrinted`，`cardIssuedAt` 使用 `yyyy-MM-dd HH:mm:ss` 文本格式；回写会同步刷新 `CardRecord.status.flowStatus`，并按后台状态联动规则补齐或清理相关状态字段。
10. 打印预设字段支持 `fixed_text` 固定文本，用于固定辅助说明、提示语或版面标识；固定文本字段由标定工具维护，打印工具和在线打印工具不要求录入该字段，预览与打印时优先输出固定文本。
11. 打印预设的 `text_align（文本对齐）` 支持 `left（左对齐）`、`center（居中对齐）`、`right（右对齐）`；居中对齐必须配置大于 `0` 的 `print_width_mm（打印宽度）`，`distribute_align（分散对齐）` 开启时优先于普通文本对齐。
12. 打印文本内容支持字面量 `/n` 和真实换行符强制换行，只识别小写 `/n`；连续换行保留空行，`max_len（最大长度）` 仅计算实际打印字符；二维码字段不解析换行标记。
13. 打印预设字段支持 `print_border（是否打印边框）`；开启时必须同时配置大于 `0` 的 `print_width_mm（打印宽度）` 和 `print_height_mm（打印高度）`，边框按照字段位置和宽高绘制一次，不改变文字或二维码坐标。
14. 打印预设字段支持 `layout_mode（排版方式）`：`horizontal（横向）`、`vertical（纵向）`、`mixed_vertical（混合纵向）`，默认横向。纵向将全部字形顺时针旋转 `90°`，混合纵向旋转 ASCII 英文字母、数字、英文冒号 `:` 和半角空格，中文、全角冒号 `：` 及其他字符保持正向并从上向下逐字排列；旋转后的半角内容按原字形宽度采用紧凑间距。
15. 纵向排版第一列位于字段区域最右侧；遇到 `/n`、真实换行或超过打印高度时回到字段顶部并向左换列，超过打印宽度后截断。纵向的左对齐、居中、右对齐分别表示顶部、垂直居中、底部对齐，分散对齐沿打印高度生效。
16. 纵向排版必须配置大于 `0` 的打印宽度和打印高度；数字上移仅在横向排版生效，二维码不应用文字排版方式。

线下换卡业务不需要打包、制卡邮件流程和收件地址展示；后台线下换卡制卡签发页面只保留确认制卡，线下换卡创建卡片的批量编辑允许修改制卡状态。

## 7. 当前一致性说明

1. 本文件已按 2026-05-02 代码事实同步。
2. 若本文件与 `插件工程根目录` 代码不一致，以代码事实为准，并在同次改动中更新本文档。
3. 若后续新增 API、权限节点、模型字段、菜单或打印桥接契约，必须同步更新本文件、`docs/spec/BackendApiContract.md` 与 `docs/spec/项目信息结构化清单.md`。

