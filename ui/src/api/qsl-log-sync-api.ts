import { axiosInstance } from '@halo-dev/api-client'
import { getConsoleApiErrorMessage } from './qsl-console-api'

interface ApiResult<T> {
  code: string
  message: string
  data: T
}

const consoleApiBase = '/apis/console.api.qsl-management.bi1kbu.com/v1alpha1'

export type LogSyncSourceType = 'LOTW' | 'QRZ_LOGBOOK'

export interface LogSyncSourceDescriptor {
  sourceType: string
  displayName: string
  requiresCredential: boolean
  note: string
}

export interface LogSyncProfile {
  name: string
  sourceType: string
  myCallSign: string
  accountName: string
  credentialConfigured: boolean
  credentialAccountSaved: boolean
  credentialPasswordSaved: boolean
  credentialPasswordRequired: boolean
  credentialSecretName: string
  enabled: boolean
  autoSyncEnabled: boolean
  syncIntervalMinutes: number | null
  defaultSceneType: string
  conflictStrategy: string
  maxRecordsPerSync: number | null
  lastSyncAt: string
  lastCursor: string
  lastError: string
  consecutiveFailures: number | null
}

export interface LogSyncProfilePayload {
  sourceType: string
  myCallSign: string
  accountName: string
  enabled: boolean
  autoSyncEnabled: boolean
  syncIntervalMinutes: number | null
  defaultSceneType: string
  conflictStrategy: string
  maxRecordsPerSync: number | null
}

export interface LogSyncCredentialPayload {
  accountName: string
  password: string
}

export interface LogSyncTestResult {
  ok: boolean
  message: string
  recordCount: number
  testedAt: string
  records: string[]
}

export interface LogSyncJob {
  name: string
  status: string
  message: string
  fetchedCount: number | null
  createdCount: number | null
  updatedCount: number | null
  skippedCount: number | null
  failedCount: number | null
  startedAt: string
  finishedAt: string
  errorLines: string[]
}

export const listLogSyncSources = async (): Promise<LogSyncSourceDescriptor[]> => {
  const response = await axiosInstance.get<ApiResult<LogSyncSourceDescriptor[]>>(
    `${consoleApiBase}/log-sync/sources`,
  )
  return response.data.data
}

export const listLogSyncProfiles = async (): Promise<LogSyncProfile[]> => {
  const response = await axiosInstance.get<ApiResult<LogSyncProfile[]>>(
    `${consoleApiBase}/log-sync/profiles`,
  )
  return response.data.data
}

export const saveLogSyncProfile = async (
  payload: LogSyncProfilePayload,
): Promise<LogSyncProfile> => {
  const response = await axiosInstance.post<ApiResult<LogSyncProfile>>(
    `${consoleApiBase}/log-sync/profiles`,
    payload,
  )
  return response.data.data
}

export const saveLogSyncCredential = async (
  profileName: string,
  payload: LogSyncCredentialPayload,
): Promise<LogSyncProfile> => {
  const response = await axiosInstance.post<ApiResult<LogSyncProfile>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/credential`,
    payload,
  )
  return response.data.data
}

export const clearLogSyncCredential = async (profileName: string): Promise<LogSyncProfile> => {
  const response = await axiosInstance.post<ApiResult<LogSyncProfile>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/credential/clear`,
  )
  return response.data.data
}

/** 强制停止该配置下卡住的同步作业（作业停在 RUNNING 时会挡住重置游标与删除）。 */
export const forceStopLogSync = async (profileName: string): Promise<LogSyncJob> => {
  const response = await axiosInstance.post<ApiResult<LogSyncJob>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/force-stop`,
  )
  return response.data.data
}

export const resetLogSyncCursor = async (profileName: string): Promise<LogSyncProfile> => {
  const response = await axiosInstance.post<ApiResult<LogSyncProfile>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/reset-cursor`,
  )
  return response.data.data
}

export const testLogSyncConnection = async (profileName: string): Promise<LogSyncTestResult> => {
  const response = await axiosInstance.post<ApiResult<LogSyncTestResult>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/test`,
  )
  return response.data.data
}

export const runLogSync = async (profileName: string): Promise<LogSyncJob> => {
  const response = await axiosInstance.post<ApiResult<LogSyncJob>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/sync`,
  )
  return response.data.data
}

export const deleteLogSyncProfile = async (profileName: string): Promise<void> => {
  await axiosInstance.delete<ApiResult<boolean>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}`,
  )
}

export const listLogSyncJobs = async (profileName: string): Promise<LogSyncJob[]> => {
  const response = await axiosInstance.get<ApiResult<LogSyncJob[]>>(
    `${consoleApiBase}/log-sync/profiles/${encodeURIComponent(profileName)}/jobs`,
  )
  return response.data.data
}

export { getConsoleApiErrorMessage }
