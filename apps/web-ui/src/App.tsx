import { useEffect, useMemo, useState } from 'react'
import './App.css'
import { CodeDiffPage } from './pages/CodeDiffPage'
import { IssuesPage } from './pages/IssuesPage'
import { ProjectPage } from './pages/ProjectPage'
import { TaskPage } from './pages/TaskPage'
import { WorkbenchProvider } from './store/workbenchStore'

type RouteKey = 'project' | 'tasks' | 'issues' | 'diff'

const routeEntries: Array<{ key: RouteKey; label: string; hash: string }> = [
  { key: 'project', label: '项目页', hash: '#/project' },
  { key: 'tasks', label: '任务页', hash: '#/tasks' },
  { key: 'issues', label: '问题列表页', hash: '#/issues' },
  { key: 'diff', label: '代码对比页', hash: '#/diff' },
]

function parseHashRoute(hash: string): RouteKey {
  const clean = hash.replace(/^#\//, '')
  if (clean === 'tasks' || clean === 'issues' || clean === 'diff') {
    return clean
  }
  return 'project'
}

function WorkbenchApp() {
  const [route, setRoute] = useState<RouteKey>(parseHashRoute(window.location.hash))

  useEffect(() => {
    const onHashChange = () => {
      setRoute(parseHashRoute(window.location.hash))
    }
    window.addEventListener('hashchange', onHashChange)
    if (!window.location.hash) {
      window.location.hash = '/project'
    } else {
      onHashChange()
    }
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const page = useMemo(() => {
    if (route === 'tasks') {
      return <TaskPage />
    }
    if (route === 'issues') {
      return <IssuesPage onOpenDiff={() => (window.location.hash = '/diff')} />
    }
    if (route === 'diff') {
      return <CodeDiffPage />
    }
    return <ProjectPage />
  }, [route])

  return (
    <div className="shell">
      <header className="topbar">
        <h1>离线代码优化器 - 前端工作台</h1>
        <nav className="nav">
          {routeEntries.map((entry) => (
            <a
              key={entry.key}
              href={entry.hash}
              className={route === entry.key ? 'active' : ''}
            >
              {entry.label}
            </a>
          ))}
        </nav>
      </header>
      <main>{page}</main>
    </div>
  )
}

function App() {
  return (
    <WorkbenchProvider>
      <WorkbenchApp />
    </WorkbenchProvider>
  )
}

export default App
