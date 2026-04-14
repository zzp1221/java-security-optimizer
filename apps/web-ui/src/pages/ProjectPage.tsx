import { useWorkbenchStore } from '../store/workbenchStore'

export function ProjectPage() {
  const { state, toggleRulepack } = useWorkbenchStore()
  const runningCount = state.task.tasks.filter((task) => task.status === 'running').length

  return (
    <section className="page">
      <h2>项目页</h2>
      <div className="card-grid">
        <article className="card">
          <h3>工作区信息</h3>
          <p>名称：{state.workspace.name}</p>
          <p>路径：{state.workspace.rootPath}</p>
          <p>语言：{state.workspace.language}</p>
          <p>导入时间：{new Date(state.workspace.importedAt).toLocaleString()}</p>
        </article>
        <article className="card">
          <h3>任务概览</h3>
          <p>总任务：{state.task.tasks.length}</p>
          <p>运行中：{runningCount}</p>
          <p>当前任务：{state.task.activeTaskId}</p>
        </article>
      </div>

      <article className="card">
        <h3>规则包管理</h3>
        <div className="rulepack-list">
          {state.rulepacks.map((pack) => (
            <label key={pack.id} className="rulepack-item">
              <input
                type="checkbox"
                checked={pack.enabled}
                onChange={() => toggleRulepack(pack.id)}
              />
              <span>{pack.name}</span>
              <span className="muted">v{pack.version}</span>
            </label>
          ))}
        </div>
      </article>
    </section>
  )
}
