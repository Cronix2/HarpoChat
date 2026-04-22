# syntax=docker/dockerfile:1.7
# SPDX-License-Identifier: MIT
#
# Multi-stage build producing a small static binary for the HarpoChat relay.
# Targets x86_64 and aarch64 via buildx:
#   docker buildx build --platform linux/amd64,linux/arm64 -t harpo-server:dev .

FROM --platform=$BUILDPLATFORM rust:1.82-alpine AS builder
ARG TARGETPLATFORM
ARG BUILDPLATFORM

RUN apk add --no-cache musl-dev pkgconfig openssl-dev openssl-libs-static ca-certificates

WORKDIR /src
# Cache the workspace manifest first so deps are cached across code changes.
COPY Cargo.toml ./Cargo.toml
COPY crates/harpo-proto/Cargo.toml   crates/harpo-proto/Cargo.toml
COPY crates/harpo-crypto/Cargo.toml  crates/harpo-crypto/Cargo.toml
COPY crates/harpo-server/Cargo.toml  crates/harpo-server/Cargo.toml
RUN mkdir -p crates/harpo-proto/src crates/harpo-crypto/src crates/harpo-server/src \
 && echo "fn main(){}" > crates/harpo-server/src/main.rs \
 && echo "" > crates/harpo-proto/src/lib.rs \
 && echo "" > crates/harpo-crypto/src/lib.rs \
 && echo "" > crates/harpo-server/src/lib.rs \
 && cargo fetch

# Now copy the real sources and build.
COPY . .
ENV RUSTFLAGS="-C target-feature=+crt-static"
RUN cargo build --release -p harpo-server \
 && strip target/release/harpo-server || true

# -----------------------------------------------------------------------------
FROM alpine:3.20
RUN apk add --no-cache ca-certificates tini \
 && addgroup -S harpo && adduser -S -G harpo harpo
COPY --from=builder /src/target/release/harpo-server /usr/local/bin/harpo-server

USER harpo
ENV HARPO_BIND=0.0.0.0:8443
EXPOSE 8443
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/harpo-server"]
