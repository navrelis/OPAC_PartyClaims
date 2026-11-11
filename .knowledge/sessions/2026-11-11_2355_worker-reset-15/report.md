# reset-15 report

## Task: reset button follows the value (1.5.0 player config screen)
Cause: `ConfigOptionRowListElement.screenInit` created the "↺" button only if `resetVisible` was true at that moment, so the supplier was evaluated once per (re)layout; a later value change (or server sync) never created/removed it.
Fix (only `Common/.../client/gui/ConfigOptionRowListElement.java`): the button is always created in `screenInit` (initial visible/active from the supplier) and `render()` sets `visible = active = resetVisible.getAsBoolean()` each frame. Reset click still relayouts as before (search, groups, page kept by the screen). No API change.
Tests: `gradlew.bat :Fabric:build :NeoForge:build :Fabric:runBootTest :NeoForge:runTeamClaimsGameTest` BUILD SUCCESSFUL; Team Claims gametests: 156/156 passed. runBootTest passed (task ran, no failure).
Open points:
- No new gametest: the fix is client-only (widget visibility); `ConfigResetRule.isVisible` is unchanged and already tested.
- Not verified in the game client (not allowed). Visibility applies in the frame after a change (render order: widgets draw before the row's `render`), i.e. at most one frame late.
- A hidden button's tooltip/clicks are inactive (AbstractWidget ignores clicks when not visible/active).
