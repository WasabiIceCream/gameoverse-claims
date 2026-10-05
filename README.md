# Gameoverse Claims

Land-claim rules for Gameoverse, built on Open Parties and Claims' own addon API plus one small mixin (below). Fabric 26.1.2, both
sides: the server runs the rules, and clients need the jar only for the Land Deed item.

## What it does

- **Structures can't be claimed where they reach the surface.** A chunk with any piece of a structure (village, temple,
  tower, castle, monument...) whose top is within `surfaceMargin` (4) blocks of the ground can't be claimed. OPAC
  shows the player the reason ("Village Plains can't be claimed: structures stay open to everyone"); an area claim
  claims every other chunk. In a roofed dimension (the Nether) every structure counts as surface. Claim admin mode
  skips addon listeners, so admins (and server claims) still can.
- **Underground structures stay open.** Mineshafts, strongholds, dungeons, ancient cities and trial chambers that stay
  below ground don't stop a claim above them. A player standing inside one of their pieces (bounding box, +1 block),
  at least `undergroundAccessDepth` (6) blocks below the surface, isn't stopped by other players' claims in that
  chunk (OPAC chunk access override), so nobody hits a wall mid-tunnel because someone built on top. For blocks
  (break, use, place) and entities (attack, use) the *target* must be inside the piece, not the player: OPAC's
  overrider API only passes the chunk, so `ChunkProtectionMixin` wraps OPAC's private `blockAccessCheck` and
  `entityAccessCheck` to record the target's position (startup log says whether both applied; if not, those checks
  fall back to the player's position, +1 block). Item use with no target still goes by the player's position. Server claims
  and roofed dimensions are never opened. Trade-off: something a claim owner builds inside that tunnel's box, that
  deep, isn't protected from someone standing in it.
- **Ignored structures** (`ignoredStructures`, `*` wildcards and `#tags`): decoration (trees, logs, spikes), fossils,
  buried treasure and ruined portals never count.
- **Extra claims.** OPAC's per-player `claims.bonusChunkClaims` adds to the limit LuckPerms sets (`xaero.pac_max_claims`
  meta: 36 vouched, 100 Discord-verified). Two ways to raise it:
  - **Land Deed** item: right-click to add `deedClaims` (9) chunks, for good. Only works for players who can claim
    at all (`deedNeedsBaseLimit`). Drops from Locked Chests (`lockedChestDeedChance`: 5/10/20/35/60% by tier).
  - `/gameoverse_claims bonus <player> [add|set <chunks>]` (works for offline players).
- **Report on existing claims**: `/gameoverse_claims report` checks every player claim a few chunks per tick and writes
  `logs/gameoverse-claims-report.tsv` (owner, chunk, surface and underground structures). Read-only.
- `/gameoverse_claims check`: what the rules see in the current chunk and at the current spot.
- `/gameoverse_claims reload`: rereads `config/gameoverse_claims.json`.

Commands need permission level 2 (console, or a LuckPerms grant).

## Build

`sh ./gradlew build` (JDK 25+). Compiles against the server's OPAC jar by path
(`../../fabric 26.1/mods/open-parties-and-claims-fabric-26.1.2-0.31.6.jar`): update the path when OPAC updates, and
recheck `IClaimActionListenerAPI`/`IChunkAccessOverriderAPI`, `ServerClaimsManager.tryToClaimHelper` (listeners
are only consulted outside claim admin mode, for `CLAIM`) and the two `ChunkProtection` methods the mixin wraps.

`tools/draw_deed.py` draws the item texture.

## License

MIT.
