# MCXboxBroadcast NetherNet Fork

This fork is focused on one job: publish an Xbox joinable session for a Geyser-based server where the real gameplay join terminates inside a paired Geyser NetherNet fork.

This shows up to the authenticated accounts friends in-game as a joinable session. This work was built to bring back something the Bedrock community lost a long time ago: joining and inviting directly from the game. It also prepares for a future friends-of-friends flow, so players can join while their friends are already on your server.

It is not documented here as the stock upstream project. This README only covers the fork behavior added in this repo.

## What This Fork Adds

- `external-hosted` NetherNet publish mode for pairing with a separate Geyser ingress host
- a standalone jar release for Xbox session publishing
- a Geyser extension jar release for installs that still want the extension form
- bridge-first defaults with no transfer fallback in the gameplay path
- docs and config guidance for local-device deployments

## Reliable Geyser + MCXboxBroadcast Setup

Use this repository as the Xbox session publisher and pair it with the
[Geyser-Nethernet-for-mcxb fork](https://github.com/arti-inc/Geyser-Nethernet-for-mcxb)
as the gameplay ingress. The responsibilities are deliberately separate:

```text
Bedrock client
    -> Xbox session and NetherNet signaling
    -> Geyser NetherNet ingress
    -> Paper Java server
```

MCXboxBroadcast does not open a second Bedrock listener in `external-hosted`
mode. Geyser owns the live NetherNet connection and Paper owns the Java game.

### Requirements

- Java 25 for the current development builds
- Paper 1.21.11 (or the Java version selected by the paired Geyser build)
- ViaVersion and Floodgate installed on Paper, or Floodgate installed on Velocity
- The companion Geyser fork installed as `Geyser-Spigot.jar` or `Geyser-Velocity.jar`
- An Xbox/Microsoft account that is allowed to publish the session
- Bedrock players who can see the publisher through the Xbox friends/session UI

### Recommended directory layout

The standalone publisher discovers Geyser's status file automatically when it
runs from a sibling directory:

```text
stack/
  paper.jar
  plugins/
    Geyser-Spigot.jar
    floodgate-spigot.jar
    ViaVersion.jar
  mcxbox-standalone/
    MCXboxBroadcastStandalone.jar
    config.yml
    cache/
```

Do not commit or share `mcxbox-standalone/cache/cache.json`; it contains the
publisher's Xbox authentication data.

### Geyser configuration

In Geyser's `config.yml`, enable the portal bridge and point the auth-file
setting at the local MCXboxBroadcast cache. Use an absolute path:

```yaml
advanced:
  bedrock:
    portal-bridge:
      enabled: true
      xbox-auth-header-file: /absolute/path/to/stack/mcxbox-standalone/cache/cache.json
      nether-net-network-id: ''
      shard-count: 1
      debug-logging: false
```

The auth-file is read locally and is never printed by the bridge. Keep the two
processes on the same trusted machine unless you have a secure way to provide
the cache to Geyser.

### Velocity

Velocity is supported by the companion Geyser fork. Install `Geyser-Velocity.jar`
and Floodgate for Velocity in the proxy's `plugins/` directory, then configure
the portal bridge in `plugins/Geyser-Velocity/config.yml` using the same
`xbox-auth-header-file` and `nether-net-network-id: ''` settings shown above.
The MCXboxBroadcast standalone jar is unchanged: it publishes the Xbox session
while Geyser-Velocity owns NetherNet ingress. Configure the backend Java server
and Floodgate forwarding according to the normal Geyser/Velocity setup before
testing a Bedrock join.

When MCXboxBroadcast has sub-sessions, leave `xbox-auth-header-files` empty.
The companion Geyser fork automatically reads the primary cache and the
sub-session cache files listed by MCXboxBroadcast's `sub_sessions.json`, in
the same order as the NetherNet shards. Set `shard-count` to the number of
published sessions (primary plus active sub-sessions). Each shard must use the
Xbox account that publishes its corresponding Xbox session; using the primary
cache for every shard causes duplicate signaling and NetherNet join failures.

### MCXboxBroadcast configuration

In `mcxbox-standalone/config.yml`, keep the network ID empty so it is read from
Geyser's atomic readiness file:

```yaml
nether-net:
  external-hosted: true
  external-network-id: ''
  discovery-timeout-seconds: 120

friend-sync:
  auto-follow: false
  auto-unfollow: false
  initial-invite: false
  expiry:
    enabled: false
```

For a clean startup, run MCXboxBroadcast first, then start Paper/Geyser. The
publisher refreshes the Xbox authentication cache and waits; Geyser can then
bind its NetherNet signaling channel with the fresh header and write a ready
`portal-session-status.json`. MCXboxBroadcast discovers that file, verifies
the NetherNet ID, and publishes the Xbox session with the ID and `PmsgId`
supplied by the session service. No ID copying is required. If the cache is
known to be fresh, Paper/Geyser may also be started first.

The standalone console provides two safe operational commands:

```text
status                 # session, NetherNet ID, PmsgId presence, health
invite <xuid>          # one explicit invitation; validated and rate-limited
```

Automatic friend-list changes and bulk invitations are disabled by default.

### Joining and diagnosing

The Bedrock player should join from the Xbox/Minecraft friends session list.
The expected server log sequence is:

```text
session created
-> NetherNet offer/signaling
-> NetherNet peer connected
-> Bedrock session initialized
-> Floodgate authentication completed
-> Java/Paper connection established
```

If a join fails, inspect the Paper/Geyser log and classify the last stage:

- no offer: session publication, account visibility, or Xbox signaling
- offer/signals but no peer: NAT/ICE or transport failure
- peer but no Bedrock session: Bedrock protocol/NetherNet transport failure
- Bedrock session but no Floodgate: authentication or Floodgate key setup
- Floodgate but no Paper connection: Java/Paper or server shutdown failure

The client message “NetherNet” or “Door” is only a generic symptom; the
server-side stage is the useful diagnosis.

## Recommended Layout

Use this fork together with the companion Geyser fork in `arti-inc/Geyser-Nethernet-for-mcxb`.

Recommended runtime layout:

1. `MCXboxBroadcastStandalone.jar` publishes the Xbox Live session
2. `Geyser-Spigot.jar`, `Geyser-Velocity.jar`, or `Geyser-Standalone.jar` from the companion fork hosts the real NetherNet/Bedrock ingress
3. Bedrock gameplay traffic terminates in Geyser, not in `mcxba`
That removes the old gameplay relay bottleneck and is the smoothest setup from this work.

## Releases

Current release line:

- Build `3`

Assets:

- `MCXboxBroadcastStandalone.jar`
- `MCXboxBroadcastExtension.jar`

Release page:

- https://github.com/arti-inc/Broadcaster/releases

## Which Jar To Use

### Standalone

Use `MCXboxBroadcastStandalone.jar` when this process should run as its own Xbox session publisher.

Run:

```bash
java -jar MCXboxBroadcastStandalone.jar
```

### Extension

Use `MCXboxBroadcastExtension.jar` only if you explicitly want the extension form.

Install:

1. Drop the jar into Geyser's `extensions/` folder
2. Restart Geyser

## Config Note For Local Device Installs

If `mcxba` and the real Geyser NetherNet ingress are on the same local device, you do not need to use your router-forwarded public Bedrock port in `config.yml`.

In `external-hosted` mode, the important join identifier is the NetherNet network ID. The config can stay on the local or LAN listener that actually matches your Bedrock-side host.

## Companion Fork

Use this with:

- https://github.com/arti-inc/Geyser-Nethernet-for-mcxb
## Scope

This README is intentionally limited to the NetherNet fork behavior added here. For the original upstream project history and broader feature set, see the upstream `MCXboxBroadcast/Broadcaster` repository.
