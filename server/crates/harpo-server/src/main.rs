// SPDX-License-Identifier: MIT
//! HarpoChat relay server.
//!
//! A minimal, privacy-preserving WebSocket mailbox. The server never sees
//! plaintext: it only routes opaque ciphertext envelopes between Ed25519
//! identities.

mod mailbox;
mod session;
mod ws;

use std::net::SocketAddr;
use std::sync::Arc;

use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    routing::{any, get},
    Router,
};
use metrics_exporter_prometheus::{PrometheusBuilder, PrometheusHandle};
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

use crate::mailbox::{Mailbox, MemoryMailbox};
use crate::session::SessionRegistry;

#[derive(Clone)]
pub struct AppState {
    pub mailbox: Arc<dyn Mailbox>,
    pub sessions: Arc<SessionRegistry>,
    pub metrics: PrometheusHandle,
    pub server_version: u16,
}

pub const SERVER_VERSION: u16 = 1;

pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/metrics", get(metrics))
        .route("/v1/ws", any(ws::ws_handler))
        .with_state(state)
}

async fn health() -> impl IntoResponse {
    (StatusCode::OK, axum::Json(serde_json::json!({ "status": "ok" })))
}

async fn metrics(State(state): State<AppState>) -> impl IntoResponse {
    state.metrics.render()
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    init_tracing();

    let prom = PrometheusBuilder::new()
        .install_recorder()
        .expect("install prometheus recorder");

    let state = AppState {
        mailbox: Arc::new(MemoryMailbox::new()),
        sessions: Arc::new(SessionRegistry::new()),
        metrics: prom,
        server_version: SERVER_VERSION,
    };

    let addr: SocketAddr = std::env::var("HARPO_BIND")
        .unwrap_or_else(|_| "0.0.0.0:8443".to_string())
        .parse()?;

    let listener = tokio::net::TcpListener::bind(addr).await?;
    info!(%addr, "harpo-server listening");

    let app = build_router(state);
    axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>()).await?;
    Ok(())
}

fn init_tracing() {
    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info,harpo_server=debug"));
    tracing_subscriber::registry()
        .with(filter)
        .with(tracing_subscriber::fmt::layer().with_target(false))
        .init();
}

#[cfg(test)]
pub fn test_state() -> AppState {
    // Only the first call in a test process can install the global recorder; tests
    // that need metrics should call this via `std::sync::OnceLock`.
    let metrics = PrometheusBuilder::new()
        .install_recorder()
        .expect("metrics recorder already installed; wrap with OnceLock in tests");
    AppState {
        mailbox: Arc::new(MemoryMailbox::new()),
        sessions: Arc::new(SessionRegistry::new()),
        metrics,
        server_version: SERVER_VERSION,
    }
}
