# HarpoChat — Serveur relais

Relais WebSocket haute performance écrit en Rust. Principe : **le serveur ne voit jamais le contenu en clair**. Il achemine des enveloppes chiffrées Signal entre des identités Ed25519.

## Rôle

- Authentifier les clients par challenge/réponse Ed25519 (pas de mot de passe).
- Stocker temporairement les enveloppes chiffrées destinées aux utilisateurs hors-ligne.
- Relayer immédiatement aux utilisateurs en ligne.
- Publier et servir les PreKey bundles (à venir).

## Stack

- [Axum](https://github.com/tokio-rs/axum) 0.7 + Tokio 1.x
- SQLx pour la persistance (SQLite en dev, PostgreSQL en prod)
- `ed25519-dalek` pour la signature
- `tracing` + Prometheus pour l'observabilité

## Build

```bash
cd server
cargo build --release
./target/release/harpo-server
```

Variables d'environnement :

| Variable | Défaut | Description |
|---|---|---|
| `HARPO_BIND` | `0.0.0.0:8443` | Adresse d'écoute HTTP/WebSocket |
| `RUST_LOG` | `info,harpo_server=debug` | Filtre de logs |

## Endpoints

- `GET /health` → `{"status":"ok"}`
- `GET /metrics` → format Prometheus
- `WS /v1/ws` → protocole JSON-framed (voir `harpo-proto`)

## Handshake

```
Client → Hello { identity, version }
Server → Challenge { nonce }
Client → AuthResponse { signature = Ed25519(identity_sk, SHA256("harpochat/v1/auth-challenge" || nonce)) }
Server → Welcome { session_id }
```

## Tests

```bash
cargo test
```

## Prochaines étapes (roadmap serveur)

- [ ] `SqliteMailbox` persistant + migrations SQLx
- [ ] Publication/récupération de PreKey bundles
- [ ] Rate limiting par identité (tower-governor)
- [ ] TLS natif (rustls) + certificats Let's Encrypt
- [ ] Dockerfile multi-stage produisant un binaire musl ARM64
- [ ] Métriques détaillées (files d'attente par identité, latence relai, etc.)
