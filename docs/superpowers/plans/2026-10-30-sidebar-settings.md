# Scoreboard visibility implementation plan

> Execute inline with superpowers:executing-plans; one fresh whole-change review at the end.

**Goal:** Existing Scoreboard switch actually hides/restores the board and persists network-wide.
**Architecture:** service-player durable bool + independent NATS invalidation; backend-owned preference controller; shared sidebar viewer gate; existing GUI persisted row.
**Tech Stack:** Kotlin, Quarkus/PostgreSQL/Flyway, JNATS, Minestom, Adventure.
**Spec:** ../specs/2026-10-30-sidebar-settings-design.md

## Global Constraints
DefaultON; unknown initial read hidden; known setting preserved on read failure. No layout/font/pack or gameplay changes. Concrete-player ownership and request generations; successful writes always invalidate independently of old connections. Existing UI click protection and click sounds retained. No proxy upgrade for this backend preference.

## Review Focus
Hidden boards stay hidden through font readiness, timer/row changes and frame recreation. Re-enable shows newest values. Old callbacks/disconnects cannot alter new connections. Delayed save after backend/proxy move reaches new owner. Failed storage has no visibility/event success; stop closes all tasks/subscriptions.

## Task1 — Player persistence
Files service-player PlayerNameRepository.kt, new PlayerSidebarResource.kt, V14, SettingsInvalidations.kt.
- [x] Baseline tests, then RED repository(defaultON/off/preserve other columns), resource(validation/errors) and forced invalidation with cache OFF.
- [x] Minimal bool SQL/read+write REST and post-commit event; full GREEN/build/OpenAPI; commit.

## Task2 — Shared visibility
Files library-minigame-api/minestom SidebarVisibility.kt, BrandedSidebar.kt, GameSidebar.kt and tests.
- [x] RED real player/viewer tests for both frame styles, repeated updates while hidden, latest rows on re-enable, replacement/old close ownership.
- [x] Per-player observer state, show/hide gate and observer lifecycle; full GREEN; commit.

## Task3 — Settings and live backend application
Files plugin-player common SettingsMenu/PlayerSettingsClient/messages; minestom GroundsSettingsModule and new SidebarPreferenceController/SettingsInvalidationSubscription.
- [x] RED actual HTTP/GUI flow and controller race tests (old reads/reconnect/late save/errors/stop); two-subscriber NATS fanout.
- [x] Persist real Gameplay row0, successful-save callback; owner-guarded loader, initial configuration, tick dispatch and managed invalidation/reconciliation; local library0.5 dependency; full GREEN; commit.

## Task4 — Integration and review
- [x] Bump plugin-lobby and all6host shared module pins only after local artifacts available; full host builds and actual shaded class checks.
- [x] One fresh whole-change review; important findings get RED/GREEN fixes.
- [x] Prepare separate source/consumer PRs with clear publication ordering and test evidence. Do not confuse previous completed batch with this new feature.

Final verification: 960 cases across four source repos and six hosts, 0 failures/errors, 5 existing Keystone skips. Eight shared implementation classes match the local source-module artifacts in all six server JARs. Fresh whole-change review found the queued failed-save rollback; actual delayed HTTP/GUI RED→GREEN regression and full suite verify its fix. Service tests use a shared JVM-lifetime Postgres fixture after upstream Quarkus profile reuse exposed stopped datasource URLs.

Deferred minor: Jackson accepts strings/numbers as booleans for the sidebar DTO, matching the mapper's default coercion. The GUI sends boolean JSON; null/missing requests reject400. Strict wrong-type rejection remains a follow-up. Live Minecraft/proxy interaction and deployed NATS grants have not been exercised; no release or deployment is part of this change.
