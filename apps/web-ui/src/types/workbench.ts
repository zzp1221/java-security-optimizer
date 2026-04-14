export type Severity = 'critical' | 'high' | 'medium' | 'low'

export interface WorkspaceSummary {
  id: string
  name: string
  rootPath: string
  language: 'java'
  importedAt: string
}

export type TaskStatus =
  | 'created'
  | 'queued'
  | 'running'
  | 'cancelled'
  | 'failed'
  | 'completed'
  | 'archived'

export interface AnalysisTask {
  id: string
  workspaceId: string
  traceId?: string
  rulepackVersion: string
  status: TaskStatus
  progress: number
  startedAt: string
  finishedAt?: string
  failureReason?: string
  failureCategory?: string
  attempt?: number
  maxRetries?: number
}

export interface FixPreview {
  before: string
  after: string
}

export interface IssueItem {
  id: string
  taskId: string
  workspaceId: string
  ruleId: string
  ruleName: string
  severity: Severity
  filePath: string
  line: number
  column: number
  message: string
  recommendation: string
  fixPreview: FixPreview
  createdAt: string
}

export interface RulepackInfo {
  id: string
  name: string
  version: string
  enabled: boolean
  importedAt: string
}

export interface WorkbenchState {
  workspace: WorkspaceSummary
  task: {
    activeTaskId: string
    tasks: AnalysisTask[]
    issuesByTask: Record<string, IssueItem[]>
  }
  issues: {
    selectedIssueId?: string
  }
  rulepacks: RulepackInfo[]
}
