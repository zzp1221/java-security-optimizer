import { useEffect, useMemo, useState } from 'react'
import { mockIssues } from '../mocks/mockData'
import { queryIssues, seedWorkbenchStorage } from '../services/issueIndexDb'
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
  const [keyword, setKeyword] = useState('')

  const pageSize = 5
  const pageCount = Math.max(1, Math.ceil(total / pageSize))

  useEffect(() => {
    seedWorkbenchStorage({
      workspace: state.workspace,
      tasks: state.task.tasks,
      issues: mockIssues,
      rulepacks: state.rulepacks,
    }).catch((error) => {
      console.error('初始化 IndexedDB 失败', error)
    })
  }, [state.rulepacks, state.task.tasks, state.workspace])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    queryIssues({
      workspaceId: state.workspace.id,
      taskId: state.task.activeTaskId,
      severity,
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
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [state.task.activeTaskId, severity, keyword, page])

  const fromToText = useMemo(() => {
    if (total === 0) {
      return '0 / 0'
    }
    const start = (page - 1) * pageSize + 1
    const end = Math.min(page * pageSize, total)
    return `${start}-${end} / ${total}`
  }, [page, total])

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
          <span className="muted">当前任务：{state.task.activeTaskId}</span>
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
                        setSelectedIssue(issue.id)
                        onOpenDiff()
                      }}
                    >
                      查看对比
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
    </section>
  )
}
