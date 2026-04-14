import { useEffect, useMemo, useState } from 'react'
import {
  cancelTask,
  getSecurityAuditEvents,
  getTask,
  getTaskDiagnostics,
  getPluginHealth,
  retryTask,
  type PluginHealthView,
  type SecurityAuditEventView,
  type TaskDiagnosticsView,
} from '../services/backendApi'
import { useWorkbenchStore } from '../store/workbenchStore'

export function TaskPage() {
  const { state, setActiveTask, upsertTask } = useWorkbenchStore()
  const [message, setMessage] = useState<string>('')
  const [busyTaskId, setBusyTaskId] = useState<string>('')
  const [loadingPanels, setLoadingPanels] = useState(false)
  const [diagnostics, setDiagnostics] = useState<TaskDiagnosticsView>()
  const [pluginHealth, setPluginHealth] = useState<PluginHealthView[]>([])
  const [securityEvents, setSecurityEvents] = useState<SecurityAuditEventView[]>([])

  const localRecentTasks = [...state.task.tasks]
    .filter((task) => task.finishedAt)
    .sort(
      (left, right) =>
        new Date(right.finishedAt ?? right.startedAt).getTime() -
        new Date(left.finishedAt ?? left.startedAt).getTime(),
    )
    .slice(0, 5)
  const localFailureTopReasons = Object.entries(
    state.task.tasks.reduce<Record<string, number>>((acc, task) => {
      if (task.status !== 'failed') {
        return acc
      }
      const reason = task.failureReason ?? 'unknown'
      acc[reason] = (acc[reason] ?? 0) + 1
      return acc
    }, {}),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
  const localDurationDistributions = [
    {
      bucket: '<5s',
      count: localRecentTasks.filter((task) => {
        if (!task.finishedAt) {
          return false
        }
        return new Date(task.finishedAt).getTime() - new Date(task.startedAt).getTime() < 5000
      }).length,
    },
    {
      bucket: '5s-30s',
      count: localRecentTasks.filter((task) => {
        if (!task.finishedAt) {
          return false
        }
        const duration = new Date(task.finishedAt).getTime() - new Date(task.startedAt).getTime()
        return duration >= 5000 && duration < 30000
      }).length,
    },
    {
      bucket: '30s-120s',
      count: localRecentTasks.filter((task) => {
        if (!task.finishedAt) {
          return false
        }
        const duration = new Date(task.finishedAt).getTime() - new Date(task.startedAt).getTime()
        return duration >= 30000 && duration < 120000
      }).length,
    },
    {
      bucket: '>=120s',
      count: localRecentTasks.filter((task) => {
        if (!task.finishedAt) {
          return false
        }
        return new Date(task.finishedAt).getTime() - new Date(task.startedAt).getTime() >= 120000
      }).length,
    },
  ]
  const activeIssues = state.task.activeTaskId
    ? state.task.issuesByTask[state.task.activeTaskId] ?? []
    : []
  const ruleHitTop = Object.entries(
    activeIssues.reduce<Record<string, number>>((acc, issue) => {
      acc[issue.ruleId] = (acc[issue.ruleId] ?? 0) + 1
      return acc
    }, {}),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)

  const recentTasks = diagnostics?.recentTasks ?? localRecentTasks.map((task) => ({
    taskId: task.id,
    status: task.status,
    durationMillis: task.finishedAt
      ? Math.max(0, new Date(task.finishedAt).getTime() - new Date(task.startedAt).getTime())
      : 0,
    issueCount: (state.task.issuesByTask[task.id] ?? []).length,
    finishedAt: task.finishedAt ?? task.startedAt,
  }))
  const failureTopReasons = diagnostics?.failureTopReasons ?? localFailureTopReasons.map(([reason, count]) => ({ reason, count }))
  const durationDistributions = diagnostics?.durationDistributions ?? localDurationDistributions
  const backendRuleHitTop = diagnostics?.ruleHitTop ?? ruleHitTop.map(([ruleId, count]) => ({ ruleId, hits: count }))

  const taskActionableMap = useMemo(() => {
    const map = new Map<
      string,
      {
        canCancel: boolean
        canRetry: boolean
      }
    >()
    for (const task of state.task.tasks) {
      map.set(task.id, {
        canCancel:
          task.status === 'created' ||
          task.status === 'queued' ||
          task.status === 'running',
        canRetry: task.status === 'failed' || task.status === 'cancelled',
      })
    }
    return map
  }, [state.task.tasks])

  useEffect(() => {
    let cancelled = false

    async function loadPanels() {
      setLoadingPanels(true)
      const [diagResult, pluginResult, auditResult] = await Promise.allSettled([
        getTaskDiagnostics(),
        getPluginHealth(),
        getSecurityAuditEvents(20),
      ])
      if (cancelled) {
        return
      }
      if (diagResult.status === 'fulfilled') {
        setDiagnostics(diagResult.value)
      }
      if (pluginResult.status === 'fulfilled') {
        setPluginHealth(pluginResult.value)
      }
      if (auditResult.status === 'fulfilled') {
        setSecurityEvents(auditResult.value)
      }
      setLoadingPanels(false)
    }

    loadPanels().catch((error) => {
      if (!cancelled) {
        setMessage(`读取后端面板失败：${error instanceof Error ? error.message : String(error)}`)
        setLoadingPanels(false)
      }
    })

    return () => {
      cancelled = true
    }
  }, [state.task.tasks])

  async function onCancel(taskId: string) {
    try {
      setBusyTaskId(taskId)
      await cancelTask(taskId)
      const refreshed = await getTask(taskId)
      upsertTask(refreshed)
      setMessage(`任务已取消：${taskId}`)
    } catch (error) {
      setMessage(`取消失败：${error instanceof Error ? error.message : String(error)}`)
    } finally {
      setBusyTaskId('')
    }
  }

  async function onRetry(taskId: string) {
    try {
      setBusyTaskId(taskId)
      const retried = await retryTask(taskId)
      upsertTask(retried)
      setActiveTask(taskId)
      setMessage(`任务已重试入队：${taskId}`)
    } catch (error) {
      setMessage(`重试失败：${error instanceof Error ? error.message : String(error)}`)
    } finally {
      setBusyTaskId('')
    }
  }

  return (
    <section className="page">
      <h2>任务页</h2>
      {message ? <p>{message}</p> : null}
      <article className="card">
        <table className="table">
          <thead>
            <tr>
              <th>任务ID</th>
              <th>状态</th>
              <th>重试</th>
              <th>进度</th>
              <th>失败分类</th>
              <th>规则版本</th>
              <th>开始时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {state.task.tasks.map((task) => (
              <tr key={task.id}>
                <td>{task.id}</td>
                <td>
                  <span className={`badge badge-${task.status}`}>{task.status}</span>
                </td>
                <td>
                  {task.attempt ?? 0}/{task.maxRetries ?? 0}
                </td>
                <td>{task.progress}%</td>
                <td>{task.failureCategory ?? '-'}</td>
                <td>{task.rulepackVersion}</td>
                <td>{new Date(task.startedAt).toLocaleString()}</td>
                <td>
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={state.task.activeTaskId === task.id}
                    onClick={() => setActiveTask(task.id)}
                  >
                    {state.task.activeTaskId === task.id ? '当前任务' : '设为当前'}
                  </button>
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={!taskActionableMap.get(task.id)?.canCancel || busyTaskId === task.id}
                    onClick={() => onCancel(task.id)}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    className="btn-ghost"
                    disabled={!taskActionableMap.get(task.id)?.canRetry || busyTaskId === task.id}
                    onClick={() => onRetry(task.id)}
                  >
                    重试
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
      <article className="card">
        <h3>后端诊断面板</h3>
        {loadingPanels ? <p className="muted">正在加载后端诊断数据...</p> : null}
        <p>最近任务耗时</p>
        <table className="table">
          <thead>
            <tr>
              <th>任务ID</th>
              <th>状态</th>
              <th>耗时</th>
            </tr>
          </thead>
          <tbody>
            {recentTasks.map((task) => (
              <tr key={`recent-${task.taskId}`}>
                <td>{task.taskId}</td>
                <td>
                  <span className={`badge badge-${task.status}`}>{task.status}</span>
                </td>
                <td>{Math.round(task.durationMillis / 1000)}s</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>失败 TOP 原因</p>
        <table className="table">
          <thead>
            <tr>
              <th>原因</th>
              <th>次数</th>
            </tr>
          </thead>
          <tbody>
            {failureTopReasons.length === 0 ? (
              <tr>
                <td colSpan={2}>暂无失败任务</td>
              </tr>
            ) : (
              failureTopReasons.map((item) => (
                <tr key={`failure-${item.reason}`}>
                  <td>{item.reason}</td>
                  <td>{item.count}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <p>耗时分布</p>
        <table className="table">
          <thead>
            <tr>
              <th>区间</th>
              <th>任务数</th>
            </tr>
          </thead>
          <tbody>
            {durationDistributions.map((distribution) => (
              <tr key={`duration-${distribution.bucket}`}>
                <td>{distribution.bucket}</td>
                <td>{distribution.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>命中规则 TOP</p>
        <table className="table">
          <thead>
            <tr>
              <th>规则ID</th>
              <th>命中次数</th>
            </tr>
          </thead>
          <tbody>
            {backendRuleHitTop.map((item) => (
              <tr key={`rule-${item.ruleId}`}>
                <td>{item.ruleId}</td>
                <td>{item.hits}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
      <article className="card">
        <h3>插件健康状态</h3>
        <table className="table">
          <thead>
            <tr>
              <th>语言</th>
              <th>插件</th>
              <th>状态</th>
              <th>兼容</th>
              <th>支持自动修复</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            {pluginHealth.length === 0 ? (
              <tr>
                <td colSpan={6}>暂无插件健康数据</td>
              </tr>
            ) : (
              pluginHealth.map((item) => (
                <tr key={`${item.language}-${item.pluginId}`}>
                  <td>{item.language}</td>
                  <td>{item.pluginId}</td>
                  <td>{item.status}</td>
                  <td>{item.compatible ? '是' : '否'}</td>
                  <td>{item.supportsAutofix ? '是' : '否'}</td>
                  <td>{item.message}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </article>
      <article className="card">
        <h3>安全审计事件</h3>
        <table className="table">
          <thead>
            <tr>
              <th>时间</th>
              <th>动作</th>
              <th>任务</th>
              <th>结果</th>
              <th>信息</th>
            </tr>
          </thead>
          <tbody>
            {securityEvents.length === 0 ? (
              <tr>
                <td colSpan={5}>暂无审计事件</td>
              </tr>
            ) : (
              securityEvents.map((event, index) => (
                <tr key={`${event.timestamp}-${event.userAction}-${index}`}>
                  <td>{new Date(event.timestamp).toLocaleString()}</td>
                  <td>{event.userAction}</td>
                  <td>{event.taskId ?? '-'}</td>
                  <td>{event.success ? '成功' : '失败'}</td>
                  <td>{event.message}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </article>
    </section>
  )
}
