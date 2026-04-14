import { useEffect, useState } from 'react'
import {
  getInstalledRulePacks,
  importCustomRulePack,
  submitAnalysisTask,
  uploadProjectFile,
  type InstalledRulePackView,
} from '../services/backendApi'
import { useWorkbenchStore } from '../store/workbenchStore'

export function ProjectPage() {
  const { state, upsertTask, setWorkspacePath, setActiveTask } = useWorkbenchStore()
  const runningCount = state.task.tasks.filter((task) => task.status === 'running').length
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState<string>()
  const [rulePackPackageFile, setRulePackPackageFile] = useState<File>()
  const [rulePackManifestFile, setRulePackManifestFile] = useState<File>()
  const [rulePackManifestText, setRulePackManifestText] = useState('')
  const [rulePackEnvironment, setRulePackEnvironment] = useState<'DEV' | 'PROD'>('DEV')
  const [rulePackImporting, setRulePackImporting] = useState(false)
  const [rulePackMessage, setRulePackMessage] = useState<string>()
  const [installedRulePacks, setInstalledRulePacks] = useState<InstalledRulePackView[]>([])

  async function refreshInstalledRulePacks() {
    try {
      const packs = await getInstalledRulePacks()
      setInstalledRulePacks(packs)
    } catch (error) {
      setRulePackMessage(
        `读取已安装规则包失败：${error instanceof Error ? error.message : String(error)}`,
      )
    }
  }

  async function onImportRulePack() {
    if (!rulePackPackageFile) {
      setRulePackMessage('请先选择规则包文件（.zip/.jar/.bin）。')
      return
    }
    if (!rulePackManifestFile && rulePackManifestText.trim().length === 0) {
      setRulePackMessage('请提供 manifest.json 文件或粘贴 manifest JSON 内容。')
      return
    }
    try {
      setRulePackImporting(true)
      const result = await importCustomRulePack({
        packageFile: rulePackPackageFile,
        manifestFile: rulePackManifestFile,
        manifestJson: rulePackManifestText,
        environment: rulePackEnvironment,
      })
      setRulePackMessage(
        `导入成功：${result.packId}@${result.version}，规则数 ${result.ruleCount}`,
      )
      await refreshInstalledRulePacks()
      setRulePackManifestFile(undefined)
      setRulePackManifestText('')
    } catch (error) {
      setRulePackMessage(`导入失败：${error instanceof Error ? error.message : String(error)}`)
    } finally {
      setRulePackImporting(false)
    }
  }

  async function onUploadAndAnalyze(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    try {
      setUploading(true)
      setMessage('正在上传并解析项目文件...')
      const upload = await uploadProjectFile(file)
      setWorkspacePath(upload.projectPath)

      setMessage('正在提交分析任务...')
      const task = await submitAnalysisTask({
        workspaceId: state.workspace.id,
        projectPath: upload.projectPath,
      })
      upsertTask(task)
      setActiveTask(task.id)
      setMessage(`任务已提交：${task.id}`)
      window.location.hash = '/tasks'
    } catch (error) {
      const text = error instanceof Error ? error.message : String(error)
      setMessage(`执行失败：${text}`)
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  useEffect(() => {
    void refreshInstalledRulePacks()
  }, [])

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
          <p>当前任务：{state.task.activeTaskId || '无'}</p>
        </article>
      </div>

      <article className="card">
        <h3>上传并分析</h3>
        <p>支持上传 `.java`、`.jar`、`.zip`，后端会自动识别并准备分析目录。</p>
        <input
          type="file"
          accept=".java,.jar,.zip,application/java,application/java-archive,application/zip,application/x-zip-compressed"
          onChange={onUploadAndAnalyze}
          disabled={uploading}
        />
        <p className="muted">{uploading ? '处理中，请稍候...' : '选择文件后自动触发分析'}</p>
        {message ? <p>{message}</p> : null}
      </article>

      <div className="card-grid">
        <article className="card">
          <h3>自定义规则导入</h3>
          <p>上传规则包并导入到当前系统，支持通过 `manifest` 校验签名和兼容性。</p>
          <div className="toolbar">
            <label>
              规则包文件
              <input
                type="file"
                accept=".zip,.jar,.bin,application/zip,application/java-archive,application/octet-stream"
                onChange={(event) => setRulePackPackageFile(event.target.files?.[0])}
                disabled={rulePackImporting}
              />
            </label>
            <label>
              环境
              <select
                value={rulePackEnvironment}
                onChange={(event) => setRulePackEnvironment(event.target.value as 'DEV' | 'PROD')}
                disabled={rulePackImporting}
              >
                <option value="DEV">DEV</option>
                <option value="PROD">PROD</option>
              </select>
            </label>
          </div>
          <div className="toolbar">
            <label>
              manifest 文件（可选）
              <input
                type="file"
                accept=".json,application/json"
                onChange={(event) => setRulePackManifestFile(event.target.files?.[0])}
                disabled={rulePackImporting}
              />
            </label>
          </div>
          <label>
            或粘贴 manifest JSON
            <textarea
              value={rulePackManifestText}
              onChange={(event) => setRulePackManifestText(event.target.value)}
              rows={10}
              placeholder='{"packId":"pack.java.security.core","version":"1.0.0",...}'
              disabled={rulePackImporting}
            />
          </label>
          <div className="toolbar">
            <button type="button" className="btn-ghost" onClick={onImportRulePack} disabled={rulePackImporting}>
              {rulePackImporting ? '导入中...' : '导入规则包'}
            </button>
            <button
              type="button"
              className="btn-ghost"
              onClick={() => {
                void refreshInstalledRulePacks()
              }}
              disabled={rulePackImporting}
            >
              刷新已安装列表
            </button>
          </div>
          {rulePackMessage ? <p>{rulePackMessage}</p> : null}
          <p className="muted">已安装规则包：{installedRulePacks.length}</p>
          <table className="table">
            <thead>
              <tr>
                <th>Pack ID</th>
                <th>版本</th>
                <th>语言</th>
                <th>规则数</th>
                <th>安装时间</th>
              </tr>
            </thead>
            <tbody>
              {installedRulePacks.length === 0 ? (
                <tr>
                  <td colSpan={5}>暂无已安装规则包</td>
                </tr>
              ) : (
                installedRulePacks.map((item) => (
                  <tr key={`${item.packId}-${item.version}`}>
                    <td>{item.packId}</td>
                    <td>{item.version}</td>
                    <td>{item.language}</td>
                    <td>{item.ruleCount}</td>
                    <td>{new Date(item.installedAt).toLocaleString()}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </article>

        <article className="card">
          <h3>导入规则使用说明</h3>
          <p>1. 准备规则包二进制文件（通常为 `.zip` 或 `.jar`）。</p>
          <p>2. 准备 `manifest`，可通过上传 JSON 文件或直接粘贴 JSON 文本。</p>
          <p>3. `manifest` 必填关键字段：`packId`、`version`、`language`、`engineVersionRange`、`checksum`、`rules`、`signature`。</p>
          <p>4. 先在 `DEV` 环境验证签名和兼容性，通过后再导入 `PROD`。</p>
          <p>5. 导入成功后点击“刷新已安装列表”，确认版本已入库。</p>
          <p className="muted">
            提示：`checksum` 必须和上传包一致；`signature.value` 必须是对规范化 payload 的签名值，否则会被拒绝。
          </p>
        </article>
      </div>
    </section>
  )
}
