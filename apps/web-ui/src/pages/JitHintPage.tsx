import { useMemo, useState } from 'react'
import {
  getJitContextHints,
  type ContextHintResponseView,
  type JitHintView,
} from '../services/backendApi'
import { useWorkbenchStore } from '../store/workbenchStore'

type PriorityFilter = 'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'

function normalizePriority(priority: string): Exclude<PriorityFilter, 'ALL'> {
  const upper = priority.toUpperCase()
  if (upper === 'HIGH' || upper === 'MEDIUM' || upper === 'LOW') {
    return upper
  }
  return 'LOW'
}

export function JitHintPage() {
  const { state } = useWorkbenchStore()
  const [targetFilesText, setTargetFilesText] = useState('')
  const [maxFiles, setMaxFiles] = useState(50)
  const [maxMethodsPerFile, setMaxMethodsPerFile] = useState(30)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()
  const [priorityFilter, setPriorityFilter] = useState<PriorityFilter>('ALL')
  const [response, setResponse] = useState<ContextHintResponseView>()

  const filteredHints = useMemo(() => {
    const hints = response?.jitHints ?? []
    if (priorityFilter === 'ALL') {
      return hints
    }
    return hints.filter((hint) => normalizePriority(hint.priority) === priorityFilter)
  }, [priorityFilter, response?.jitHints])

  const hintsByPriority = useMemo(() => {
    const counters: Record<'HIGH' | 'MEDIUM' | 'LOW', number> = {
      HIGH: 0,
      MEDIUM: 0,
      LOW: 0,
    }
    for (const hint of response?.jitHints ?? []) {
      counters[normalizePriority(hint.priority)] += 1
    }
    return counters
  }, [response?.jitHints])

  async function onAnalyze() {
    if (!state.workspace.rootPath || state.workspace.rootPath === '未上传项目') {
      setError('请先在项目页上传项目，再执行上下文分析。')
      return
    }
    setLoading(true)
    setError(undefined)
    try {
      const targetFiles = targetFilesText
        .split(/\r?\n/)
        .map((item) => item.trim())
        .filter(Boolean)
      const result = await getJitContextHints({
        projectPath: state.workspace.rootPath,
        targetFiles: targetFiles.length > 0 ? targetFiles : undefined,
        maxFiles,
        maxMethodsPerFile,
      })
      setResponse(result)
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : String(requestError),
      )
    } finally {
      setLoading(false)
    }
  }

  function renderHintPriorityBadge(hint: JitHintView) {
    const normalized = normalizePriority(hint.priority)
    return <span className={`badge badge-${normalized.toLowerCase()}`}>{hint.priority}</span>
  }

  return (
    <section className="page">
      <h2>JIT提示页</h2>
      <article className="card">
        <h3>多级上下文理解</h3>
        <p>项目路径：{state.workspace.rootPath}</p>
        <div className="toolbar">
          <label>
            最大扫描文件数
            <input
              type="number"
              min={1}
              value={maxFiles}
              onChange={(event) => setMaxFiles(Number(event.target.value) || 1)}
            />
          </label>
          <label>
            单文件最大方法数
            <input
              type="number"
              min={1}
              value={maxMethodsPerFile}
              onChange={(event) =>
                setMaxMethodsPerFile(Number(event.target.value) || 1)
              }
            />
          </label>
          <button
            type="button"
            className="btn-ghost"
            onClick={onAnalyze}
            disabled={loading}
          >
            {loading ? '分析中...' : '执行上下文分析'}
          </button>
        </div>
        <label className="full-width">
          目标文件（可选，每行一个相对路径或绝对路径）
          <textarea
            rows={4}
            value={targetFilesText}
            onChange={(event) => setTargetFilesText(event.target.value)}
            placeholder="src/main/java/com/example/service/OrderService.java"
          />
        </label>
        {error ? <p>{error}</p> : null}
      </article>

      {response ? (
        <>
          <div className="card-grid">
            <article className="card">
              <h3>项目上下文汇总</h3>
              <p>文件数：{response.projectSummary.fileCount}</p>
              <p>类数：{response.projectSummary.classCount}</p>
              <p>方法数：{response.projectSummary.methodCount}</p>
              <p>循环数：{response.projectSummary.loopCount}</p>
              <p>分支数：{response.projectSummary.branchCount}</p>
            </article>
            <article className="card">
              <h3>JIT提示汇总</h3>
              <p>总提示：{response.jitHints.length}</p>
              <p>高优先级：{hintsByPriority.HIGH}</p>
              <p>中优先级：{hintsByPriority.MEDIUM}</p>
              <p>低优先级：{hintsByPriority.LOW}</p>
            </article>
          </div>

          <article className="card">
            <h3>文件上下文明细</h3>
            <table className="table">
              <thead>
                <tr>
                  <th>文件</th>
                  <th>类</th>
                  <th>方法</th>
                  <th>循环</th>
                  <th>分支</th>
                </tr>
              </thead>
              <tbody>
                {response.fileSummaries.map((file) => (
                  <tr key={file.filePath}>
                    <td>{file.filePath}</td>
                    <td>{file.classCount}</td>
                    <td>{file.methodCount}</td>
                    <td>{file.loopCount}</td>
                    <td>{file.branchCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>

          <article className="card">
            <h3>JIT友好代码提示</h3>
            <div className="toolbar">
              <label>
                优先级筛选
                <select
                  value={priorityFilter}
                  onChange={(event) =>
                    setPriorityFilter(event.target.value as PriorityFilter)
                  }
                >
                  <option value="ALL">全部</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>
              </label>
              <span className="muted">当前展示：{filteredHints.length}</span>
            </div>
            <table className="table">
              <thead>
                <tr>
                  <th>优先级</th>
                  <th>提示ID</th>
                  <th>位置</th>
                  <th>问题说明</th>
                  <th>优化建议</th>
                </tr>
              </thead>
              <tbody>
                {filteredHints.length === 0 ? (
                  <tr>
                    <td colSpan={5}>当前条件下没有提示</td>
                  </tr>
                ) : (
                  filteredHints.map((hint) => (
                    <tr key={`${hint.hintId}-${hint.filePath}-${hint.methodName}`}>
                      <td>{renderHintPriorityBadge(hint)}</td>
                      <td>{hint.hintId}</td>
                      <td>
                        {hint.filePath}
                        <br />
                        <span className="muted">
                          {hint.className ?? '-'}#{hint.methodName ?? '-'}
                        </span>
                      </td>
                      <td>{hint.message}</td>
                      <td>{hint.recommendation}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </article>
        </>
      ) : null}
    </section>
  )
}
