import type { IssueItem, TaskStatus } from '../types/workbench'

const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://127.0.0.1:8080'

interface BackendProgressEvent {
  stage: string
  percentage: number
}

interface BackendIssue {
  ruleId: string
  message: string
  filePath: string
  line: number
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
}

interface BackendTaskSnapshot {
  taskId: string
  traceId: string
  workspaceId: string
  status: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
  issueCount: number
  durationMillis: number
  failureReason?: string
  failureCategory?: string
  attempt?: number
  maxRetries?: number
  events: BackendProgressEvent[]
  issues?: BackendIssue[]
}

export interface UploadProjectResponse {
  uploadId: string
  projectPath: string
  fileName: string
}

export interface RulePackImportResponse {
  success: boolean
  packId: string
  version: string
  ruleCount: number
  message: string
  errorCode?: string
}

export interface InstalledRulePackView {
  packId: string
  version: string
  language: string
  checksum: string
  installedAt: string
  ruleCount: number
}

export interface SubmitTaskRequestPayload {
  workspaceId: string
  projectPath: string
}

export interface AnalysisTaskView {
  id: string
  workspaceId: string
  traceId: string
  rulepackVersion: string
  status: TaskStatus
  progress: number
  startedAt: string
  finishedAt?: string
  failureReason?: string
  failureCategory?: string
  attempt?: number
  maxRetries?: number
  issues: IssueItem[]
}

export interface TaskDiagnosticsView {
  recentTasks: Array<{
    taskId: string
    status: TaskStatus
    durationMillis: number
    issueCount: number
    finishedAt: string
  }>
  failureTopReasons: Array<{
    reason: string
    count: number
  }>
  durationDistributions: Array<{
    bucket: string
    count: number
  }>
  ruleHitTop: Array<{
    ruleId: string
    hits: number
  }>
}

export interface PluginHealthView {
  language: string
  pluginId: string
  status: string
  implemented: boolean
  compatible: boolean
  supportsAutofix: boolean
  message: string
}

export interface SecurityAuditEventView {
  taskId?: string
  rulePackId?: string
  userAction: string
  timestamp: string
  success: boolean
  message: string
  metadata: Record<string, string>
}

export interface ContextHintRequestPayload {
  projectPath: string
  targetFiles?: string[]
  maxFiles?: number
  maxMethodsPerFile?: number
}

export interface ProjectContextSummaryView {
  fileCount: number
  classCount: number
  methodCount: number
  loopCount: number
  branchCount: number
}

export interface ClassContextSummaryView {
  className: string
  methodCount: number
  fieldCount: number
  constructorCount: number
}

export interface MethodContextSummaryView {
  methodName: string
  statementCount: number
  loopCount: number
  branchCount: number
  callCount: number
  synchronizedMethod: boolean
}

export interface FileContextSummaryView {
  filePath: string
  classCount: number
  methodCount: number
  loopCount: number
  branchCount: number
  classes: ClassContextSummaryView[]
  methods: MethodContextSummaryView[]
}

export interface JitHintView {
  hintId: string
  level: string
  filePath: string
  className?: string
  methodName?: string
  message: string
  recommendation: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string
}

export interface ContextHintResponseView {
  projectSummary: ProjectContextSummaryView
  fileSummaries: FileContextSummaryView[]
  jitHints: JitHintView[]
}

function normalizeStatus(input: string): TaskStatus {
  const lowered = input.toLowerCase()
  if (lowered === 'created' || lowered === 'queued' || lowered === 'running' || lowered === 'cancelled' || lowered === 'failed' || lowered === 'completed' || lowered === 'archived') {
    return lowered
  }
  return 'failed'
}

function normalizeSeverity(input: BackendIssue['severity']): IssueItem['severity'] {
  const lowered = input.toLowerCase()
  if (lowered === 'critical' || lowered === 'high' || lowered === 'medium' || lowered === 'low') {
    return lowered
  }
  return 'low'
}

function toIssue(taskId: string, workspaceId: string, issue: BackendIssue, index: number): IssueItem {
  const filePath =
    typeof issue.filePath === 'string' && issue.filePath.length > 0 ? issue.filePath : 'unknown'
  const ruleId = issue.ruleId || `RULE-${index + 1}`
  const message = issue.message || '未提供问题描述'
  return {
    id: `${taskId}-${index + 1}`,
    taskId,
    workspaceId,
    ruleId,
    ruleName: ruleId,
    severity: normalizeSeverity(issue.severity),
    filePath,
    line: issue.line ?? 1,
    column: 1,
    message,
    recommendation: '请根据规则提示修复后重新扫描验证。',
    fixPreview: {
      before: message,
      after: '修复建议请查看规则文档或结合代码上下文处理。',
    },
    createdAt: new Date().toISOString(),
  }
}

function toTaskView(snapshot: BackendTaskSnapshot): AnalysisTaskView {
  const status = normalizeStatus(snapshot.status)
  const maxProgress = snapshot.events.reduce((acc, event) => Math.max(acc, event.percentage ?? 0), 0)
  const progress = status === 'completed' ? 100 : Math.min(99, Math.max(0, maxProgress))
  const startedAt = snapshot.startedAt ?? snapshot.createdAt
  const issues = (snapshot.issues ?? []).map((item, index) =>
    toIssue(snapshot.taskId, snapshot.workspaceId, item, index),
  )

  return {
    id: snapshot.taskId,
    workspaceId: snapshot.workspaceId,
    traceId: snapshot.traceId,
    rulepackVersion: 'backend-live',
    status,
    progress,
    startedAt,
    finishedAt: snapshot.finishedAt,
    failureReason: snapshot.failureReason,
    failureCategory: snapshot.failureCategory,
    attempt: snapshot.attempt,
    maxRetries: snapshot.maxRetries,
    issues,
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`${response.status} ${response.statusText}: ${text}`)
  }
  return (await response.json()) as T
}

export async function uploadProjectFile(file: File): Promise<UploadProjectResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${API_BASE_URL}/uploads`, {
    method: 'POST',
    body: formData,
  })
  if (!response.ok) {
    const text = await response.text()
    const lowerName = file.name.toLowerCase()
    const selectedSupportedType =
      lowerName.endsWith('.java') || lowerName.endsWith('.jar') || lowerName.endsWith('.zip')
    if (response.status === 400 && selectedSupportedType) {
      throw new Error(
        `上传失败：当前后端可能还是旧版本（仅支持 zip）。请重启后端后重试。原始响应：${text}`,
      )
    }
    throw new Error(`${response.status} ${response.statusText}: ${text}`)
  }
  return (await response.json()) as UploadProjectResponse
}

export async function importCustomRulePack(payload: {
  packageFile: File
  environment: 'DEV' | 'PROD'
  manifestFile?: File
  manifestJson?: string
}): Promise<RulePackImportResponse> {
  const formData = new FormData()
  formData.append('packageFile', payload.packageFile)
  formData.append('environment', payload.environment)
  if (payload.manifestFile) {
    formData.append('manifestFile', payload.manifestFile)
  } else if (payload.manifestJson && payload.manifestJson.trim().length > 0) {
    formData.append('manifestJson', payload.manifestJson)
  }
  const response = await fetch(`${API_BASE_URL}/rulepacks/import`, {
    method: 'POST',
    body: formData,
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${text}`)
  }
  return JSON.parse(text) as RulePackImportResponse
}

export async function getInstalledRulePacks(): Promise<InstalledRulePackView[]> {
  return requestJson<InstalledRulePackView[]>('/rulepacks/installed')
}

export async function submitAnalysisTask(payload: SubmitTaskRequestPayload): Promise<AnalysisTaskView> {
  const snapshot = await requestJson<BackendTaskSnapshot>('/tasks', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      workspaceId: payload.workspaceId,
      projectPath: payload.projectPath,
      mode: 'FULL',
    }),
  })
  return toTaskView(snapshot)
}

export async function getTask(taskId: string): Promise<AnalysisTaskView> {
  const snapshot = await requestJson<BackendTaskSnapshot>(`/tasks/${encodeURIComponent(taskId)}`)
  return toTaskView(snapshot)
}

export async function cancelTask(taskId: string): Promise<{ cancelled: boolean; taskId: string }> {
  return requestJson<{ cancelled: boolean; taskId: string }>(
    `/tasks/${encodeURIComponent(taskId)}/cancel`,
    {
      method: 'POST',
    },
  )
}

export async function retryTask(taskId: string): Promise<AnalysisTaskView> {
  const snapshot = await requestJson<BackendTaskSnapshot>(
    `/tasks/${encodeURIComponent(taskId)}/retry`,
    {
      method: 'POST',
    },
  )
  return toTaskView(snapshot)
}

export async function getTaskDiagnostics(): Promise<TaskDiagnosticsView> {
  const raw = await requestJson<{
    recentTasks: Array<{
      taskId: string
      status: string
      durationMillis: number
      issueCount: number
      finishedAt: string
    }>
    failureTopReasons: Array<{ reason: string; count: number }>
    durationDistributions: Array<{ bucket: string; count: number }>
    ruleHitTop: Array<{ ruleId: string; hits: number }>
  }>('/tasks/diagnostics')

  return {
    recentTasks: raw.recentTasks.map((item) => ({
      ...item,
      status: normalizeStatus(item.status),
    })),
    failureTopReasons: raw.failureTopReasons,
    durationDistributions: raw.durationDistributions,
    ruleHitTop: raw.ruleHitTop,
  }
}

export async function getPluginHealth(): Promise<PluginHealthView[]> {
  return requestJson<PluginHealthView[]>('/tasks/plugins/health')
}

export async function getSecurityAuditEvents(limit = 50): Promise<SecurityAuditEventView[]> {
  return requestJson<SecurityAuditEventView[]>(
    `/security/audit/events?limit=${encodeURIComponent(String(limit))}`,
  )
}

export async function recordFixApplyAudit(payload: {
  taskId: string
  rulePackId: string
  fixId: string
  operator: string
}): Promise<{ accepted: boolean; taskId: string; fixId: string }> {
  return requestJson<{ accepted: boolean; taskId: string; fixId: string }>(
    '/security/audit/fix-apply',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        taskId: payload.taskId,
        rulePackId: payload.rulePackId,
        fixId: payload.fixId,
        operator: payload.operator,
        confirmed: true,
      }),
    },
  )
}

export async function getJitContextHints(
  payload: ContextHintRequestPayload,
): Promise<ContextHintResponseView> {
  return requestJson<ContextHintResponseView>('/analysis/hints/jit-context', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      projectPath: payload.projectPath,
      targetFiles: payload.targetFiles,
      maxFiles: payload.maxFiles,
      maxMethodsPerFile: payload.maxMethodsPerFile,
    }),
  })
}
