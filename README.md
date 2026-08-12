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

## Recommended Layout

Use this fork together with the companion Geyser fork in `arti-inc/Geyser-Nethernet-for-mcxb`.

Recommended runtime layout:

1. `MCXboxBroadcastStandalone.jar` publishes the Xbox Live session
2. `Geyser-Spigot.jar` or `Geyser-Standalone.jar` from the companion fork hosts the real NetherNet/Bedrock ingress
3. Bedrock gameplay traffic terminates in Geyser, not in `mcxba`
That removes the old gameplay relay bottleneck and is the smoothest setup from this work.

## Releases

Current release line:

- `nethernet-bridge-1`

Assets:

- `MCXboxBroadcastStandalone.jar`
- `MCXboxBroadcastExtension.jar`

Release page:

- https://github.com/arti-inc/Broadcaster/releases/tag/nethernet-bridge-1

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
