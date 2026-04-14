import type {
  AnalysisTask,
  IssueItem,
  RulepackInfo,
  WorkbenchState,
} from '../types/workbench'

const rulepacks: RulepackInfo[] = [
  {
    id: 'rp-core-java',
    name: 'Java Core Rules',
    version: '1.2.0',
    enabled: true,
    importedAt: '2026-04-10T09:00:00.000Z',
  },
  {
    id: 'rp-sec-enterprise',
    name: 'Enterprise Security Pack',
    version: '0.9.3',
    enabled: true,
    importedAt: '2026-04-10T09:02:00.000Z',
  },
  {
    id: 'rp-style',
    name: 'Style and Maintainability',
    version: '0.5.1',
    enabled: false,
    importedAt: '2026-04-10T09:06:00.000Z',
  },
]

const tasks: AnalysisTask[] = [
  {
    id: 'task-20260414-001',
    workspaceId: 'ws-demo',
    rulepackVersion: 'core:1.2.0 + sec:0.9.3',
    status: 'running',
    progress: 62,
    startedAt: '2026-04-14T01:10:00.000Z',
  },
  {
    id: 'task-20260413-004',
    workspaceId: 'ws-demo',
    rulepackVersion: 'core:1.2.0 + sec:0.9.3',
    status: 'completed',
    progress: 100,
    startedAt: '2026-04-13T13:10:00.000Z',
    finishedAt: '2026-04-13T13:16:00.000Z',
  },
  {
    id: 'task-20260412-003',
    workspaceId: 'ws-demo',
    rulepackVersion: 'core:1.2.0 + sec:0.9.3',
    status: 'failed',
    progress: 73,
    startedAt: '2026-04-12T11:10:00.000Z',
    finishedAt: '2026-04-12T11:13:00.000Z',
    failureReason: 'parse timeout exceeded',
  },
]

const previewBefore = `public class AuthService {
  public boolean login(String user, String pwd) {
    if (user.equals("admin") && pwd.equals("123456")) {
      return true;
    }
    return false;
  }
}`

const previewAfter = `public class AuthService {
  public boolean login(String user, String pwd) {
    return PasswordVerifier.verify(user, pwd);
  }
}`

export const mockIssues: IssueItem[] = [
  {
    id: 'iss-001',
    taskId: 'task-20260414-001',
    workspaceId: 'ws-demo',
    ruleId: 'SEC-HARDCODED-CREDENTIAL',
    ruleName: 'Hardcoded Credential',
    severity: 'critical',
    filePath: 'src/main/java/com/acme/auth/AuthService.java',
    line: 3,
    column: 9,
    message: '检测到硬编码凭证，存在高风险。',
    recommendation: '将凭证迁移到安全存储并使用统一鉴权组件。',
    fixPreview: {
      before: previewBefore,
      after: previewAfter,
    },
    createdAt: '2026-04-14T01:11:00.000Z',
  },
  {
    id: 'iss-002',
    taskId: 'task-20260414-001',
    workspaceId: 'ws-demo',
    ruleId: 'PERF-STRING-CONCAT-IN-LOOP',
    ruleName: 'String Concat In Loop',
    severity: 'medium',
    filePath: 'src/main/java/com/acme/export/ReportBuilder.java',
    line: 42,
    column: 15,
    message: '循环中频繁字符串拼接会导致性能下降。',
    recommendation: '替换为 StringBuilder，减少中间对象创建。',
    fixPreview: {
      before: 'for (...) { text += value; }',
      after: 'StringBuilder sb = new StringBuilder(); for (...) { sb.append(value); }',
    },
    createdAt: '2026-04-14T01:12:00.000Z',
  },
  {
    id: 'iss-003',
    taskId: 'task-20260413-004',
    workspaceId: 'ws-demo',
    ruleId: 'STYLE-NULL-CHECK',
    ruleName: 'Prefer Objects.nonNull',
    severity: 'low',
    filePath: 'src/main/java/com/acme/order/OrderValidator.java',
    line: 28,
    column: 7,
    message: '建议统一空值判断风格。',
    recommendation: '使用 Objects.nonNull 提高可读性。',
    fixPreview: {
      before: 'if (obj != null) { ... }',
      after: 'if (Objects.nonNull(obj)) { ... }',
    },
    createdAt: '2026-04-13T13:12:00.000Z',
  },
  {
    id: 'iss-004',
    taskId: 'task-20260413-004',
    workspaceId: 'ws-demo',
    ruleId: 'SEC-SQL-INJECTION',
    ruleName: 'Potential SQL Injection',
    severity: 'high',
    filePath: 'src/main/java/com/acme/repo/UserRepo.java',
    line: 88,
    column: 21,
    message: 'SQL 拼接存在注入风险。',
    recommendation: '改为参数化查询或预编译语句。',
    fixPreview: {
      before: 'String sql = "select * from user where name=" + name;',
      after: 'PreparedStatement ps = conn.prepareStatement("select * from user where name=?");',
    },
    createdAt: '2026-04-13T13:14:00.000Z',
  },
]

export const initialWorkbenchState: WorkbenchState = {
  workspace: {
    id: 'ws-demo',
    name: 'demo-security-lab',
    rootPath: 'D:/repos/demo-security-lab',
    language: 'java',
    importedAt: '2026-04-10T08:55:00.000Z',
  },
  task: {
    activeTaskId: tasks[0].id,
    tasks,
    issuesByTask: {},
  },
  issues: {},
  rulepacks,
}
