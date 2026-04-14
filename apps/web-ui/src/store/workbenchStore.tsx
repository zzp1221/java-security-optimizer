import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  type PropsWithChildren,
} from 'react'
import type { WorkbenchState } from '../types/workbench'
import type { AnalysisTaskView } from '../services/backendApi'
import { getTask } from '../services/backendApi'

type Action =
  | { type: 'task/setActive'; payload: string }
  | { type: 'issues/setSelected'; payload?: string }
  | { type: 'task/upsert'; payload: AnalysisTaskView }
  | { type: 'workspace/setPath'; payload: string }

interface WorkbenchContextValue {
  state: WorkbenchState
  setActiveTask: (taskId: string) => void
  setSelectedIssue: (issueId?: string) => void
  upsertTask: (task: AnalysisTaskView) => void
  setWorkspacePath: (path: string) => void
}

const initialWorkbenchState: WorkbenchState = {
  workspace: {
    id: 'ws-local',
    name: 'local-upload-workspace',
    rootPath: '未上传项目',
    language: 'java',
    importedAt: new Date().toISOString(),
  },
  task: {
    activeTaskId: '',
    tasks: [],
    issuesByTask: {},
  },
  issues: {},
  rulepacks: [],
}

function reducer(state: WorkbenchState, action: Action): WorkbenchState {
  switch (action.type) {
    case 'task/setActive':
      return {
        ...state,
        task: {
          ...state.task,
          activeTaskId: action.payload,
        },
      }
    case 'issues/setSelected':
      return {
        ...state,
        issues: {
          selectedIssueId: action.payload,
        },
      }
    case 'task/upsert': {
      const incomingTask = action.payload
      const existingIndex = state.task.tasks.findIndex((task) => task.id === incomingTask.id)
      const nextTaskState = {
        ...incomingTask,
      }
      const nextTasks =
        existingIndex >= 0
          ? state.task.tasks.map((task, index) => (index === existingIndex ? nextTaskState : task))
          : [nextTaskState, ...state.task.tasks]
      return {
        ...state,
        task: {
          ...state.task,
          activeTaskId: state.task.activeTaskId || incomingTask.id,
          tasks: nextTasks,
          issuesByTask: {
            ...state.task.issuesByTask,
            [incomingTask.id]: incomingTask.issues,
          },
        },
      }
    }
    case 'workspace/setPath':
      return {
        ...state,
        workspace: {
          ...state.workspace,
          rootPath: action.payload,
          importedAt: new Date().toISOString(),
        },
      }
    default:
      return state
  }
}

const WorkbenchContext = createContext<WorkbenchContextValue | null>(null)

export function WorkbenchProvider({ children }: PropsWithChildren) {
  const [state, dispatch] = useReducer(reducer, initialWorkbenchState)

  useEffect(() => {
    if (!state.task.activeTaskId) {
      return
    }
    const activeTask = state.task.tasks.find((task) => task.id === state.task.activeTaskId)
    if (!activeTask || activeTask.status === 'completed' || activeTask.status === 'failed' || activeTask.status === 'cancelled' || activeTask.status === 'archived') {
      return
    }

    const timer = window.setInterval(() => {
      getTask(activeTask.id)
        .then((task) => {
          dispatch({ type: 'task/upsert', payload: task })
        })
        .catch((error) => {
          console.error('任务轮询失败', error)
        })
    }, 1500)

    return () => window.clearInterval(timer)
  }, [state.task.activeTaskId, state.task.tasks])

  const value = useMemo<WorkbenchContextValue>(
    () => ({
      state,
      setActiveTask: (taskId) =>
        dispatch({ type: 'task/setActive', payload: taskId }),
      setSelectedIssue: (issueId) =>
        dispatch({ type: 'issues/setSelected', payload: issueId }),
      upsertTask: (task) => dispatch({ type: 'task/upsert', payload: task }),
      setWorkspacePath: (path) => dispatch({ type: 'workspace/setPath', payload: path }),
    }),
    [state],
  )

  return (
    <WorkbenchContext.Provider value={value}>{children}</WorkbenchContext.Provider>
  )
}

export function useWorkbenchStore() {
  const context = useContext(WorkbenchContext)
  if (!context) {
    throw new Error('useWorkbenchStore 必须在 WorkbenchProvider 内使用')
  }
  return context
}
