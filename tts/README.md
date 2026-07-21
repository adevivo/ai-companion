# Voice output — local TTS with Kokoro

Makes the companion **speak its chat lines** instead of you reading them. Runs entirely locally: no
cloud, no API key, no per-word cost. Upstream PlayerEngine voiced the companion through the Player2
cloud; we kept its (good) client-side audio path and repointed the audio source at a local
[Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) server.

**Only the companion's spoken `message` is voiced — never its `command` or reasoning.**

---

## 1. Start the TTS server

```bash
cd tts
docker compose up -d       # first run pulls ~2GB + fetches model weights
docker compose logs -f     # watch it come up
```

Verify it's healthy (takes ~10–30s on first boot):

```bash
curl localhost:8880/health                 # -> {"status":"healthy"}
curl localhost:8880/v1/audio/voices        # -> 68 voices
```

Hear it for yourself:

```bash
curl -X POST localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{"model":"kokoro","input":"I found some oak logs.","voice":"af_heart","response_format":"wav"}' \
  --output test.wav && afplay test.wav      # 'afplay' is macOS; use 'aplay' on Linux
```

## 2. Turn it on in the mod

In `config/aicompanion.json`:

```json
"tts": {
  "enabled": true,
  "endpoint": "http://localhost:8880",
  "model": "kokoro",
  "voice": "af_heart",
  "speed": 1.0
}
```

It's **off by default** — voice needs this container running, and enabling it without the container
would make every companion line attempt a doomed HTTP call.

Restart the game (config is read once at mod init). You should see the mode in the log:

```
[aicompanion] config loaded: ... tts=af_heart @ http://localhost:8880
```

## 3. Pick a voice

`curl localhost:8880/v1/audio/voices` lists all 68. The prefix encodes accent and gender —
`af_*` = American female, `am_*` = American male, `bf_*`/`bm_*` = British. Good starting points:
`af_heart`, `af_bella`, `af_sky`, `am_michael`, `bm_george`.

A persona can carry its own voice: a non-blank `Character.voiceIds()[0]` overrides `tts.voice`.

---

## How it works (and why the endpoint must reach the *client*)

```
server: AgentSideEffects ─► TTSManager ─► Player2APIService.textToSpeech
                                             │  packet: playerengine:stream_tts
                                             │  {endpoint, model, voice, text, speed}
                                             ▼
client: PlayerEngineClient ─► AudioUtils.streamAudio ─► POST {endpoint}/v1/audio/speech ─► javax.sound
```

The server never touches audio — it only tells the client *what* to say and *where* to synthesize it.
The **client** makes the HTTP call and plays the result, so sound comes out of the player's speakers
rather than the server's (which, on a headless box, would be nowhere).

The consequence: **`tts.endpoint` is resolved on the client machine.** For singleplayer / LAN on one
box, `localhost:8880` is correct. For a **remote dedicated server**, each player needs their own local
Kokoro (keep `localhost:8880`), or you point them at one reachable host (e.g. `http://10.0.0.5:8880`)
and bind the container beyond loopback. The server's config value is pushed to clients as-is.

While a line is being spoken, `TTSManager` holds a lock so the companion won't start another LLM turn
and talk over itself. The release is time-estimated from message length (~25 chars/sec), so a TTS
failure can't wedge the conversation.

---

## Notes and gotchas

- **Format must be `wav`.** `javax.sound.sampled` has no MP3 decoder — anything else throws
  `UnsupportedAudioFileException`. Kokoro returns 16-bit mono PCM @ 24kHz, which Java Sound reads natively.
- **Apple Silicon: use the CPU image** (it's what the compose file pins, multi-arch `linux/arm64`).
  The `-gpu` image is **CUDA-only** and will not use Metal through Docker. CPU is comfortably fast
  anyway — ~0.6s to synthesize ~3s of speech on an M-series, well ahead of realtime. For actual Metal
  acceleration you'd have to run Kokoro natively via `uv` instead of Docker.
- **The image tag is pinned** (`v0.6.0`) on purpose. Bump it deliberately and re-test rather than
  tracking `:latest`.
- **Failures are non-fatal by design.** If Kokoro is down, the line still appears in chat and the client
  logs `[AudioUtils] TTS playback failed`. The game is never blocked on speech.
- **No auth.** This endpoint is unauthenticated — don't expose port 8880 to an untrusted network.

## Config reference

| Key | Default | Meaning |
|---|---|---|
| `tts.enabled` | `false` | Master switch. |
| `tts.endpoint` | `http://localhost:8880` | Kokoro base URL, **resolved client-side**. |
| `tts.model` | `kokoro` | Sent as `model`; for OpenAI compatibility. |
| `tts.voice` | `af_heart` | Voice id; overridden by a persona's `voiceIds[0]`. |
| `tts.speed` | `1.0` | Synthesis rate; `1.0` is natural. |

Each also has a system-property / env override (`-Daicompanion.tts.enabled=true`,
`AICOMPANION_TTS_ENABLED=true`, etc.) — see `engine/.../player2api/TtsConfig.java`.
