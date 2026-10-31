<script setup lang="ts">
import { VButton, VCard, VTag } from '@halo-dev/components'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  clearLogSyncCredential,
  deleteLogSyncProfile,
  forceStopLogSync,
  getConsoleApiErrorMessage,
  listLogSyncJobs,
  listLogSyncProfiles,
  listLogSyncSources,
  resetLogSyncCursor,
  runLogSync,
  saveLogSyncCredential,
  saveLogSyncProfile,
  testLogSyncConnection,
  type LogSyncJob,
  type LogSyncProfile,
  type LogSyncProfilePayload,
  type LogSyncSourceDescriptor,
} from '../../api/qsl-log-sync-api'

interface ProfileForm {
  sourceType: string
  myCallSign: string
  enabled: boolean
  autoSyncEnabled: boolean
  syncIntervalMinutes: number | null
  defaultSceneType: string
  conflictStrategy: string
  maxRecordsPerSync: number | null
}

/** 与后端 isDueForScheduledSync 一致：间隔下限 60 分钟。 */
const MIN_SYNC_INTERVAL_MINUTES = 60

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const testing = ref(false)
const syncing = ref(false)
const deleting = ref(false)
const confirmingDelete = ref(false)
const feedback = ref('')
const sources = ref<LogSyncSourceDescriptor[]>([])
const profiles = ref<LogSyncProfile[]>([])
const currentProfileName = ref('')
const jobs = ref<LogSyncJob[]>([])
const testSamples = ref<string[]>([])
const resettingCursor = ref(false)
const expandedJobName = ref('')
const credentialAccount = ref('')
const credentialPassword = ref('')
const stopping = ref(false)
/** 后台同步的轮询计时器：作业在服务端跑，页面靠它把结果刷新出来。 */
let jobPollTimer: ReturnType<typeof setTimeout> | null = null

const form = ref<ProfileForm>(createEmptyForm())

function createEmptyForm(): ProfileForm {
  return {
    sourceType: 'LOTW',
    myCallSign: '',
    enabled: true,
    autoSyncEnabled: false,
    syncIntervalMinutes: 60,
    defaultSceneType: 'QSO',
    conflictStrategy: 'SKIP',
    maxRecordsPerSync: 5000,
  }
}

const currentProfile = computed(() =>
  profiles.value.find((profile) => profile.name === currentProfileName.value),
)

const currentIsQrz = computed(
  () => (currentProfile.value?.sourceType ?? form.value.sourceType) === 'QRZ_LOGBOOK',
)

const credentialTip = computed(() =>
  currentIsQrz.value
    ? 'QRZ Logbook 只校验 Key，密码留空即可。'
    : 'LoTW 需要用户名与 lotw.arrl.org 登录密码（首次必须填写密码）。',
)

const currentSourceNote = computed(() => {
  const sourceType = currentProfile.value?.sourceType ?? form.value.sourceType
  const matched = sources.value.find((source) => source.sourceType === sourceType)
  return matched ? matched.note : ''
})

const nextSyncText = computed(() => {
  const profile = currentProfile.value
  if (!profile) {
    return '—'
  }
  if (!profile.enabled || !profile.autoSyncEnabled) {
    return '未开启自动同步'
  }
  const interval = Math.max(
    MIN_SYNC_INTERVAL_MINUTES,
    profile.syncIntervalMinutes ?? MIN_SYNC_INTERVAL_MINUTES,
  )
  if (!profile.lastSyncAt) {
    return '下一轮检查时执行'
  }
  const last = new Date(profile.lastSyncAt).getTime()
  if (Number.isNaN(last)) {
    return '按间隔执行'
  }
  const next = last + interval * 60_000
  if (next <= Date.now()) {
    return '已到期，下一轮检查时执行'
  }
  return formatTime(new Date(next).toISOString())
})

const sourceLabel = (sourceType: string): string => {
  const matched = sources.value.find((source) => source.sourceType === sourceType)
  return matched ? matched.displayName : sourceType
}

/** 页面统一显示本地时间，避免 ISO 的 Z 后缀让人误判时区。 */
const formatTime = (value: string | null | undefined): string => {
  if (!value) {
    return '—'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  const pad = (part: number) => `${part}`.padStart(2, '0')
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(
    parsed.getHours(),
  )}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}`
}

/** 是否存在进行中的作业（决定要不要露出「强制停止」）。 */
const hasRunningJob = computed(() => jobs.value.some((job) => job.status === 'RUNNING'))

const jobResultTheme = (status: string): 'primary' | 'danger' | 'secondary' =>
  status === 'SUCCESS' ? 'primary' : status === 'RUNNING' ? 'secondary' : 'danger'

const jobResultLabel = (status: string): string =>
  status === 'SUCCESS' ? '成功' : status === 'RUNNING' ? '进行中' : '失败'

/** 作业耗时，单位秒；未结束的作业返回空串。 */
const jobDuration = (job: LogSyncJob): string => {
  if (!job.startedAt || !job.finishedAt) {
    return ''
  }
  const started = new Date(job.startedAt).getTime()
  const finished = new Date(job.finishedAt).getTime()
  if (Number.isNaN(started) || Number.isNaN(finished) || finished < started) {
    return ''
  }
  return `${((finished - started) / 1000).toFixed(1)} 秒`
}

const toggleJobDetail = (jobName: string) => {
  expandedJobName.value = expandedJobName.value === jobName ? '' : jobName
}

/**
 * 把页面上刚填、还没保存的凭据先写进 Secret。
 *
 * <p>「测试连接」「立即同步」只读后端 Secret，不看输入框；用户填完密码没点「保存凭据」时，
 * 若直接发起动作只会拿到 422。这里把「填了就当要保存」补上，免得用户以为按钮坏了。
 *
 * @returns 是否已具备可用的凭据
 */
const persistPendingCredential = async (): Promise<boolean> => {
  const password = credentialPassword.value
  if (password) {
    await saveLogSyncCredential(currentProfileName.value, {
      accountName: credentialAccount.value.trim(),
      password,
    })
    credentialPassword.value = ''
    await loadAll()
    return true
  }
  if (currentProfile.value?.credentialConfigured) {
    return true
  }
  feedback.value = currentIsQrz.value
    ? '还没有保存 QRZ Key：请在「账号凭据」里填写后点「保存凭据」，或直接填好后点这里。'
    : '还没有保存密码：请在「账号凭据」里填写 LoTW 用户名与登录密码后点「保存凭据」，或直接填好后点这里。'
  return false
}

const loadAll = async () => {
  loading.value = true
  try {
    const [sourceList, profileList] = await Promise.all([
      listLogSyncSources(),
      listLogSyncProfiles(),
    ])
    sources.value = sourceList
    profiles.value = profileList
    if (!currentProfileName.value && profileList.length > 0) {
      selectProfile(profileList[0].name)
    } else if (currentProfileName.value) {
      const matched = profileList.find((profile) => profile.name === currentProfileName.value)
      if (matched) {
        selectProfile(matched.name, false)
      } else {
        currentProfileName.value = ''
        jobs.value = []
      }
    }
    feedback.value = ''
  } catch (error) {
    feedback.value = `加载同步配置失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    loading.value = false
  }
}

const selectProfile = (profileName: string, withJobs = true) => {
  currentProfileName.value = profileName
  confirmingDelete.value = false
  expandedJobName.value = ''
  testSamples.value = []
  const profile = profiles.value.find((item) => item.name === profileName)
  if (profile) {
    form.value = {
      sourceType: profile.sourceType,
      myCallSign: profile.myCallSign,
      enabled: profile.enabled,
      autoSyncEnabled: profile.autoSyncEnabled,
      syncIntervalMinutes: profile.syncIntervalMinutes,
      defaultSceneType: profile.defaultSceneType || 'QSO',
      conflictStrategy: profile.conflictStrategy || 'SKIP',
      maxRecordsPerSync: profile.maxRecordsPerSync,
    }
    credentialAccount.value = profile.accountName
    credentialPassword.value = ''
  }
  if (withJobs) {
    void loadJobs()
  }
}

const startNewProfile = () => {
  currentProfileName.value = ''
  confirmingDelete.value = false
  expandedJobName.value = ''
  testSamples.value = []
  form.value = createEmptyForm()
  credentialAccount.value = ''
  credentialPassword.value = ''
  jobs.value = []
  feedback.value = '已切换为新建配置，填写后点击「保存配置」。'
}

const toPayload = (): LogSyncProfilePayload => ({
  sourceType: form.value.sourceType,
  myCallSign: form.value.myCallSign.trim().toUpperCase(),
  // 账号只在凭据卡填写；配置里跟随它，避免保存配置时把已存账号清空。
  accountName: (credentialAccount.value.trim() || currentProfile.value?.accountName || '').trim(),
  enabled: form.value.enabled,
  autoSyncEnabled: form.value.autoSyncEnabled,
  syncIntervalMinutes: form.value.syncIntervalMinutes,
  defaultSceneType: form.value.defaultSceneType,
  conflictStrategy: form.value.conflictStrategy,
  maxRecordsPerSync: form.value.maxRecordsPerSync,
})

const saveProfile = async () => {
  if (!form.value.myCallSign.trim()) {
    feedback.value = '本台呼号不能为空。'
    return
  }
  saving.value = true
  try {
    const saved = await saveLogSyncProfile(toPayload())
    feedback.value = `已保存配置：${saved.name}`
    currentProfileName.value = saved.name
    await loadAll()
  } catch (error) {
    feedback.value = `保存配置失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    saving.value = false
  }
}

const saveCredential = async () => {
  if (!currentProfileName.value) {
    feedback.value = '请先保存配置，再填写账号密码。'
    return
  }
  if (!credentialAccount.value.trim()) {
    feedback.value = '账号不能为空。'
    return
  }
  if (!credentialPassword.value && !currentProfile.value?.credentialPasswordSaved) {
    feedback.value = currentIsQrz.value
      ? 'QRZ Logbook 不需要密码，填好 Key 后直接保存即可。'
      : '首次保存凭据必须填写密码。'
    return
  }
  submitting.value = true
  try {
    await saveLogSyncCredential(currentProfileName.value, {
      accountName: credentialAccount.value.trim(),
      password: credentialPassword.value,
    })
    credentialPassword.value = ''
    feedback.value = '凭据已保存到 Halo Secret，页面不再回显密码。'
    await loadAll()
  } catch (error) {
    feedback.value = `保存凭据失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    submitting.value = false
  }
}

const clearCredential = async () => {
  if (!currentProfileName.value) {
    return
  }
  submitting.value = true
  try {
    await clearLogSyncCredential(currentProfileName.value)
    feedback.value = '已清除该配置保存的凭据。'
    await loadAll()
  } catch (error) {
    feedback.value = `清除凭据失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    submitting.value = false
  }
}

const requestDelete = () => {
  confirmingDelete.value = true
  feedback.value = ''
}

const cancelDelete = () => {
  confirmingDelete.value = false
}

const confirmDelete = async () => {
  if (!currentProfileName.value) {
    return
  }
  deleting.value = true
  try {
    await deleteLogSyncProfile(currentProfileName.value)
    const deletedName = currentProfileName.value
    currentProfileName.value = ''
    confirmingDelete.value = false
    jobs.value = []
    credentialAccount.value = ''
    credentialPassword.value = ''
    form.value = createEmptyForm()
    await loadAll()
    feedback.value = `已删除配置：${deletedName}（凭据与历史作业一并删除）`
  } catch (error) {
    feedback.value = `删除配置失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    deleting.value = false
  }
}

/**
 * 强制停止卡住的作业。
 *
 * <p>作业停在 RUNNING 时「重置游标」「删除配置」都会被服务端的并发保护挡住，
 * 这个按钮是用户唯一的自救入口，不必等十分钟的陈旧判定或重启插件。
 */
const forceStop = async () => {
  if (!currentProfileName.value) {
    return
  }
  stopping.value = true
  try {
    const job = await forceStopLogSync(currentProfileName.value)
    feedback.value = job.message || '已强制停止进行中的作业，可以重新发起同步。'
    await loadAll()
    await loadJobs()
  } catch (error) {
    feedback.value = `强制停止失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    stopping.value = false
  }
}

const resetCursor = async () => {
  if (!currentProfileName.value) {
    feedback.value = '请先选择配置。'
    return
  }
  resettingCursor.value = true
  try {
    await resetLogSyncCursor(currentProfileName.value)
    await loadAll()
    feedback.value = '已重置同步游标，下一次「立即同步」会按全量重新拉取（不会删除已同步的记录）。'
  } catch (error) {
    feedback.value = `重置游标失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    resettingCursor.value = false
  }
}

const testConnection = async () => {
  if (!currentProfileName.value) {
    feedback.value = '请先保存配置并填写凭据。'
    return
  }
  testing.value = true
  try {
    if (!(await persistPendingCredential())) {
      return
    }
    const result = await testLogSyncConnection(currentProfileName.value)
    testSamples.value = result.ok ? (result.records ?? []) : []
    feedback.value = result.ok
      ? `连接成功，试拉取到 ${result.recordCount} 条记录（${formatTime(result.testedAt)}）。`
      : `连接失败：${result.message}`
  } catch (error) {
    feedback.value = `连接测试失败：${getConsoleApiErrorMessage(error)}`
  } finally {
    testing.value = false
  }
}

/**
 * 轮询作业表直到没有进行中的作业。
 *
 * <p>同步在服务端后台执行（避免请求被取消导致作业永远停在 RUNNING），因此这里
 * 不等接口返回统计值，而是反复刷新作业表；最多轮询约两分钟，超时后提示手动刷新。
 */
const watchRunningJobs = (attempt = 0) => {
  if (jobPollTimer) {
    clearTimeout(jobPollTimer)
    jobPollTimer = null
  }
  jobPollTimer = setTimeout(async () => {
    await loadJobs()
    await loadAll()
    const stillRunning = jobs.value.some((job) => job.status === 'RUNNING')
    if (stillRunning && attempt < 40) {
      watchRunningJobs(attempt + 1)
      return
    }
    if (stillRunning) {
      feedback.value = '同步仍在后台执行，可稍后点「刷新历史」查看结果。'
    }
  }, 3000)
}

const syncNow = async () => {
  if (!currentProfileName.value) {
    feedback.value = '请先保存配置并填写凭据。'
    return
  }
  syncing.value = true
  try {
    if (!(await persistPendingCredential())) {
      return
    }
    const job = await runLogSync(currentProfileName.value)
    feedback.value = job.message || '任务已启动，完成后作业表会自动刷新。'
    await loadAll()
    await loadJobs()
    watchRunningJobs()
  } catch (error) {
    // 同步失败的具体原因已写回作业与配置，这里仍把响应里的说明露出来。
    feedback.value = `同步失败：${getConsoleApiErrorMessage(error)}`
    await loadAll()
    await loadJobs()
  } finally {
    syncing.value = false
  }
}

const loadJobs = async () => {
  if (!currentProfileName.value) {
    jobs.value = []
    return
  }
  try {
    jobs.value = await listLogSyncJobs(currentProfileName.value)
  } catch (error) {
    feedback.value = `加载同步历史失败：${getConsoleApiErrorMessage(error)}`
  }
}

onUnmounted(() => {
  if (jobPollTimer) {
    clearTimeout(jobPollTimer)
    jobPollTimer = null
  }
})

onMounted(loadAll)
</script>

<template>
  <div class="qsl-page">
    <div class="qsl-page__content">
      <VCard title="同步配置">
        <div class="qsl-tab-row">
          <VButton
            v-for="profile in profiles"
            :key="profile.name"
            :type="currentProfileName === profile.name ? 'secondary' : 'default'"
            size="sm"
            @click="selectProfile(profile.name)"
          >
            {{ sourceLabel(profile.sourceType) }} · {{ profile.myCallSign }}
          </VButton>
          <VButton size="sm" :disabled="loading" @click="startNewProfile">新建配置</VButton>
        </div>

        <div class="log-sync-form">
          <label class="qsl-field">
            <span class="qsl-field__label">数据源（LoTW 或 QRZ Logbook）</span>
            <div class="qsl-input-shell">
              <select v-model="form.sourceType">
                <option v-for="source in sources" :key="source.sourceType" :value="source.sourceType">
                  {{ source.displayName }}
                </option>
              </select>
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">本台呼号（用于匹配要拉取的记录）</span>
            <div class="qsl-input-shell">
              <input v-model.trim="form.myCallSign" type="text" placeholder="BI1KBU" />
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">默认场景（QSO 通联，SWL 收听）</span>
            <div class="qsl-input-shell">
              <select v-model="form.defaultSceneType">
                <option value="QSO">QSO（通联）</option>
                <option value="SWL">SWL（收听）</option>
              </select>
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">重复记录处理（判重键命中时）</span>
            <div class="qsl-input-shell">
              <select v-model="form.conflictStrategy">
                <option value="SKIP">保留已有，跳过重复</option>
                <option value="OVERWRITE">覆盖已有记录</option>
              </select>
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">单次最多拉取条数（默认 5000）</span>
            <div class="qsl-input-shell">
              <input v-model.number="form.maxRecordsPerSync" type="number" min="1" max="20000" />
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">自动同步间隔（分钟，最短 60）</span>
            <div class="qsl-input-shell">
              <input v-model.number="form.syncIntervalMinutes" type="number" min="60" />
            </div>
          </label>
        </div>

        <div class="log-sync-switches">
          <div class="qsl-switch-row">
            <div>
              <p class="qsl-switch-row__title">启用该配置</p>
              <p class="qsl-switch-row__desc">关闭后不参与任何同步，历史记录保留。</p>
            </div>
            <label class="qsl-checkbox">
              <input v-model="form.enabled" type="checkbox" />
              <span>启用</span>
            </label>
          </div>
          <div class="qsl-switch-row">
            <div>
              <p class="qsl-switch-row__title">启用自动同步</p>
              <p class="qsl-switch-row__desc">
                每分钟检查一次，已启用且距上次同步超过间隔时才拉取；从未同步过的配置保存后立即执行一次。
              </p>
            </div>
            <label class="qsl-checkbox">
              <input v-model="form.autoSyncEnabled" type="checkbox" />
              <span>自动同步</span>
            </label>
          </div>
        </div>

        <div v-if="currentProfile" class="log-sync-summary">
          <div class="log-sync-summary__item">
            <span class="qsl-field__label">最近同步（lastSyncAt）</span>
            <span>{{ formatTime(currentProfile.lastSyncAt) }}</span>
          </div>
          <div class="log-sync-summary__item">
            <span class="qsl-field__label">数据游标（lastCursor）</span>
            <span class="log-sync-cursor">{{ currentProfile.lastCursor || '无（将从最早记录开始）' }}</span>
          </div>
          <div class="log-sync-summary__item">
            <span class="qsl-field__label">凭据（credential）</span>
            <span class="log-sync-badges">
              <VTag :theme="currentProfile.credentialAccountSaved ? 'primary' : 'danger'">
                账号{{ currentProfile.credentialAccountSaved ? '已保存' : '未保存' }}
              </VTag>
              <VTag :theme="currentProfile.credentialPasswordSaved ? 'primary' : 'secondary'">
                {{
                  currentProfile.credentialPasswordRequired
                    ? `密码${currentProfile.credentialPasswordSaved ? '已保存' : '未保存'}`
                    : '无需密码'
                }}
              </VTag>
            </span>
          </div>
          <div class="log-sync-summary__item">
            <span class="qsl-field__label">下次自动同步（nextRun）</span>
            <span>{{ nextSyncText }}</span>
          </div>
        </div>

        <p v-if="currentSourceNote" class="qsl-field__tip">{{ currentSourceNote }}</p>
        <p v-if="currentProfile && !currentProfile.credentialConfigured" class="log-sync-warning">
          当前配置的凭据还不完整，同步会直接失败。{{ credentialTip }}
        </p>
        <p v-if="currentProfile && currentProfile.lastError" class="log-sync-error">
          最近一次错误：{{ currentProfile.lastError }}
        </p>

        <div class="log-sync-actions">
          <VButton type="secondary" :disabled="saving || loading" @click="saveProfile">
            保存配置
          </VButton>
          <VButton :disabled="loading" @click="loadAll">刷新</VButton>
          <template v-if="currentProfileName">
            <template v-if="confirmingDelete">
              <span class="log-sync-error">确认删除该配置？凭据与历史作业会一并删除。</span>
              <VButton type="danger" :disabled="deleting" @click="confirmDelete">
                确认删除
              </VButton>
              <VButton :disabled="deleting" @click="cancelDelete">取消</VButton>
            </template>
            <VButton v-else type="danger" class="log-sync-actions__right" @click="requestDelete">
              删除配置
            </VButton>
          </template>
        </div>
      </VCard>

      <VCard title="账号凭据">
        <p class="qsl-field__tip">
          凭据只保存在 Halo Secret 中：不写入配置、不写入导出包、不写进审计日志，页面也不回显明文。
          {{ credentialTip }}
        </p>
        <div class="log-sync-form log-sync-form--narrow">
          <label class="qsl-field">
            <span class="qsl-field__label">账号（LoTW 用户名 / QRZ Key）</span>
            <div class="qsl-input-shell">
              <input v-model.trim="credentialAccount" type="text" placeholder="登录账号" />
            </div>
          </label>
          <label class="qsl-field">
            <span class="qsl-field__label">密码（QRZ 可留空）</span>
            <div class="qsl-input-shell">
              <input
                v-model="credentialPassword"
                type="password"
                autocomplete="new-password"
                placeholder="首次必须填写；之后留空表示不修改"
              />
            </div>
          </label>
        </div>
        <div class="log-sync-actions">
          <VButton
            type="secondary"
            :disabled="submitting || !currentProfileName"
            @click="saveCredential"
          >
            保存凭据
          </VButton>
          <VButton
            type="danger"
            :disabled="submitting || !currentProfileName"
            @click="clearCredential"
          >
            清除凭据
          </VButton>
        </div>
      </VCard>

      <VCard title="同步操作">
        <div class="qsl-query-toolbar">
          <div class="qsl-query-toolbar__left">
            <span class="qsl-field__tip">
              同步按「场景 + 呼号 + 日期 + 时间 + 时区 + 模式 + 频率」判重，重复记录按上面的策略处理。
            </span>
          </div>
          <div class="qsl-query-toolbar__right">
            <VButton
              v-if="hasRunningJob"
              type="danger"
              :disabled="stopping || !currentProfileName"
              @click="forceStop"
            >
              强制停止
            </VButton>
            <VButton :disabled="resettingCursor || !currentProfileName" @click="resetCursor">
              重置游标
            </VButton>
            <VButton :disabled="!currentProfileName" @click="loadJobs">刷新历史</VButton>
            <VButton :disabled="testing || !currentProfileName" @click="testConnection">
              测试连接
            </VButton>
            <VButton type="secondary" :disabled="syncing || !currentProfileName" @click="syncNow">
              立即同步
            </VButton>
          </div>
        </div>

        <p v-if="feedback" class="qsl-feedback">{{ feedback }}</p>

        <div v-if="testSamples.length > 0" class="log-sync-samples">
          <p class="qsl-field__tip">
            上游返回的记录样例（最多 20 条，用于确认拉到的到底是谁的记录）：
          </p>
          <ul class="qsl-meta-list">
            <li v-for="(sample, index) in testSamples" :key="index">{{ sample }}</li>
          </ul>
        </div>

        <div class="qsl-table-wrap">
          <table class="qsl-table">
            <thead>
              <tr>
                <th>开始时间（startedAt）</th>
                <th>结果（status）</th>
                <th>拉取（fetched）</th>
                <th>新建（created）</th>
                <th>更新（updated）</th>
                <th>跳过（skipped）</th>
                <th>失败（failed）</th>
                <th>耗时（duration）</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="job in jobs" :key="job.name">
                <tr class="qsl-table-clickable-row" @click="toggleJobDetail(job.name)">
                  <td>{{ formatTime(job.startedAt) }}</td>
                  <td>
                    <VTag :theme="jobResultTheme(job.status)" rounded>
                      {{ jobResultLabel(job.status) }}
                    </VTag>
                    <span v-if="job.message" class="log-sync-job-message">{{ job.message }}</span>
                  </td>
                  <td>{{ job.fetchedCount ?? 0 }}</td>
                  <td>{{ job.createdCount ?? 0 }}</td>
                  <td>{{ job.updatedCount ?? 0 }}</td>
                  <td>{{ job.skippedCount ?? 0 }}</td>
                  <td :class="{ 'log-sync-error': (job.failedCount ?? 0) > 0 }">
                    {{ job.failedCount ?? 0 }}
                  </td>
                  <td>{{ jobDuration(job) || '—' }}</td>
                </tr>
                <tr v-if="expandedJobName === job.name" class="qsl-table-detail-row">
                  <td colspan="8">
                    <p class="qsl-field__tip">
                      作业（job）：{{ job.name }} ｜ 结束时间（finishedAt）：{{
                        formatTime(job.finishedAt)
                      }}
                    </p>
                    <p class="qsl-field__tip">状态说明（message）：{{ job.message || '无' }}</p>
                    <div v-if="(job.errorLines ?? []).length > 0" class="qsl-meta-list">
                      <p class="qsl-muted">失败明细（最多 200 行）：</p>
                      <ul>
                        <li v-for="(line, index) in job.errorLines" :key="index">{{ line }}</li>
                      </ul>
                    </div>
                    <p v-else class="qsl-muted">该作业没有逐条失败明细。</p>
                  </td>
                </tr>
              </template>
              <tr v-if="jobs.length === 0">
                <td colspan="8" class="qsl-table-empty">暂无同步历史</td>
              </tr>
            </tbody>
          </table>
        </div>
      </VCard>
    </div>
  </div>
</template>

<style scoped>
.log-sync-form {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  max-width: 1000px;
}

.log-sync-form--narrow {
  max-width: 640px;
}

.log-sync-switches {
  margin-top: 12px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.log-sync-summary {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f9fafb;
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.log-sync-summary__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #374151;
}

.log-sync-cursor {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}

.log-sync-badges {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.log-sync-actions {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.log-sync-actions__right {
  margin-left: auto;
}

.log-sync-warning {
  margin: 8px 0 0;
  color: #b45309;
  font-size: 13px;
  line-height: 20px;
}

.log-sync-error {
  margin: 8px 0 0;
  color: #b91c1c;
  font-size: 13px;
  line-height: 20px;
}

.log-sync-samples {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f9fafb;
}

.log-sync-job-message {
  display: block;
  margin-top: 4px;
  color: #b91c1c;
  font-size: 12px;
  line-height: 18px;
}
</style>
