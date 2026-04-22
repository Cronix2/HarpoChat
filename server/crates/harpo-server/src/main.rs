// SPDX-License-Identifier: MIT
//! Binary entry point for the HarpoChat relay server.

use std::net::SocketAddr;
use std::sync::Arc;

use harpo_server::mailbox::MemoryMailbox;
use harpo_server::rate_limit::RateLimiter;
use harpo_server::session::SessionRegistry;
use harpo_server::{
    build_router, AppState, DEFAULT_RATE_LIMIT, DEFAULT_RATE_WINDOW, SERVER_VERSION,
};
use metrics_exporter_prometheus::PrometheusBuilder;
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    init_tracing();

    let metrics = PrometheusBuilder::new()
        .install_recorder()
        .expect("install prometheus recorder");

    let mailbox: Arc<dyn harpo_server::mailbox::Mailbox> = match std::env::var("HARPO_DB").ok() {
        Some(url) if !url.is_empty() => {
            info!(%url, "opening sqlite mailbox");
            Arc::new(harpo_server::mailbox::SqliteMailbox::connect(&url).await?)
        }
        _ => {
            info!("using in-memory mailbox (set HARPO_DB for persistence)");
            Arc::new(MemoryMailbox::new())
        }
    };

    let state = AppState {
        mailbox,
        sessions: Arc::new(SessionRegistry::new()),
        rate_limiter: Arc::new(RateLimiter::new(DEFAULT_RATE_WINDOW, DEFAULT_RATE_LIMIT)),
        metrics,
        server_version: SERVER_VERSION,
    };

    let addr: SocketAddr = std::env::var("HARPO_BIND")
        .unwrap_or_else(|_| "0.0.0.0:8443".to_string())
        .parse()?;

    let listener = tokio::net::TcpListener::bind(addr).await?;
    info!(%addr, "harpo-server listening");

    let app = build_router(state);
    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await?;
    Ok(())
}

fn init_tracing() {
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("info,harpo_server=debug"));
    tracing_subscriber::registry()
        .with(filter)
        .with(tracing_subscriber::fmt::layer().with_target(false))
        .init();
}
