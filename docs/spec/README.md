# QSL 项目结构化文档索引

## 文档定位
1. 本目录用于沉淀“与当前代码一致”的结构化项目信息。
2. 本目录文档属于输出性文档，需随代码同步更新。

## 当前文档
1. `ProductDefinition.md`：产品定义、业务边界与约束。
2. `BackendApiContract.md`：后台与公开 API 合同，包含公开卡片版本的 `qsoOnly（是否仅限实体 QSL 通联卡）` 场景限制字段，以及实体 QSL 卡申请的审核、建卡、制卡、打包、发卡和签收公开状态合同。
3. `项目信息结构化清单.md`：项目概览、目录结构、业务菜单、数据模型、接口边界、构建运行与工具链。
4. `数据字段与组织结构汇总.md`：数据字段、资源命名与组织结构汇总。
5. `QSL2重构方案.md`：2.0.0 发卡/收卡解耦、线下换卡整改与统计口径重构方案。
6. `Halo非资源型API待核查问题.md`：记录自定义深层路由可能被 Halo 归类为非资源型 API 的待验证权限问题、候选接口与后续处理条件。

## 关联约束性开发文档

1. `../plans/场景拆分重构冻结方案.md`：业务场景拆分与菜单流程冻结基线。
2. `../plans/实体QSL卡申请开发约束框架.md`：`/qsl_card` 实体 QSL 卡申请功能的页面、数据模型、API、安全、RBAC、审核建卡、公开进度状态与验收约束；当前功能已按该框架实现。

## 呼号示例统一规则（2026-10-13）

参见 `ProductDefinition.md` 的页面展示约定、`BackendApiContract.md` 的 `GET /station-call-sign（本台示例呼号）` 合同及 `项目信息结构化清单.md` 的呼号示例数据流说明。

### 本次验证记录

- 插件版本：`2.3.31`；Java 后端 223 项测试、前端 13 项测试、CardPrint 相关 43 项测试通过，前端类型检查和构建通过。
- 本地 Docker Desktop 开发实例运行 Halo 2.23.2；首次热重载因开发实例认证未就绪返回 401，完成官方 DevTools 初始化后，`reloadPlugin` 执行成功，插件运行状态为 `STARTED`。
- 运行验证：持久化呼号先后设为 `BG1ABC`、`BH2DEF` 和空白，新增接口及 5 类公开页面均随配置更新，实际预填呼号保持不变；验证后原呼号已恢复。
- 匿名 GET 示例接口成功，仅返回呼号且禁止缓存；匿名 POST 及完整通信地址读取均被 Halo 拒绝并重定向登录（302）。

## 线上换卡卡片局流程实测（2026-10-13）

- 环境：本地 `http://localhost:8090`，Halo 2.23.2、插件 2.3.31；线上换卡采用手动审核，相关邮件策略为手动发送。
- 初始实例没有卡片版本或卡片局，已添加明确标记的“模拟测试卡片-20261013”和“模拟测试卡片局-20261013”（`BURO-1`），用于本次测试。
- 实际通过 `/oe` 页面以模拟呼号 `ZZ0TEST` 选择已有卡片局并提交，生成申请 `EX0001`；卡片局名称、邮编和地址按提交时快照持久化，初始状态为“待审核”。
- 后台点击“同意”后状态变为“已通过”，此时尚未创建卡片；另点击“创建卡片”生成 `C1001`，其 `addressEntryName（绑定地址编号）` 正确关联 `BURO-1`，没有重复新增卡片局。
- 通过后台依次确认制卡、打包和发信，再通过 `/rp/C1001?cs=ZZ0TEST` 页面提交模拟签收，最终 `flowStatus（流转状态）=已签收`；制卡、打包、发信、签收标记均为真，`cardReceived（我方收到对方卡片）` 仍为假，符合收发解耦规则。
- 反向验证通过：同呼号存在待审核申请时重复提交返回 409；审核前建卡返回 422；同一申请重复建卡返回 422。最终仍只有一条申请、一张卡片和一个卡片局。
- 卡片局关联机制：公开提交保存名称、邮编、地址，不提交卡片局资源编号；建卡时按这三项精确匹配并复用主数据。如提交后主数据发生变化，可能创建新的卡片局。
- 本次仅验证软件状态流转和持久化，未实际打印、邮寄或发送通知邮件；模拟数据保留在本地实例，便于复查。过程截图及状态快照位于本地忽略目录 `tmp/oe-test-20261013/`。

### 实测发现的配置与提示问题

1. 本站通信地址尚未配置，申请成功提示中的本站邮编、地址、收件人、电话、邮箱均显示 `-`；真实换卡前需完成配置。
2. 审核成功提示重复使用“已”，显示“换卡申请已已通过”（拒绝分支同理）。位置：`ui/src/views/modules/ExchangeRequestReviewModule.vue`。
3. 发信确认在手动邮件策略下提示“发卡邮件已自动跳过”，实际邮件状态仍为空、未自动处理；应区分“手动发送”与持久化的“已跳过”状态。位置：`ui/src/views/modules/MailSendConfirmModule.vue`。
4. 卡片局模式不提供申请人邮箱输入，申请邮箱和卡片通知邮箱为空，本次未覆盖实际邮件送达。

以上为 2.3.31 的测试记录；其中第 1 项的地址占位提示及第 2、3 项文案已纳入下述 2.3.32 修正，第 4 项邮箱输入不在本次修改范围内。

## 状态提示与提交成功页修正（2.3.32）

- 审核提示直接显示“已通过/已拒绝”；邮件策略区分 `PENDING（待处理响应）` 和 `SKIPPED（已持久化跳过）`，手动策略显示“等待手动发送”。
- `/oe` 成功页分项显示申请编号、审核状态、下一步和本站通信地址；本站未填写地址时明确提示联系管理员确认寄送信息，不再展示整块空占位。
- 沿用 Halo 2.23 的现有接口、权限模板、认证与限流；没有增加持久化模型字段或权限。官方核验日期：2026-10-13，依据为 [自定义 API](https://docs.halo.run/developer-guide/plugin/api-reference/server/custom-endpoints)、[API 权限控制](https://docs.halo.run/developer-guide/plugin/security/role-template) 和 [2.23 API 变更记录](https://docs.halo.run/developer-guide/plugin/api-changelog#2230)。

### 2.3.32 验证结果（2026-10-13）

- Java 后端 230 项测试、前端 13 项测试及类型检查通过，构建成功；`reloadPlugin` 成功，运行插件版本为 `2.3.32`、状态为 `STARTED`。
- 实际通过 `/oe` 提交 `ZZ0FIX1 / EX0002`，成功页正确显示待审核、等待管理员审核后建卡、本站通信地址尚未配置。后台审核提示为“换卡申请已通过”，创建 `C1002` 并模拟制卡、打包、发信后分别显示制卡/发卡邮件“等待手动发送”；两项持久化邮件状态仍为空。
- 临时启用自动审核并填写模拟本站地址，通过 `/oe` 提交 `ZZ0FIX2 / EX0003`，页面显示已通过、等待管理员建卡寄送以及已配置的本站地址；测试后审核策略和本站通信配置均已恢复原值。
- 策略接口运行验证：手动策略返回 `PENDING` 且卡片记录完全不变；自动跳过但业务条件不足返回 `PENDING` 且记录不变；真正自动跳过时返回并持久化 `SKIPPED`。用于该验证的 `C1001` 记录及系统策略已恢复原值。
- 回归模拟记录保留在本地；没有实际打印、寄送或发送邮件。截图位于本地忽略目录 `tmp/feedback-test-2.3.32/`；页面浏览器日志未发现脚本错误。
- 已再次核对官方自定义 API、权限控制与 2.23 变更记录：使用现有响应式接口，未新增权限、持久化字段或高于目标版本的 API。

## 2.3.33 发布验证（2026-10-13）

- 发布范围为此前 2.3.31 与 2.3.32 开发验证阶段累计的呼号示例统一、状态反馈和线上换卡成功页改进。
- `gradle.properties`、插件元数据和 JAR 产物版本统一为 `2.3.33`；正式构建及 `reloadPlugin` 成功，本地 Halo 运行状态为 `STARTED`。
- 本次打包的后端 230 项测试通过；同一功能代码的前端 13 项、类型检查和 CardPrint 相关 43 项测试已通过，详见上文验证记录。
- CI 执行完整构建检查；CD 分别执行构建、GitHub Release 附件上传和 Halo 应用市场提交。发布时不手工上传 JAR，三项 CD 结果需分别核实。

## 2.3.34 地址串用修复与验证（2026-10-17）

- 生产只读排查发现“确认打包”曾用页面全局候选地址覆盖其他卡片绑定。本次修复仅在本地实现与验证，未修改生产配置、历史卡片或邮件记录，未执行历史数据迁移及补发邮件。
- `CardIssueModule.vue` 删除打包时解析/覆盖地址的逻辑；打包保留当前卡片地址和邮箱，包括空值、个人地址及卡片局地址。选定地址仅作为候选，通过独立“确认绑定地址”提交，页面明确列出呼号精确匹配且未打包、未发出、未签收的目标卡片。
- 输入或切换查询上下文会清空候选；写入期间锁定查询、选择及其他写操作，固定本次提交目标和资源版本。显式绑定审计保存原地址、新地址和目标卡片编号；批次部分失败停止后续处理，并报告真实已保存数量。
- 沿用 Halo 2.23 Extension `PUT /apis/qsl-management.bi1kbu.com/v1alpha1/card-records/{name}`、`metadata.version（资源版本）` 和服务端 RBAC。界面 `card-issue:view/edit` 为菜单节点，资源更新由现有 `card-records:update` 权限控制；未新增路由、数据模型、权限或 CardPrint 契约。官方核验日期为 2026-10-17，依据为 [API 权限控制](https://docs.halo.run/developer-guide/plugin/security/role-template) 与 [2.23 API 变更记录](https://docs.halo.run/developer-guide/plugin/api-changelog#2230)。
- 新增 15 项实际挂载 Vue 组件的回归用例，覆盖先选 A 再打包 B、空地址和卡片局保留、选择不写库、显式确认、相似呼号隔离、历史卡片排除、上下文切换、并发提交、409 冲突及审计失败时的保存计数。测试全部模拟资源、审计和邮件接口，不访问生产或发送真实邮件。
- 前端共 28 项测试、类型检查、后端 230 项测试全部通过；执行 `./gradlew.bat build reloadPlugin --console=plain` 成功。版本文件与 JAR 元数据一致为 `2.3.34`，本地 Docker Halo 返回插件状态 `STARTED`；本地通联/线上换卡制卡邮件策略均为 `MANUAL`。
- 历史异常地址恢复需要另行依据申请、审计与实际寄送情况核对。2.3.34 为本地开发验证版本，该阶段未提交或推送 Git、未创建 GitHub Release、未部署生产；正式发布版本见下文。

## 2.3.35 发布验证（2026-10-17）

- 正式版本在本地开发验证版 2.3.34 基础上递增，发布内容为上文地址串用修复；插件元数据、Gradle 版本与 JAR 版本统一为 `2.3.35`。
- `./gradlew.bat clean build reloadPlugin --console=plain` 通过：后端 230 项、前端 28 项测试及类型检查通过，本地 Docker Halo 返回 `version=2.3.35`、`phase=STARTED`。
- 组件测试夹具已统一使用虚构呼号、卡片编号及 `example.test` 邮箱，替换后再次通过前端 28 项测试与类型检查；生产日志和数据不进入提交。
- GitHub Release 附件由 CD 自动构建上传，同时执行 Halo 应用市场发布；发布过程分别跟踪 CI、CD 构建、附件上传和市场发布结果。发布包不自动修复已有异常地址、不补发历史邮件，生产后台仍保持只读。

## 权限模板补齐（2026-10-31，未发布）

- 缺陷一：菜单节点 `card-issue` 被三个「制卡签发」菜单和 `MenuModuleRegistry` 引用，但 `qsl-menu-role-templates.yaml` 里没有任何对应 Role 模板，非超管角色拿不到 `plugin:qsl-management:card-issue:view/edit`，菜单静默消失。本次补 `qsl-management-card-issue-view/edit` 两个模板，依赖关系与前端菜单、后端注册表三处一致。
- 缺陷二：Console 路由 `POST /receipt-confirms/{cardRecordName}/confirm` 没有授权规则，控制台确认签收对非超管角色报 403。本次把 `receipt-confirms` 的 `create` 补进 `qsl-management-mail-receive-confirm-edit`，与 `BackendApiContract.md` 记录的 `mail-receive-confirm:edit` 一致。
- 缺陷三：控制台前端在 12 个模块内直接创建 `qsl-audit-logs`，但此前只有 `report-auditlog` 模板持有该资源且仅有 `get/list`，非超管执行业务写入会先改数据、再在审计写入处 403。本次新增隐藏模板 `qsl-management-audit-log-write`（仅 `qsl-audit-logs/create`），并由 12 个编辑模板统一依赖。
- `QslRoleTemplateContractTest` 增加三条反向校验：菜单节点必须有同名模板、Console 每条路由必须命中规则、写审计的编辑模板必须依赖审计写入模板。三条校验在补丁前都会失败，补丁后通过。
- 本次仅改权限模板与契约测试，未改路由、数据模型、业务逻辑与 CardPrint 契约；版本号未递增，正式打包前再按 `agents.md` 同步递增。

## 外部日志同步（2026-10-31，未发布）

- 新增功能：按本台呼号从外部日志服务拉取通联记录并写入通联记录列表，菜单入口「数据 → 外部日志同步」，权限节点 `log-sync`，调用方为「数据」组内第 6 个模块。功能名不使用 LoTW 商标，页面标题统一「外部日志同步」。
- 数据源：ARRL LoTW 无官方编程接口，采用带查询参数的 ADIF 下载地址抓取；QRZ Logbook 使用官方 Logbook API 的 `FETCH` 动作（需 Key 与订阅）。Club Log 整本日志下载与 eQSL InBox 未纳入本次范围，原因见 `../plans/外部日志同步设计基线.md` §4。
- 数据模型：新增 `LogSyncProfile`（配置）与 `LogSyncJob`（作业）两个 Extension，Extension 注册点由 18 项增至 20 项；`QsoRecord` 不新增字段，来源通过既有 `status.source` 与 `status.autoCreated` 标记。
- 凭据：账号与密码由页面表单填写后写入 Halo `Secret`，不落 `LogSyncProfile.spec`、不进导出包、不回显、不写进错误信息与审计日志；错误信息只含异常类型与安全描述，不拼接含密码的请求地址。
- 判重：按 `sceneType|呼号|日期|时间|时区|模式|频率` 判定，与前端 ADIF 导入同一规则；`SKIP` 保留已有，`OVERWRITE` 只覆盖通联事实字段，不动备注与人工维护的设备、天线信息。
- 服务端：新增 `QslAdifReader`（ADIF 解析）、`QsoDuplicateKey`（判重键）、`QslLogSyncMapping`（ADIF 字段到通联记录的纯函数映射）、`QslLogSyncSource` 及 `QslLotwLogSyncSource`/`QslQrzLogbookSyncSource` 两个适配器、`QslLogSyncService`（配置、凭据、连通性测试、同步编排、作业记录）。控制台新增 8 条 `/log-sync` 路由，错误码前缀 `QSL-4xx-LS-xxxx`。
- RBAC：新增 `qsl-management-log-sync-view` 与 `qsl-management-log-sync-edit` 两个 Role 模板（yaml 43→45 document），编辑模板依赖 `log-sync-view`、`qso-record-edit` 与 `audit-log-write`；前端菜单与 `MenuModuleRegistry`（17→18 模块）同步注册 `plugin:qsl-management:log-sync:view/edit`。
- 定时同步：插件启动类开启 Spring 调度，`QslLogSyncScheduler` 每分钟检查一次到期配置（三个条件：已启用、已开启自动同步、距上次同步超过同步间隔且下限 60 分钟），首次配置立即执行一次；不注册固定周期任务，改间隔无需重启；同一配置有未结束作业时跳过本轮；失败也写回最近错误与连续失败次数并推进最近同步时间，避免上游故障时连续冲击。
- 凭据按数据源区分：LoTW 必须填用户名与 `lotw.arrl.org` 登录密码（可能与 ADIF 签名证书密码不同）；QRZ Logbook 只填 Key、密码可留空，`QslLogSyncSource.requiresPassword()` 控制该差异，`loadCredentials`、`upsertSecret`、`credentialConfigured` 三处都按数据源判断。
- 失败可见：同步失败原因同时写入作业失败明细与配置最近错误，作业表整行可点击展开查看 `message` 与逐条明细；凭据不完整时页面给出提示；「测试连接」「立即同步」保持可点，若输入框里刚填了凭据则先自动保存再执行，否则明确提示先填写（不禁用按钮，避免“填完还是灰的又没解释”）。
- 官方文档核验：LoTW 参数按 `https://www.arrl.org/adif` 实现（`qso_qsl=no` 才返回通联记录且 `qso_qsorxsince` 才生效、`qso_mydetail=yes` 才返回本台站址字段、增量游标取头部 `APP_LOTW_LASTQSORX`、模式缺失时回退 `APP_LOTW_MODE`）；QRZ 按 `https://www.qrz.com/docs/logbook/QRZLogbookAPI.html` 实现（增量去掉 `ALL` 改用 `AFTERLOGID`，其口径为大于等于，故游标取最大 `APP_QRZLOG_LOGID` 加一）。
- 删除配置：新增 `DELETE /log-sync/profiles/{profileName}`，一并删除凭据 Secret 与该配置的历史作业；同步进行中返回 409 `QSL-409-LS-0001`，配置不存在返回 404 `QSL-404-LS-0001`。前端为两段式确认（点「删除配置」进入确认态，再点「确认删除」执行），不弹原生确认框。RBAC 的 `log-sync-edit` 相应补充 `delete` 动词（console `log-sync` 与 `log-sync-profiles`/`log-sync-jobs` 资源）。
- 凭据读写同源：Secret 名一律由配置名派生（`qsl-log-sync-credential-<配置名>`），`LogSyncProfile.spec.credentialSecretName` 标记废弃且代码不再读写。此前写路径用派生名、读路径用配置字段，字段没落库就出现「保存成功但页面一直显示未配置」的假故障。页面用两个标记分别显示账号与密码的写入状态，不需要密码的数据源显示「无需密码」。
- 页面重排：配置、账号凭据、同步操作三张卡片分开；表单限宽后宽屏三列排布，不再把七个字段拉成一整行；开关项单独成行并带说明；按钮组右对齐，不再出现按钮撑满整行的排版；作业表精简为「开始时间/结果/拉取/新建/更新/跳过/失败/耗时」并把时间统一显示为本地时间。
- 删除链路的内层 `Mono` 必须用 `Mono.defer` 包裹：否则装配阶段就会执行删除前的查询与删除动作，前置检查（作业进行中拦截）形同虚设。
- 凭据保存必须处理「Secret 尚不存在」：`loadSecret(...).flatMap(...)` 在空流上会整段跳过，首次保存既不创建也不报错，表现为「保存成功但一直显示未配置」。真实写法是再接 `switchIfEmpty(Mono.defer(...))` 走创建分支。
- 验证：后端全量 298 项测试通过（新增 `QslAdifReaderTest` 7、`QsoDuplicateKeyTest` 5、`QslLogSyncMappingTest` 10、`QslLogSyncSchedulingTest` 16、`QslLogSyncDeleteTest` 5、`QslLogSyncCredentialTest` 4、`QslLogSyncTestConnectionTest` 3、`QslLogSyncStuckJobTest` 5、`QslLogSyncRateLimitTest` 6），`QslRoleTemplateContractTest` 的菜单覆盖与路由覆盖断言对 `log-sync` 同样通过；前端类型检查与现有 28 项前端测试通过。本次未连真实 LoTW/QRZ 账号，同步链路以单元测试与模拟响应验证。
- 作业卡死处理（2.3.46）：立即同步改为后台执行，页面轮询作业表；作业收尾遇乐观锁冲突会重取再写；新增「强制停止」与启动时遗留 `RUNNING` 作业收尾，避免页面永远显示「进行中」并挡住重置游标与删除配置。
- 上游限流处理（2.3.47）：LoTW 对同一账号限制每分钟一次下载，超限返回 503 的 `Page Request Limit` 页面。识别后按 `QSL-429-LS-0001` 上报并自动等待 65 秒重试一次；同步、连通性测试、定时任务共用「同账号最小请求间隔」闸门（`QSL-429-LS-0002`）。
- 数据清理：见 `docs/reports/插件数据清理方案-2026-10-31.md`（二十个扩展类型的存放位置、四档清理力度、直连数据库的前置与顺序）。
- 收卡记录删除（2.3.48）：审计-收卡记录查询新增「删除」，补上 `receive-records/delete` 授权（`card-query:edit` 单列一条规则，不连带放开 `card-records` 删除）与审计日志写入依赖。此前插件没有任何模板给过该动词，导致删除一律 403。后端全量 299 项测试通过（`QslRoleTemplateContractTest` 新增 `shouldGrantReceiveRecordDeleteToCardQueryRole`），前端类型检查与 28 项前端测试通过。
- 偏差与验证限制见 `../reports/外部日志同步实现报告-2026-10-31.md`。

## 更新要求
1. 变更菜单结构、场景拆分、数据模型、API 合同或本地打印链路时，必须同步更新本目录。
2. 如实现超出 `agents.md` 与 `docs/spec/ProductDefinition.md` 约束，需另附“偏差表”。

