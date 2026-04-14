import Dexie, { type Collection, type Table } from 'dexie'
import type {
  AnalysisTask,
  IssueItem,
  RulepackInfo,
  Severity,
  WorkspaceSummary,
} from '../types/workbench'

const DB_NAME = 'optimizer-workbench'
const EMPTY_WORKSPACE_FALLBACK = 'workspace-unknown'

type RulepackIndexRecord = {
  id: string
  workspaceId: string
  rulepackId: string
  name: string
  version: string
  enabled: boolean
  importedAt: string
}

type TaskRecord = AnalysisTask & {
  archivedAt?: string
  reportFilePath?: string
  reportSizeBytes?: number
  reportPrunedAt?: string
}

type IssueIndexRecord = IssueItem & {
  searchableText: string
}

export interface IssueQuery {
  workspaceId: string
  taskId?: string
  severity?: Severity | 'all'
  ruleId?: string
  filePath?: string
  keyword?: string
  page: number
  pageSize: number
}

export interface PagedIssues {
  rows: IssueItem[]
  total: number
}

export interface WorkbenchSeedPayload {
  workspace: WorkspaceSummary
  tasks: AnalysisTask[]
  issues: IssueItem[]
  rulepacks: RulepackInfo[]
  reports?: Array<{
    taskId: string
    reportFilePath: string
    reportSizeBytes: number
  }>
}

export interface TaskHistoryQuery {
  workspaceId: string
  page: number
  pageSize: number
  includeArchived?: boolean
}

export interface PagedTasks {
  rows: TaskRecord[]
  total: number
}

export interface LifecyclePolicy {
  archiveTaskOlderThanDays: number
  maxReportBytesPerWorkspace: number
}

export interface LifecycleResult {
  archivedTaskCount: number
  prunedReportCount: number
  plannedFileDeletes: string[]
}

class OptimizerWorkbenchDb extends Dexie {
  workspaces!: Table<WorkspaceSummary, string>
  tasks!: Table<TaskRecord, string>
  issuesIndex!: Table<IssueIndexRecord, string>
  rulepacks!: Table<RulepackIndexRecord, string>

  constructor() {
    super(DB_NAME)

    this.version(1).stores({
      workspaces: '&id,name,importedAt',
      tasks: '&id,workspaceId,status,startedAt,finishedAt,[workspaceId+status]',
      issuesIndex:
        '&id,workspaceId,taskId,ruleId,severity,filePath,createdAt,[workspaceId+severity],[workspaceId+ruleId],[workspaceId+filePath]',
      rulepacks: '&id,workspaceId,rulepackId,importedAt,[workspaceId+id]',
    })

    // Reserve schema upgrade hook for future breaking changes.
    this.version(2)
      .stores({
        workspaces: '&id,name,importedAt',
        tasks:
          '&id,workspaceId,status,startedAt,finishedAt,archivedAt,reportPrunedAt,[workspaceId+status],[workspaceId+archivedAt]',
        issuesIndex:
          '&id,workspaceId,taskId,ruleId,severity,filePath,createdAt,[workspaceId+severity],[workspaceId+ruleId],[workspaceId+filePath]',
        rulepacks: '&id,workspaceId,rulepackId,importedAt,[workspaceId+id]',
      })
      .upgrade(async () => {
        // No data rewrite is required for the current v1 -> v2 migration path.
      })
  }
}

const db = new OptimizerWorkbenchDb()

function buildSearchableText(issue: IssueItem) {
  return `${issue.ruleName} ${issue.filePath} ${issue.message} ${issue.recommendation}`.toLowerCase()
}

function mapRulepackRecord(workspaceId: string, pack: RulepackInfo): RulepackIndexRecord {
  return {
    id: `${workspaceId}:${pack.id}`,
    workspaceId,
    rulepackId: pack.id,
    name: pack.name,
    version: pack.version,
    enabled: pack.enabled,
    importedAt: pack.importedAt,
  }
}

function sortByCreatedAtDesc(a: IssueItem, b: IssueItem) {
  return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
}

function sortByStartedAtDesc(a: TaskRecord, b: TaskRecord) {
  return new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
}

function toPaged<T>(rows: T[], page: number, pageSize: number) {
  const safePage = Math.max(1, page)
  const safePageSize = Math.max(1, pageSize)
  const start = (safePage - 1) * safePageSize
  return rows.slice(start, start + safePageSize)
}

export async function seedWorkbenchStorage(payload: WorkbenchSeedPayload) {
  const reportMap = new Map(
    (payload.reports ?? []).map((report) => [report.taskId, report]),
  )

  await db.transaction(
    'rw',
    db.workspaces,
    db.tasks,
    db.issuesIndex,
    db.rulepacks,
    async () => {
      await db.workspaces.put(payload.workspace)
      await db.tasks.bulkPut(
        payload.tasks.map((task) => {
          const report = reportMap.get(task.id)
          return {
            ...task,
            reportFilePath: report?.reportFilePath,
            reportSizeBytes: report?.reportSizeBytes,
          }
        }),
      )
      await db.issuesIndex.bulkPut(
        payload.issues.map((issue) => ({
          ...issue,
          searchableText: buildSearchableText(issue),
        })),
      )
      await db.rulepacks.bulkPut(
        payload.rulepacks.map((pack) => mapRulepackRecord(payload.workspace.id, pack)),
      )
    },
  )
}

export async function seedIssues(issues: IssueItem[]) {
  if (issues.length === 0) {
    return
  }
  await db.issuesIndex.bulkPut(
    issues.map((issue) => ({
      ...issue,
      searchableText: buildSearchableText(issue),
    })),
  )
}

function createIssueCollection(params: IssueQuery): Collection<IssueIndexRecord, string> {
  if (params.severity && params.severity !== 'all') {
    return db.issuesIndex
      .where('[workspaceId+severity]')
      .equals([params.workspaceId, params.severity])
  }
  if (params.ruleId) {
    return db.issuesIndex
      .where('[workspaceId+ruleId]')
      .equals([params.workspaceId, params.ruleId])
  }
  if (params.filePath) {
    return db.issuesIndex
      .where('[workspaceId+filePath]')
      .equals([params.workspaceId, params.filePath])
  }
  return db.issuesIndex.where('workspaceId').equals(params.workspaceId)
}

export async function queryIssues(params: IssueQuery): Promise<PagedIssues> {
  const keyword = params.keyword?.trim().toLowerCase()
  const rows = await createIssueCollection(params).toArray()

  const filtered = rows
    .filter((row) => (params.taskId ? row.taskId === params.taskId : true))
    .filter((row) => (keyword ? row.searchableText.includes(keyword) : true))
    .map(({ searchableText: _searchableText, ...issue }) => issue)
    .sort(sortByCreatedAtDesc)

  return {
    rows: toPaged(filtered, params.page, params.pageSize),
    total: filtered.length,
  }
}

export async function queryTaskHistory(params: TaskHistoryQuery): Promise<PagedTasks> {
  const rows = await db.tasks.where('workspaceId').equals(params.workspaceId).toArray()
  const filtered = params.includeArchived
    ? rows
    : rows.filter((task) => !task.archivedAt)
  const sorted = filtered.sort(sortByStartedAtDesc)
  return {
    rows: toPaged(sorted, params.page, params.pageSize),
    total: sorted.length,
  }
}

export async function applyDataLifecyclePolicy(
  workspaceId: string,
  policy: LifecyclePolicy,
): Promise<LifecycleResult> {
  const now = new Date().toISOString()
  const archiveBefore = Date.now() - policy.archiveTaskOlderThanDays * 24 * 60 * 60 * 1000
  const workspaceTasks = await db.tasks.where('workspaceId').equals(workspaceId).toArray()

  const archiveCandidates = workspaceTasks.filter((task) => {
    if (task.archivedAt || !task.finishedAt) {
      return false
    }
    return new Date(task.finishedAt).getTime() < archiveBefore
  })

  for (const task of archiveCandidates) {
    await db.tasks.update(task.id, { archivedAt: now })
  }

  const refreshedTasks = await db.tasks.where('workspaceId').equals(workspaceId).toArray()
  const reportCandidates = refreshedTasks
    .filter((task) => task.reportFilePath && !task.reportPrunedAt)
    .sort((left, right) => {
      const leftFinishedAt = left.finishedAt ?? left.startedAt
      const rightFinishedAt = right.finishedAt ?? right.startedAt
      return new Date(leftFinishedAt).getTime() - new Date(rightFinishedAt).getTime()
    })

  let totalReportBytes = reportCandidates.reduce(
    (sum, task) => sum + (task.reportSizeBytes ?? 0),
    0,
  )
  const plannedFileDeletes: string[] = []

  for (const task of reportCandidates) {
    if (totalReportBytes <= policy.maxReportBytesPerWorkspace) {
      break
    }
    if (!task.reportFilePath) {
      continue
    }
    const reportSize = task.reportSizeBytes ?? 0
    totalReportBytes -= reportSize
    plannedFileDeletes.push(task.reportFilePath)
    await db.tasks.update(task.id, {
      reportPrunedAt: now,
      reportFilePath: undefined,
      reportSizeBytes: 0,
    })
  }

  return {
    archivedTaskCount: archiveCandidates.length,
    prunedReportCount: plannedFileDeletes.length,
    plannedFileDeletes,
  }
}

export async function resetIssueIndexDb() {
  await db.delete()
}

export function inferWorkspaceIdFromIssues(issues: IssueItem[]): string {
  return issues[0]?.workspaceId ?? EMPTY_WORKSPACE_FALLBACK
}
