# AI Companion 0.4.1 — for Minecraft 1.21.11

The first release for **Minecraft 1.21.11**. Playing 1.20.1? Version 0.2.9 is still the one for you.

**Coming from 0.2.9?** Three updates in between (0.3.0, 0.3.1 and 0.4.0) never made it to CurseForge, so everything below is new to you.

## Highlights

- **She remembers you.** Tell your companion something ("my dog's name is Duke") and she can bring it up days later, in another world, or on a server. Optional and off by default; it needs a small embedding model such as Ollama's `nomic-embed-text`.
- **Multiplayer for everyone.** Each player brings their own companions, model and API key. The thinking happens on each player's own computer, and their memories stay there. A companion only listens to its owner. Companions are put away when their owner logs off and come back when they return.
- **Bodyguard.** Say "protect me" and she stays at your side and fights whatever comes for you, creepers first, without waiting to be told. She doesn't run from the fight.
- **Chests, with permission.** She can check, take from and put into chests, but only the ones you've opened yourself or told her to use.
- **Builds from what she carries.** Ask for "a small house from what you have" and she designs it around her inventory.
- **Doors.** She opens and closes them instead of breaking through.

## Privacy: no Player2 code, no telemetry

The engine inside this mod is our fork of PlayerEngine. Until now it still carried upstream's code for the Player2 hosted service: an account login, a background "heartbeat" request to Player2's servers every minute, and a route that could send AI requests there. It was switched off by default and AI Companion never used it, but it was in the jar. **In 0.4.1 it has been deleted.**

- The mod sends no telemetry and contacts no service of its own.
- The only network requests it makes go to endpoints **you** set in its config: your AI model, and optionally an embeddings model (memory) and a voice server (TTS). The defaults point at your own machine.
- The bundled engine now appears in your mod list as "AI Companion Engine (PlayerEngine fork)", so it's clear what it is.

## Also new

- **Better manners in company.** With other players online she only picks up lines that use her name, plus your follow-ups within 30 seconds of talking to her.
- **Instant edits.** Change a companion's skin, voice or personality in the settings and it applies immediately.
- **Cheaper on hosted models.** Most of each request is now cached by the provider.
- **Cloth Config is optional.** It's only needed for the settings screen. Without it, `/aicompanion config` edits settings from chat.
- **Local models:** servers that ignore or refuse JSON mode (seen with llama.cpp and LM Studio) are now handled.
- **Memory listens past "okay".** A sentence starting with filler like "okay", "hm" or "alright" no longer skips her memory for that turn. "okay stop" is still a command.
- **Fixes:** a client crash when a companion goes fishing, chat flooded with "Fishing process started", item duplication with chests, companions frozen after reconnecting to a dedicated server, conversation lost on logout, getting stuck in powder snow, and many more.

## Requirements

Minecraft 1.21.11 · Fabric Loader 0.17.2+ · Fabric API · **Architectury API**.
Optional: Cloth Config and Mod Menu (settings screen), and Kokoro in Docker (voice).
You still need a model to think with: a local server (llama.cpp, Ollama, LM Studio) or a hosted OpenAI-compatible API key.

**Running a server?** Its settings now live in `config/aicompanion-server.json`. That file is created from your old `aicompanion.json` the first time the server starts.

## Known issues

- If a companion is unloaded mid-task (for example, you die far away), she forgets the task. Just ask again.
- Free hosted models struggle with large builds. Small ones are fine.
- Stopping a build doesn't cancel the plan her model is already writing.
- She can't see animals or villagers yet.
- Land-claim mods can't see companions (they aren't players), so on a protected server a companion could break blocks inside a claim.
