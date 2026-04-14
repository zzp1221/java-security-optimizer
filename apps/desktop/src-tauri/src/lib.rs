use std::{
    collections::HashSet,
    path::{Path, PathBuf},
    sync::Mutex,
    time::Duration,
};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};
use tokio::sync::{watch, Mutex as AsyncMutex};

#[derive(Default)]
struct AppState {
    whitelist: Mutex<HashSet<PathBuf>>,
    engine: AsyncMutex<EngineSupervisor>,
}

#[derive(Default)]
struct EngineSupervisor {
    runtime: Option<EngineRuntime>,
    status: EngineStatus,
    config: Option<EngineConfig>,
    client: reqwest::Client,
}

struct EngineRuntime {
    stop_tx: watch::Sender<bool>,
    worker: tauri::async_runtime::JoinHandle<()>,
}

#[derive(Debug, Clone)]
struct EngineConfig {
    command: String,
    args: Vec<String>,
    base_url: String,
    health_path: String,
    submit_path: String,
    cancel_path_template: String,
    restart_delay_ms: u64,
}

impl Default for EngineConfig {
    fn default() -> Self {
        Self {
            command: "java".to_string(),
            args: vec![
                "-jar".to_string(),
                "./services/analysis-java/target/analysis-java.jar".to_string(),
                "--server.port=18765".to_string(),
            ],
            base_url: "http://127.0.0.1:18765".to_string(),
            health_path: "/health".to_string(),
            submit_path: "/tasks".to_string(),
            cancel_path_template: "/tasks/{taskId}/cancel".to_string(),
            restart_delay_ms: 1500,
        }
    }
}

#[derive(Debug, Default, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct EngineStatus {
    running: bool,
    pid: Option<u32>,
    restart_count: u32,
    base_url: Option<String>,
    last_error: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StartEngineRequest {
    project_dir: Option<String>,
    command: Option<String>,
    args: Option<Vec<String>>,
    base_url: Option<String>,
    health_path: Option<String>,
    submit_path: Option<String>,
    cancel_path_template: Option<String>,
    restart_delay_ms: Option<u64>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SubmitTaskRequest {
    project_dir: String,
    payload: serde_json::Value,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CancelTaskRequest {
    task_id: String,
    confirm_text: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SubmitTaskResponse {
    accepted: bool,
    engine_status: EngineStatus,
    data: serde_json::Value,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct CancelTaskResponse {
    cancelled: bool,
    engine_status: EngineStatus,
}

#[tauri::command(rename_all = "camelCase")]
fn select_project_dir(state: State<'_, AppState>) -> Result<Option<String>, String> {
    let Some(path) = rfd::FileDialog::new().pick_folder() else {
        return Ok(None);
    };

    let canonical = normalize_path(&path)?;
    ensure_not_sensitive_path(&canonical)?;
    let mut whitelist = state.whitelist.lock().map_err(|e| e.to_string())?;
    whitelist.insert(canonical.clone());
    Ok(Some(canonical.to_string_lossy().to_string()))
}

#[tauri::command(rename_all = "camelCase")]
async fn start_engine(
    app: AppHandle,
    state: State<'_, AppState>,
    request: Option<StartEngineRequest>,
) -> Result<EngineStatus, String> {
    if let Some(req) = &request {
        if let Some(project_dir) = &req.project_dir {
            ensure_authorized(&state, project_dir)?;
        }
        enforce_engine_security_policy(req)?;
    }

    let mut guard = state.engine.lock().await;
    guard.start(app, request).await
}

#[tauri::command(rename_all = "camelCase")]
async fn submit_task(
    state: State<'_, AppState>,
    request: SubmitTaskRequest,
) -> Result<SubmitTaskResponse, String> {
    ensure_authorized(&state, &request.project_dir)?;

    let mut engine = state.engine.lock().await;
    let payload = inject_project_dir(request.payload, &request.project_dir);
    let data = engine.submit_task(payload).await?;
    Ok(SubmitTaskResponse {
        accepted: true,
        engine_status: engine.status.clone(),
        data,
    })
}

#[tauri::command(rename_all = "camelCase")]
async fn cancel_task(
    state: State<'_, AppState>,
    request: CancelTaskRequest,
) -> Result<CancelTaskResponse, String> {
    ensure_cancel_confirmation(&request)?;
    let mut engine = state.engine.lock().await;
    engine.cancel_task(&request.task_id).await?;
    Ok(CancelTaskResponse {
        cancelled: true,
        engine_status: engine.status.clone(),
    })
}

impl EngineSupervisor {
    async fn start(
        &mut self,
        app: AppHandle,
        request: Option<StartEngineRequest>,
    ) -> Result<EngineStatus, String> {
        if self.runtime.is_some() && self.status.running {
            return Ok(self.status.clone());
        }

        let config = merge_engine_config(request)?;
        let (stop_tx, stop_rx) = watch::channel(false);
        let status_state = std::sync::Arc::new(AsyncMutex::new(self.status.clone()));
        let worker_config = config.clone();
        let app_for_worker = app.clone();
        let worker_status = status_state.clone();

        let worker = tauri::async_runtime::spawn(async move {
            sidecar_worker_loop(app_for_worker, worker_config, stop_rx, worker_status).await;
        });

        self.runtime = Some(EngineRuntime { stop_tx, worker });
        self.config = Some(config.clone());
        self.status.base_url = Some(config.base_url.clone());
        self.status.last_error = None;

        for _ in 0..10 {
            tokio::time::sleep(Duration::from_millis(400)).await;
            let status = status_state.lock().await.clone();
            self.status = status;
            if self.status.running {
                break;
            }
        }

        self.health_check().await?;
        Ok(self.status.clone())
    }

    async fn health_check(&mut self) -> Result<(), String> {
        let Some(config) = &self.config else {
            return Err("engine config is missing".to_string());
        };
        let url = join_url(&config.base_url, &config.health_path);
        let resp = self
            .client
            .get(url)
            .timeout(Duration::from_secs(3))
            .send()
            .await
            .map_err(|e| format!("engine health check failed: {e}"))?;
        if !resp.status().is_success() {
            let err = format!("engine health check status {}", resp.status());
            self.status.last_error = Some(err.clone());
            return Err(err);
        }
        Ok(())
    }

    async fn submit_task(&mut self, payload: serde_json::Value) -> Result<serde_json::Value, String> {
        let config = self
            .config
            .as_ref()
            .ok_or_else(|| "engine not started".to_string())?;
        let url = join_url(&config.base_url, &config.submit_path);
        let resp = self
            .client
            .post(url)
            .json(&payload)
            .send()
            .await
            .map_err(|e| format!("submit task failed: {e}"))?;
        if !resp.status().is_success() {
            let err = format!("submit task returned {}", resp.status());
            self.status.last_error = Some(err.clone());
            return Err(err);
        }

        resp.json::<serde_json::Value>()
            .await
            .map_err(|e| format!("submit task response parse failed: {e}"))
    }

    async fn cancel_task(&mut self, task_id: &str) -> Result<(), String> {
        let config = self
            .config
            .as_ref()
            .ok_or_else(|| "engine not started".to_string())?;
        let path = config.cancel_path_template.replace("{taskId}", task_id);
        let url = join_url(&config.base_url, &path);
        let resp = self
            .client
            .post(url)
            .send()
            .await
            .map_err(|e| format!("cancel task failed: {e}"))?;
        if !resp.status().is_success() {
            let err = format!("cancel task returned {}", resp.status());
            self.status.last_error = Some(err.clone());
            return Err(err);
        }
        Ok(())
    }
}

async fn sidecar_worker_loop(
    app: AppHandle,
    config: EngineConfig,
    mut stop_rx: watch::Receiver<bool>,
    status_state: std::sync::Arc<AsyncMutex<EngineStatus>>,
) {
    loop {
        if *stop_rx.borrow() {
            break;
        }

        let mut command = tokio::process::Command::new(&config.command);
        command.args(&config.args);
        command.kill_on_drop(true);

        let spawn_result = command.spawn();
        let mut child = match spawn_result {
            Ok(child) => child,
            Err(error) => {
                let mut status = status_state.lock().await;
                status.running = false;
                status.last_error = Some(format!("spawn sidecar failed: {error}"));
                let _ = app.emit("engine-status", &*status);
                tokio::time::sleep(Duration::from_millis(config.restart_delay_ms)).await;
                continue;
            }
        };

        {
            let mut status = status_state.lock().await;
            status.running = true;
            status.pid = child.id();
            status.restart_count = status.restart_count.saturating_add(1);
            status.base_url = Some(config.base_url.clone());
            status.last_error = None;
            let _ = app.emit("engine-status", &*status);
        }

        tokio::select! {
            exit = child.wait() => {
                let mut status = status_state.lock().await;
                status.running = false;
                status.pid = None;
                match exit {
                    Ok(exit_status) => {
                        status.last_error = Some(format!("sidecar exited: {exit_status}"));
                    }
                    Err(error) => {
                        status.last_error = Some(format!("wait sidecar failed: {error}"));
                    }
                }
                let _ = app.emit("engine-status", &*status);
                tokio::time::sleep(Duration::from_millis(config.restart_delay_ms)).await;
            }
            changed = stop_rx.changed() => {
                if changed.is_ok() && *stop_rx.borrow() {
                    let _ = child.kill().await;
                    let _ = child.wait().await;
                    let mut status = status_state.lock().await;
                    status.running = false;
                    status.pid = None;
                    let _ = app.emit("engine-status", &*status);
                    break;
                }
            }
        }
    }
}

fn ensure_authorized(state: &State<'_, AppState>, project_dir: &str) -> Result<(), String> {
    let requested = normalize_path(project_dir)?;
    ensure_not_sensitive_path(&requested)?;
    let whitelist = state.whitelist.lock().map_err(|e| e.to_string())?;
    let allowed = whitelist
        .iter()
        .any(|root| requested == *root || requested.starts_with(root));
    if allowed {
        Ok(())
    } else {
        Err(format!(
            "path is not authorized, please call selectProjectDir first: {}",
            requested.to_string_lossy()
        ))
    }
}

fn normalize_path(path: impl AsRef<Path>) -> Result<PathBuf, String> {
    let input = path.as_ref();
    if !input.exists() {
        return Err(format!("path does not exist: {}", input.to_string_lossy()));
    }
    std::fs::canonicalize(input).map_err(|e| format!("canonicalize path failed: {e}"))
}

fn merge_engine_config(request: Option<StartEngineRequest>) -> Result<EngineConfig, String> {
    let mut config = EngineConfig::default();
    if let Some(req) = request {
        if let Some(command) = req.command {
            if command.trim().is_empty() {
                return Err("engine command can not be empty".to_string());
            }
            config.command = command;
        }
        if let Some(args) = req.args {
            config.args = args;
        }
        if let Some(base_url) = req.base_url {
            config.base_url = base_url;
        }
        if let Some(path) = req.health_path {
            config.health_path = path;
        }
        if let Some(path) = req.submit_path {
            config.submit_path = path;
        }
        if let Some(path) = req.cancel_path_template {
            config.cancel_path_template = path;
        }
        if let Some(delay) = req.restart_delay_ms {
            config.restart_delay_ms = delay.max(200);
        }
    }
    Ok(config)
}

fn ensure_not_sensitive_path(path: &Path) -> Result<(), String> {
    let normalized = path.to_string_lossy().replace('\\', "/").to_lowercase();
    const SENSITIVE_MARKERS: [&str; 8] = [
        "/windows",
        "/program files",
        "/program files (x86)",
        "/programdata",
        "/users/default",
        "/.ssh",
        "/.gnupg",
        "/system32",
    ];
    if SENSITIVE_MARKERS.iter().any(|marker| normalized.contains(marker)) {
        return Err(format!(
            "sensitive path is not allowed for authorization: {}",
            path.to_string_lossy()
        ));
    }
    Ok(())
}

fn ensure_cancel_confirmation(request: &CancelTaskRequest) -> Result<(), String> {
    let expected = format!("CANCEL:{}", request.task_id);
    let actual = request.confirm_text.as_deref().unwrap_or_default();
    if actual != expected {
        return Err(format!(
            "cancel confirmation required, expected confirmText=\"{}\"",
            expected
        ));
    }
    Ok(())
}

fn enforce_engine_security_policy(request: &StartEngineRequest) -> Result<(), String> {
    if let Some(command) = &request.command {
        let normalized = command.trim().replace('\\', "/").to_lowercase();
        let allowed = normalized.ends_with("/java") || normalized.ends_with("/java.exe") || normalized == "java";
        if !allowed {
            return Err("engine command is blocked by security policy, only java/java.exe is allowed".to_string());
        }
    }

    if let Some(base_url) = &request.base_url {
        let parsed = reqwest::Url::parse(base_url).map_err(|e| format!("invalid baseUrl: {e}"))?;
        let host = parsed.host_str().unwrap_or_default().to_lowercase();
        let is_loopback = host == "127.0.0.1" || host == "localhost" || host == "::1";
        if !is_loopback {
            return Err("baseUrl is blocked by default deny-network policy, only loopback is allowed".to_string());
        }
    }

    if let Some(args) = &request.args {
        for arg in args {
            let lowered = arg.to_lowercase();
            let blocked = lowered.contains("-javaagent")
                || lowered.contains("-agentlib:jdwp")
                || lowered.contains("http://")
                || lowered.contains("https://");
            if blocked {
                return Err(format!("engine arg is blocked by security policy: {}", arg));
            }
        }
    }
    Ok(())
}

fn join_url(base_url: &str, path: &str) -> String {
    let base = base_url.trim_end_matches('/');
    let suffix = if path.starts_with('/') {
        path.to_string()
    } else {
        format!("/{path}")
    };
    format!("{base}{suffix}")
}

fn inject_project_dir(mut payload: serde_json::Value, project_dir: &str) -> serde_json::Value {
    if payload.get("projectPath").is_some() {
        return payload;
    }

    if let Some(map) = payload.as_object_mut() {
        map.insert(
            "projectPath".to_string(),
            serde_json::Value::String(project_dir.to_string()),
        );
        return payload;
    }

    serde_json::json!({
        "projectPath": project_dir
    })
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_process::init())
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            select_project_dir,
            start_engine,
            submit_task,
            cancel_task
        ])
        .run(tauri::generate_context!())
        .expect("failed to run tauri app");
}
