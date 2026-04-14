import { useWorkbenchStore } from '../store/workbenchStore'

function calcDurationSeconds(startedAt: string, finishedAt?: string) {
  if (!finishedAt) {
    return '-'
  }
  const millis = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  return `${Math.max(0, Math.round(millis / 1000))}s`
}

export function TaskPage() {
  const { state, setActiveTask } = useWorkbenchStore()
  const recentTasks = [...state.task.tasks]
    .filter((task) => task.finishedAt)
    .sort(
      (left, right) =>
        new Date(right.finishedAt ?? right.startedAt).getTime() -
        new Date(left.finishedAt ?? left.startedAt).getTime(),
    )
    .slice(0, 5)
  const failureTopReasons = Object.entries(
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

  return (
    <section className="page">
      <h2>任务页</h2>
      <article className="card">
        <table className="table">
          <thead>
            <tr>
              <th>任务ID</th>
              <th>状态</th>
              <th>进度</th>
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
                <td>{task.progress}%</td>
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
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
      <article className="card">
        <h3>本地诊断面板</h3>
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
              <tr key={`recent-${task.id}`}>
                <td>{task.id}</td>
                <td>
                  <span className={`badge badge-${task.status}`}>{task.status}</span>
                </td>
                <td>{calcDurationSeconds(task.startedAt, task.finishedAt)}</td>
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
              failureTopReasons.map(([reason, count]) => (
                <tr key={`failure-${reason}`}>
                  <td>{reason}</td>
                  <td>{count}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </article>
    </section>
  )
}
