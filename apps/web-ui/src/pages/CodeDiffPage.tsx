import { mockIssues } from '../mocks/mockData'
import { useWorkbenchStore } from '../store/workbenchStore'

export function CodeDiffPage() {
  const { state } = useWorkbenchStore()
  const issue = mockIssues.find((item) => item.id === state.issues.selectedIssueId)

  return (
    <section className="page">
      <h2>代码对比页</h2>
      {!issue ? (
        <article className="card">
          <p>请先在问题列表页选择一条问题再查看修复前后对比。</p>
        </article>
      ) : (
        <>
          <article className="card">
            <h3>{issue.ruleName}</h3>
            <p>
              位置：{issue.filePath}:{issue.line}:{issue.column}
            </p>
            <p>建议：{issue.recommendation}</p>
          </article>
          <div className="card-grid">
            <article className="card">
              <h3>修复前</h3>
              <pre>{issue.fixPreview.before}</pre>
            </article>
            <article className="card">
              <h3>修复后</h3>
              <pre>{issue.fixPreview.after}</pre>
            </article>
          </div>
        </>
      )}
    </section>
  )
}
