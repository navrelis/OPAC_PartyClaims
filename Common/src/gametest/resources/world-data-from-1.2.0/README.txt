World data written by Open Parties and Claims: Team Claims 1.2.0 (OPAC 0.31.6)
=================================================================================

What this is
------------
The files below "data/" are the OPAC and Team Claims files of a world saved by the fork in version 1.2.0
(git commit 50d1919, Fabric, Minecraft 1.21.1), copied UNCHANGED (byte for byte; .gitattributes keeps git from
touching the line endings of the text files). They are test input for the "save compatibility" run of the gametests
(Gradle tasks :Fabric:runSaveCompatTest and :NeoForge:runSaveCompatGameTest): that run copies "data/" into a fresh
world folder before the server starts and then checks that the current code loads all of it. They are not part of
any jar.

How it was produced
-------------------
A temporary git worktree of commit 50d1919 got one temporary gametest (not committed, worktree removed afterwards)
that, on a fresh world of ":Fabric:runBootTest", did the following with fixed UUIDs, then saved OPAC's data
(party, player config and player claim files) and the world's saved data, and let the server stop:
  - gave FixOwner the party name "Fixture Team" and the claims name "Fixture Land", created the party with its team
    (team_fixture_team) with the members FixMemberB (rank ADMIN) and FixMemberC (rank MEMBER);
  - made the claims below, with the team sub-config (index 1) for team claims and the main config (-1) for private
    claims, and forceloaded two of them;
  - set the team roles "claim" = MODERATOR and "forceload" = ADMIN ("unclaim" stays at the default MEMBER).
Nothing else touched the world: the files are exactly what the server wrote.

Players (offline, claim files carry the UUID as the user name)
-----------------------------------------------------------
  A  FixOwner    11111111-1111-4111-8111-111111111111   party owner
  B  FixMemberB  22222222-2222-4222-8222-222222222222   party member (rank ADMIN)
  C  FixMemberC  33333333-3333-4333-8333-333333333333   party member (rank MEMBER)
  D  FixLoner    44444444-4444-4444-8444-444444444444   in no party
Party: 557ea6ce-99d8-4c3c-957e-9762b8121a27 ("Fixture Team", team sub-config id "team_fixture_team").

Claims (chunk x, z; overworld unless noted) = 17 claims, 7 of them team claims
-----------------------------------------------------------------------------
  A private:  (10,0)   (11,0) forceloaded
  A team:     (100,0)  (101,0)  (102,0) forceloaded
  B private:  (20,0)   (20,1)   the_nether (6,6)
  B team:     (110,0)  (111,0)  the_nether (5,5)
  C private:  (30,0)
  C team:     (120,0)
  D private:  (40,0)   (40,1)   (41,0)   the_nether (7,7)
Team claims in claim order (as saved in opacteamclaims_data.dat): (100,0) (101,0) (102,0) (110,0) (111,0)
the_nether (5,5) (120,0); team forceload: (102,0). Private forceload: (11,0) of A.
Counts: A 5 claims (3 team, 2 private), B 6 (3 team, 3 private), C 2 (1 team, 1 private), D 4 (private).
Nobody has anchors (the files predate them).

Files
-----
  data/openpartiesandclaims/player-claims/*.nbt      claims per player (00..00 and 00..01 are the empty server and
                                                      expired-claims owners)
  data/openpartiesandclaims/player-configs/*.toml    player configs (A has claims name "Fixture Land" and party name
                                                      "Fixture Team")
  data/openpartiesandclaims/player-configs/sub-configs/<uuid>/team_fixture_team$1.toml   team sub-configs
  data/openpartiesandclaims/parties/<party id>.nbt   the party
  data/openpartiesandclaims/server-info.nbt          OPAC server info
  data/opacteamclaims/teams/<party id>.json          Team Claims' team config (name, members, roles)
  data/opacteamclaims_data.dat                       Team Claims' saved data (tracked team claims, team forceloads)
