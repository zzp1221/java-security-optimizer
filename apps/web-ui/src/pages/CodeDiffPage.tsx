import { useEffect, useMemo, useState } from 'react'
import { useWorkbenchStore } from '../store/workbenchStore'
import { queryIssues } from '../services/issueIndexDb'
import type { IssueItem } from '../types/workbench'

interface CompareResult {
  added: IssueItem[]
  fixed: IssueItem[]
  persistent: IssueItem[]
}

function buildIssueKey(issue: IssueItem) {
  return `${issue.ruleId}|${issue.filePath}|${issue.line}|${issue.column}`
}

export function CodeDiffPage() {
  const { state } = useWorkbenchStore()
  const [compare, setCompare] = useState<CompareResult>({
    added: [],
    fixed: [],
    persistent: [],
  })
  const [loading, setLoading] = useState(false)

  const activeTask = useMemo(
    () => state.task.tasks.find((task) => task.id === state.task.activeTaskId),
    [state.task.activeTaskId, state.task.tasks],
  )
  const previousTask = useMemo(() => {
    const sortedCompleted = [...state.task.tasks]
      .filter(
        (task) => task.id !== state.task.activeTaskId && task.status === 'completed',
      )
      .sort(
        (left, right) =>
          new Date(right.finishedAt ?? right.startedAt).getTime() -
          new Date(left.finishedAt ?? left.startedAt).getTime(),
      )
    return sortedCompleted[0]
  }, [state.task.activeTaskId, state.task.tasks])
  const selectedIssue = useMemo(
    () => compare.added
      .concat(compare.persistent)
      .find((item) => item.id === state.issues.selectedIssueId),
    [compare, state.issues.selectedIssueId],
  )

  useEffect(() => {
    if (!activeTask || !previousTask) {
      setCompare({
        added: [],
        fixed: [],
        persistent: [],
      })
      return
    }
    setLoading(true)
    Promise.all([
      queryIssues({
        workspaceId: state.workspace.id,
        taskId: activeTask.id,
        page: 1,
        pageSize: 5000,
      }),
      queryIssues({
        workspaceId: state.workspace.id,
        taskId: previousTask.id,
        page: 1,
        pageSize: 5000,
      }),
    ])
      .then(([current, previous]) => {
        const previousMap = new Map(
          previous.rows.map((issue) => [buildIssueKey(issue), issue]),
        )
        const currentMap = new Map(
          current.rows.map((issue) => [buildIssueKey(issue), issue]),
        )
        const added = current.rows.filter(
          (issue) => !previousMap.has(buildIssueKey(issue)),
        )
        const persistent = current.rows.filter((issue) =>
          previousMap.has(buildIssueKey(issue)),
        )
        const fixed = previous.rows.filter(
          (issue) => !currentMap.has(buildIssueKey(issue)),
        )
        setCompare({
          added,
          persistent,
          fixed,
        })
      })
      .finally(() => setLoading(false))
  }, [activeTask, previousTask, state.workspace.id])

  return (
    <section className="page">
      <h2>结果对比页</h2>
      {!activeTask ? (
        <article className="card">
          <p>没有找到当前任务，无法生成对比结果。</p>
        </article>
      ) : !previousTask ? (
        <article className="card">
          <p>缺少上一次已完成任务，暂时无法对比新增/修复/持续问题。</p>
        </article>
      ) : (
        <>
          <article className="card">
            <p>
              当前任务：{activeTask.id}，对比任务：{previousTask.id}
            </p>
            {loading ? <p>正在计算对比结果...</p> : null}
            <div className="compare-metrics">
              <span>新增问题：{compare.added.length}</span>
              <span>持续问题：{compare.persistent.length}</span>
              <span>修复问题：{compare.fixed.length}</span>
            </div>
          </article>
          <div className="card-grid">
            <article className="card">
              <h3>新增问题</h3>
              <ul className="issue-list">
                {compare.added.map((issue) => (
                  <li key={`added-${issue.id}`}>
                    {issue.ruleName} @ {issue.filePath}:{issue.line}
                  </li>
                ))}
              </ul>
            </article>
            <article className="card">
              <h3>持续问题</h3>
              <ul className="issue-list">
                {compare.persistent.map((issue) => (
                  <li key={`persistent-${issue.id}`}>
                    {issue.ruleName} @ {issue.filePath}:{issue.line}
                  </li>
                ))}
              </ul>
            </article>
            <article className="card">
              <h3>修复问题</h3>
              <ul className="issue-list">
                {compare.fixed.map((issue) => (
                  <li key={`fixed-${issue.id}`}>
                    {issue.ruleName} @ {issue.filePath}:{issue.line}
                  </li>
                ))}
              </ul>
            </article>
          </div>

          {selectedIssue ? (
            <div className="card-grid">
              <article className="card">
                <h3>修复前</h3>
                <pre>{selectedIssue.fixPreview.before}</pre>
              </article>
              <article className="card">
                <h3>修复后</h3>
                <pre>{selectedIssue.fixPreview.after}</pre>
              </article>
            </div>
          ) : null}
        </>
      )}
    </section>
  )
}
