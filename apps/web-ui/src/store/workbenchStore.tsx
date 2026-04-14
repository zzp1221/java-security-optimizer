import {
  createContext,
  useContext,
  useMemo,
  useReducer,
  type PropsWithChildren,
} from 'react'
import { initialWorkbenchState } from '../mocks/mockData'
import type { WorkbenchState } from '../types/workbench'

type Action =
  | { type: 'task/setActive'; payload: string }
  | { type: 'issues/setSelected'; payload?: string }
  | { type: 'rulepacks/toggle'; payload: string }

interface WorkbenchContextValue {
  state: WorkbenchState
  setActiveTask: (taskId: string) => void
  setSelectedIssue: (issueId?: string) => void
  toggleRulepack: (rulepackId: string) => void
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
    case 'rulepacks/toggle':
      return {
        ...state,
        rulepacks: state.rulepacks.map((pack) =>
          pack.id === action.payload ? { ...pack, enabled: !pack.enabled } : pack,
        ),
      }
    default:
      return state
  }
}

const WorkbenchContext = createContext<WorkbenchContextValue | null>(null)

export function WorkbenchProvider({ children }: PropsWithChildren) {
  const [state, dispatch] = useReducer(reducer, initialWorkbenchState)

  const value = useMemo<WorkbenchContextValue>(
    () => ({
      state,
      setActiveTask: (taskId) =>
        dispatch({ type: 'task/setActive', payload: taskId }),
      setSelectedIssue: (issueId) =>
        dispatch({ type: 'issues/setSelected', payload: issueId }),
      toggleRulepack: (rulepackId) =>
        dispatch({ type: 'rulepacks/toggle', payload: rulepackId }),
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
