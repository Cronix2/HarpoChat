// SPDX-License-Identifier: MIT
//! HarpoChat relay server library — exposes the router builder and state type
//! so integration tests can spin up the server in-process.

pub mod mailbox;
pub mod session;
pub mod ws;

use std::sync::Arc;

use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    routing::{any, get},
    Router,
};
use metrics_exporter_prometheus::PrometheusHandle;

use crate::mailbox::Mailbox;
use crate::session::SessionRegistry;

pub const SERVER_VERSION: u16 = 1;

#[derive(Clone)]
pub struct AppState {
    pub mailbox: Arc<dyn Mailbox>,
    pub sessions: Arc<SessionRegistry>,
    pub metrics: PrometheusHandle,
    pub server_version: u16,
}

pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/metrics", get(metrics_route))
        .route("/v1/ws", any(ws::ws_handler))
        .with_state(state)
}

async fn health() -> impl IntoResponse {
    (StatusCode::OK, axum::Json(serde_json::json!({ "status": "ok" })))
}

async fn metrics_route(State(state): State<AppState>) -> impl IntoResponse {
    state.metrics.render()
}
