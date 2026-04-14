import { useEffect, useMemo, useState } from 'react'
import { recordFixApplyAudit } from '../services/backendApi'
import {
  queryIssueFacets,
  queryIssues,
  seedWorkbenchStorage,
} from '../services/issueIndexDb'
import { useWorkbenchStore } from '../store/workbenchStore'
import type { IssueItem, Severity } from '../types/workbench'

interface IssuesPageProps {
  onOpenDiff: () => void
}

export function IssuesPage({ onOpenDiff }: IssuesPageProps) {
  const { state, setSelectedIssue } = useWorkbenchStore()
  const [loading, setLoading] = useState(true)
  const [rows, setRows] = useState<IssueItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [severity, setSeverity] = useState<Severity | 'all'>('all')
  const [ruleId, setRuleId] = useState<string>('all')
  const [filePath, setFilePath] = useState<string>('all')
  const [keyword, setKeyword] = useState('')
  const [selectedIssue, setSelectedIssueLocal] = useState<IssueItem>()
  const [ruleOptions, setRuleOptions] = useState<
    Array<{ ruleId: string; ruleName: string; count: number }>
  >([])
  const [filePathOptions, setFilePathOptions] = useState<
    Array<{ filePath: string; count: number }>
  >([])

  const pageSize = 5
  const pageCount = Math.max(1, Math.ceil(total / pageSize))

  useEffect(() => {
    const mergedIssues = Object.values(state.task.issuesByTask).flat()
    seedWorkbenchStorage({
      workspace: state.workspace,
      tasks: state.task.tasks,
      issues: mergedIssues,
      rulepacks: state.rulepacks,
    }).catch((error) => {
      console.error('初始化 IndexedDB 失败', error)
    })
  }, [state.rulepacks, state.task.issuesByTask, state.task.tasks, state.workspace])

  useEffect(() => {
    queryIssueFacets(state.workspace.id, state.task.activeTaskId)
      .then((facets) => {
        setRuleOptions(facets.ruleOptions)
        setFilePathOptions(facets.filePathOptions)
      })
      .catch((error) => {
        console.error('读取筛选器选项失败', error)
      })
  }, [state.task.activeTaskId, state.workspace.id])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    queryIssues({
      workspaceId: state.workspace.id,
      taskId: state.task.activeTaskId,
      severity,
      ruleId: ruleId === 'all' ? undefined : ruleId,
      filePath: filePath === 'all' ? undefined : filePath,
      keyword,
      page,
      pageSize,
    })
      .then((result) => {
        if (cancelled) {
          return
        }
        setRows(result.rows)
        setTotal(result.total)
        setSelectedIssueLocal((prev) =>
          prev ? result.rows.find((item) => item.id === prev.id) ?? result.rows[0] : result.rows[0],
        )
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [state.workspace.id, state.task.activeTaskId, severity, ruleId, filePath, keyword, page])

  const fromToText = useMemo(() => {
    if (total === 0) {
      return '0 / 0'
    }
    const start = (page - 1) * pageSize + 1
    const end = Math.min(page * pageSize, total)
    return `${start}-${end} / ${total}`
  }, [page, total])

  function createIssueContext(issue: IssueItem) {
    const sourceLines = issue.fixPreview.before.split('\n')
    const lineInPreview = Math.max(1, Math.min(issue.line, sourceLines.length))
    const from = Math.max(1, lineInPreview - 2)
    const to = Math.min(sourceLines.length, lineInPreview + 2)
    const lines: Array<{ no: number; content: string; focus: boolean }> = []
    for (let current = from; current <= to; current += 1) {
      lines.push({
        no: current,
        content: sourceLines[current - 1] ?? '',
        focus: current === lineInPreview,
      })
    }
    return lines
  }

  function resolveTriggerReason(issue: IssueItem) {
    if (issue.message.includes('硬编码')) {
      return '规则检测到敏感信息以字面量形式出现在源码中。'
    }
    if (issue.message.includes('注入')) {
      return '规则检测到拼接式 SQL 构造，缺少参数化约束。'
    }
    if (issue.message.includes('性能')) {
      return '规则检测到热点循环中的低效对象创建模式。'
    }
    return `由规则 ${issue.ruleId} 命中，触发条件见问题描述。`
  }

  function exportCsv() {
    const header = [
      'issueId',
      'taskId',
      'ruleId',
      'ruleName',
      'severity',
      'filePath',
      'line',
      'column',
      'message',
      'recommendation',
      'createdAt',
    ]
    const escape = (value: string | number) =>
      `"${String(value).replace(/"/g, '""')}"`
    const content = [
      header.join(','),
      ...rows.map((issue) =>
        [
          issue.id,
          issue.taskId,
          issue.ruleId,
          issue.ruleName,
          issue.severity,
          issue.filePath,
          issue.line,
          issue.column,
          issue.message,
          issue.recommendation,
          issue.createdAt,
        ]
          .map(escape)
          .join(','),
      ),
    ].join('\n')
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `issues-${state.task.activeTaskId}-p${page}.csv`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  }

  async function onRecordFixApply(issue: IssueItem) {
    try {
      await recordFixApplyAudit({
        taskId: issue.taskId,
        rulePackId: 'backend-live',
        fixId: issue.id,
        operator: 'web-ui',
      })
      window.alert(`已记录修复审计：${issue.id}`)
    } catch (error) {
      const text = error instanceof Error ? error.message : String(error)
      window.alert(`记录审计失败：${text}`)
    }
  }

  return (
    <section className="page">
      <h2>问题列表页</h2>
      <article className="card">
        <div className="toolbar">
          <label>
            严重级别
            <select
              value={severity}
              onChange={(event) => {
                setSeverity(event.target.value as Severity | 'all')
                setPage(1)
              }}
            >
              <option value="all">全部</option>
              <option value="critical">critical</option>
              <option value="high">high</option>
              <option value="medium">medium</option>
              <option value="low">low</option>
            </select>
          </label>
          <label>
            规则
            <select
              value={ruleId}
              onChange={(event) => {
                setRuleId(event.target.value)
                setPage(1)
              }}
            >
              <option value="all">全部</option>
              {ruleOptions.map((option) => (
                <option key={option.ruleId} value={option.ruleId}>
                  {option.ruleName} ({option.count})
                </option>
              ))}
            </select>
          </label>
          <label>
            文件
            <select
              value={filePath}
              onChange={(event) => {
                setFilePath(event.target.value)
                setPage(1)
              }}
            >
              <option value="all">全部</option>
              {filePathOptions.map((option) => (
                <option key={option.filePath} value={option.filePath}>
                  {option.filePath} ({option.count})
                </option>
              ))}
            </select>
          </label>
          <label>
            关键词
            <input
              value={keyword}
              onChange={(event) => {
                setKeyword(event.target.value)
                setPage(1)
              }}
              placeholder="规则名 / 文件 / 描述"
            />
          </label>
          <span className="muted">当前任务：{state.task.activeTaskId || '无'}</span>
          <button type="button" className="btn-ghost" onClick={exportCsv}>
            导出摘要 CSV
          </button>
        </div>

        {loading ? <p>加载中...</p> : null}
        {!loading && rows.length === 0 ? <p>当前筛选条件下没有问题。</p> : null}
        {!loading && rows.length > 0 ? (
          <table className="table">
            <thead>
              <tr>
                <th>规则</th>
                <th>级别</th>
                <th>定位</th>
                <th>描述</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((issue) => (
                <tr key={issue.id}>
                  <td>{issue.ruleName}</td>
                  <td>
                    <span className={`badge badge-${issue.severity}`}>
                      {issue.severity}
                    </span>
                  </td>
                  <td>
                    {issue.filePath}:{issue.line}:{issue.column}
                  </td>
                  <td>{issue.message}</td>
                  <td>
                    <button
                      type="button"
                      className="btn-ghost"
                      onClick={() => {
                        setSelectedIssueLocal(issue)
                        setSelectedIssue(issue.id)
                        onOpenDiff()
                      }}
                    >
                      查看对比
                    </button>
                    <button
                      type="button"
                      className="btn-ghost"
                      onClick={() => {
                        setSelectedIssueLocal(issue)
                        setSelectedIssue(issue.id)
                      }}
                    >
                      查看详情
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        <div className="pager">
          <button
            type="button"
            className="btn-ghost"
            disabled={page <= 1}
            onClick={() => setPage((prev) => Math.max(1, prev - 1))}
          >
            上一页
          </button>
          <span>{fromToText}</span>
          <button
            type="button"
            className="btn-ghost"
            disabled={page >= pageCount}
            onClick={() => setPage((prev) => Math.min(pageCount, prev + 1))}
          >
            下一页
          </button>
        </div>
      </article>

      <article className="card">
        <h3>问题详情面板</h3>
        {!selectedIssue ? (
          <p>请先在问题列表中选择问题查看详情。</p>
        ) : (
          <>
            <p>
              规则：{selectedIssue.ruleName} ({selectedIssue.ruleId})
            </p>
            <p>
              定位：{selectedIssue.filePath}:{selectedIssue.line}:{selectedIssue.column}
            </p>
            <p>触发原因：{resolveTriggerReason(selectedIssue)}</p>
            <p>建议说明：{selectedIssue.recommendation}</p>
            <button
              type="button"
              className="btn-ghost"
              onClick={() => onRecordFixApply(selectedIssue)}
            >
              记录修复审计
            </button>
            <div className="issue-context">
              <p className="muted">上下文代码（模拟 Monaco 定位高亮）</p>
              {createIssueContext(selectedIssue).map((line) => (
                <div
                  key={`${selectedIssue.id}-line-${line.no}`}
                  className={`ctx-line ${line.focus ? 'ctx-line-focus' : ''}`}
                >
                  <span className="ctx-line-no">{line.no}</span>
                  <code>{line.content}</code>
                </div>
              ))}
            </div>
          </>
        )}
      </article>
    </section>
  )
}
