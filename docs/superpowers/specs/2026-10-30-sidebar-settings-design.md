# Stored scoreboard visibility

The existing Gameplay/Scoreboard switch becomes real. Default ON. OFF hides the full sidebar, including logo, match ID and footer, in Lobby and all game modes. ON restores current data. State survives reconnects, backend/proxy changes and newly created match frames. Keep existing layout, fonts and click feedback.

Store sidebar_enabled BOOLEAN NOT NULL DEFAULT true on durable service-player.player_names (V14). Authenticated GET/PUT /v1/players/{id}/sidebar, JSON enabled bool. Failed reads/writes never pretend success; missing/invalid bool400, unknown write404. Writes touch only this column. Publish existing player.settings.changed.<UUID> after commit even with service settings cache disabled. Sidebar reads remain direct/authoritative; existing locale/region cache stays unchanged.

The minigame Minestom library exposes SidebarVisibility with per-concrete-Player transient state and removable observers. Both BrandedSidebar and GameSidebar respect it on every render/refresh; hide/remove viewer without destroying current rows; retain hidden state across frame replacement; unregister closed-frame observers. No static UUID ownership or shader changes.

plugin-player Minestom module loads during initial player configuration, keeps an owner/request-generation-guarded controller and applies only on the game scheduler. Unknown initial value stays hidden until read, known value survives read failure. Subscribe to existing service invalidations for locally owned players only; periodic60s reconciliation and reconnect refresh bound missed events. Every subscription, executor/task and stale result is cleaned on stop/disconnect.

GUI reads stored value on open; real Gameplay row0 keeps its position, remaining previews rows1/2. PUT success updates the current player's visibility immediately; failed save rolls GUI back and keeps the prior board. Late writes after reconnect/backend change reach the new connection via service-origin invalidation. No preference bridge through sender's proxy and no new proxy-plugin upgrade. Paper shares the persisted GUI setting; current Grounds sidebars targeted by this request are Minestom.

Implement and prepare separate reviewable PRs following the established PR-first workflow. Earlier release/deploy authorization applied to the finished mention/click batch; do not silently deploy this new feature to Production.
