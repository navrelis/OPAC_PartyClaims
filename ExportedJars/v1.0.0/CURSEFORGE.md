# OPAC: Team Claims — CurseForge listing (v1.0.0)

Project name suggestion: **Open Parties and Claims: Team Claims**
Game version: **Minecraft 1.21.1** · Mod loader: **Fabric** · Environment: **client + server (required on both)** · License: **LGPL-3.0-only**

## Summary

Paste this into the CurseForge "Summary" field (max 256 characters):

```
Open Parties and Claims with real team bases: claim land together as a party, share one fair claim budget, let every member manage and forceload team chunks, and keep colors, names and permissions in sync automatically. Fabric 1.21.1.
```

## Categories

| Slot | Category | Why |
|---|---|---|
| **Main** | **Server Utility** | Chunk claiming, protection and party management are server administration / multiplayer infrastructure first. |
| Additional | **Utility & QoL** | Removes the daily friction of running a shared base: no more "who owns this chunk?", no manual permission juggling. |
| Additional | **Map and Information** | Team claims show up with one shared color and name on Xaero's Minimap and World Map, exactly like normal OPAC claims. |
| Additional | **Adventure and RPG** | Parties, shared territory and team identity are the backbone of faction-, town- and guild-style servers. |
| Additional | **API and Library** | Ships the complete, unchanged Open Parties and Claims API, so addons and integrations written for OPAC keep working. |

If CurseForge limits the number of additional categories, keep them in the order listed above.

Suggested release type for this first file: **Beta** (feature complete and covered by automated tests, first public build).

## Description

Everything below this line is the project description.

---

# Open Parties and Claims: Team Claims

**Your party built the base together. Now your party can own it together.**

This is a drop-in replacement build of **[Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims)** (OPAC) by Xaero96 for **Fabric 1.21.1**. It contains everything OPAC does — chunk claims, claim protection, forceloading, parties, allies, the full config UI, Xaero's Minimap / World Map integration, the complete API — and adds one thing OPAC players keep asking for: **Team Claims**.

## The problem it solves

In stock OPAC every claim belongs to exactly one player. For a group that builds together this gets awkward fast:

- The base is claimed by whoever got there first. If that player is offline, nobody else can expand, unclaim or fix the borders.
- Forceloaded farms stop working when "the wrong person" logs off.
- Everyone has to configure their own claim color, name and permissions, so one base looks like a patchwork on the map.
- Giving the whole party access means fiddling with per-player protection settings, and one wrong toggle locks your friends out of their own storage room.

Team Claims makes the party the unit that matters, while keeping every personal claim exactly as it was.

## How it works

### 1. Create a party — get a team
Create a party with a name in a single command:

```
/oparties create Iron Wolves
```

(or `/teamclaims create Iron Wolves`). The name becomes the party name, and every member — now and in the future — automatically receives a **team sub-config** called `team_iron_wolves` in their OPAC player config. Nobody has to set anything up. Existing parties are picked up automatically as well.

### 2. Claim for the team
Open the OPAC config (or use the usual OPAC sub-config option) and select the team sub-config as your **active sub-config for new claims**. From that moment, every chunk you claim — by command, keybind or by dragging on Xaero's World Map — is a **team claim**. Switch back to your main config whenever you want to claim something private. Personal and team claims live side by side.

### 3. Everyone can manage team land
Any party member can:

- **unclaim** a team claim, no matter which member originally claimed it,
- **enable or disable forceloading** on a team claim,
- build, break and interact everywhere on team land — party access on team claims is **always on and cannot be switched off by accident**.

No admin mode, no asking the original claimer to come online.

### 4. One fair, shared budget
Team claims are not free extra land. Every team claim counts against the claim limit of **every** party member, and a new team claim (or team forceload) is only accepted if **all** members still have room for it. In practice:

- Your claim counter shows *your personal claims + all team claims of your party* — in chat, in the claim UI and on the map.
- A party cannot out-claim solo players by stacking members: the member with the smallest limit caps the team.
- Server owners keep full control with the normal OPAC limits, bonus claims and permission-based limits. No new config to learn.
- When a teammate is at their limit you get a clear message naming who is blocking the claim.

### 5. One look, one set of rules
A team claim should look and behave the same no matter who claimed it:

- The team gets a **shared claim color** (generated from the party, changeable) and a **shared claim name**.
- All protection options of the team sub-config are **synchronized to every member automatically**.
- Only the **party owner and party admins** can change team settings. For everyone else the options are shown read-only, and the server enforces it.
- The team sub-config **cannot be deleted** by hand — it disappears by itself when you leave the party.

### 6. Forceloading that follows the team
Forceloaded team chunks stay loaded **while any party member is online**, not just the player who flipped the switch. Your shared farms keep running when the claimer goes to bed and someone else is still playing. When the last member logs off, the tickets are released again.

### 7. Members come and go, the base stays
When a member leaves or is kicked, their team claims are **transferred to the party owner automatically** — including the forceload state — instead of turning into private land in the middle of your base. Both sides get a chat message. This also works if it happened while the server was offline. If the whole party is disbanded, the team data is cleaned up.

## Feature overview

- Automatic `team_<party name>` sub-config for every party member (long party names supported)
- Team claims and personal claims side by side, switchable at any time
- Shared claim budget with per-member limit checks for claims **and** forceloads
- Teammates can unclaim and toggle forceload on team claims
- Party access permanently guaranteed on team claims
- Shared, synchronized claim color, claim name and protection settings
- Owner/admin-only editing, enforced server-side, read-only UI for members
- Team forceloads active while any member is online
- Automatic claim transfer to the party owner on leave/kick (also after server downtime)
- `/oparties create <name>` and `/teamclaims create <name>`
- All player-facing messages are translatable; players without the language file still get readable text
- Team data is stored per world, so singleplayer worlds never mix
- Fully compatible with Xaero's Minimap and Xaero's World Map claim display and claim controls
- Covered by automated server-side game tests (claiming, budgets, permissions, forceloading, settings sync, leaving)

## Installation

1. **Remove the original Open Parties and Claims jar.** This mod *is* Open Parties and Claims (same mod id `openpartiesandclaims`) with Team Claims built in — the two cannot be installed together.
2. Put this jar into the `mods` folder of the **server and every client**.
3. Requirements: Minecraft 1.21.1, Fabric Loader, **Fabric API**, **Forge Config API Port**. Recommended: Xaero's Minimap and Xaero's World Map.

Existing OPAC worlds, claims, parties and configs are used as they are — the data format is unchanged. You can go back to stock OPAC at any time; team claims then simply remain as normal claims of the players who own them.

## For server owners

- Based on Open Parties and Claims **0.31.6**; every OPAC server option, permission node and command works as documented upstream.
- Team Claims needs **no configuration**. Limits come from your existing `maxPlayerClaims` / `maxPlayerClaimForceloads`, bonus options and permission systems.
- OPAC's own, separate `partyOwnedClaims` option can stay off. If you enable it as well, both systems share one claim pool per party owner; the server log explains the details at startup.
- Team data lives in `<world>/data/opacteamclaims/` and in the world's saved data.
- Mods built against the OPAC API (parties, claims, config API, addon events) keep working.

## FAQ

**Do clients need it?** Yes. Like OPAC itself, it has to be installed on both sides for the UI, map integration and config screens.

**Can I still have private claims?** Yes. Only claims made while the team sub-config is selected are team claims.

**Who technically owns a team claim?** The member who claimed it (so OPAC's data stays fully compatible), but the whole party can manage it and it counts for everyone's budget.

**What happens to team land when I leave the party?** Your team claims move to the party owner. Your personal claims stay yours.

**Does it work with other party systems (FTB Teams, Argonauts)?** Team Claims is built on OPAC's default party system.

**Forge / NeoForge / other versions?** This release is Fabric 1.21.1 only.

## Credits & license

Open Parties and Claims is created by **Xaero96** and contributors — all credit for the claim, party and protection systems goes to them. Please support the original project: https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims

Team Claims fork by **Navrelis**. Licensed under the **GNU LGPL v3.0** (LGPL-3.0-only), the same license as the original. Source code: https://github.com/navrelis/OPAC_PartyClaims

Please report issues with this build to the fork's issue tracker, not to Xaero.
