# Worker twoplayer-15 — T6 two-player test for 1.5.0

## Status (context limit reached during investigation)
No harness code changed yet. Merged `claude/opac-1.5-unified-menu` (3da56f15). Design and findings below, ready to implement.
Detailed notes: `handover-T6.md` in this folder.

## Open points
- Product UX issue (to verify in the run, not fixed): `PlayerConfigScreen` value change does not re-init the page, so the
  reset button (↺) of an option appears only after a relayout (search change, page change, reopen).
- `MixinWorldMapSupportOpenPartiesAndClaims` uses `require = 0`: a failed injection would silently drop our 4 entries;
  the planned assertion on the entries catches it.
- The test client runs have no World Map today (`twoPlayerTestClient1/2` use `source(sourceSets.twoplayertest)`);
  a client-only source set is needed (see hand-over).
- This worktree has no `run-server/` (gitignored): copy `eula.txt`, `server.properties`, `ops.json` from
  `.claude/worktrees/opac-admin-rework-d73a06/run-server` (only those three files; not config/).
