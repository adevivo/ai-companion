# Changelog

Release notes as published on CurseForge. Newest first. Paste the version's section into the
CurseForge changelog field at upload — it renders Markdown there.

---

## 0.3.1 — A silent turn no longer crashes the world

Bundles PlayerEngine 1.1.18. One bug, in two places, and it is the kind worth shipping on its own.

### A companion that acted without speaking took the server down

A build issues dozens of turns that carry a command and no chat line — the companion is working, not
talking. The reply for one of those has a `command` and no `message`, and the guard that decides
whether to record it is an OR, so it recorded the missing message as a null. Gson does not skip a null
here: `addProperty("content", null)` stores a JSON null, and reading one as text throws.

Nothing failed at that point. The next turn logged the conversation history, `toString()` read that
stored null, and `UnsupportedOperationException: JsonNull` came out of a logging call **inside the
server tick loop** — so the world went down on a turn where nothing was wrong, because of one stored
on a turn that also looked fine. Observed 2026-08-22 in singleplayer, immediately after
`Ran out of birch_planks partway through building`.

Fixed in three places, because any one of them alone would have prevented it and a future writer that
forgets should not be able to bring it back:

- A command-only turn now stores an empty message. A companion that acted without speaking is an
  ordinary turn, not a missing one, and the role stays in the transcript.
- No adder can write a JSON null into history at all. Besides crashing readers, it goes out on the
  wire as `"content": null`, which some OpenAI-compatible providers reject.
- `toString()` tolerates one anyway. It is a logging helper on the server thread; a dump of the
  history is never worth a crash.

### And the same null, already saved, stopped a companion from starting at all

The record does reach disk — conversation history is a `.txt` transcript, one JSON object per line —
so a companion that took a command-only turn under 0.3.0 has a
`{"role":"assistant","content":null}` line in its file. Loading it read that null as text and threw,
and because the throw escapes the brain's constructor the companion never finished starting: it
retried on the next tick, and the next, at one error per second indefinitely. Observed 2026-08-22 as
`Luna's AI threw during tick; skipping this update`, repeating for as long as the world was open,
with that companion unable to answer.

The loader now repairs the line rather than choking on it — that turn was a companion acting without
speaking, and an empty message is what it always meant, so discarding the line would quietly rewrite
the transcript. **Your existing history files heal themselves on the next load; there is nothing to
delete.**

A corrupt transcript also no longer costs more than a transcript. The loader caught `IOException`
only, so anything else escaped and took the companion with it; it now catches everything, says which
file and why, and starts with an empty history. Losing a transcript is recoverable — a companion that
cannot exist is not.

Eight regression tests cover both halves, verified to fail against the old code.

### A rate limit is no longer reported as a broken endpoint

Every client-brain failure sent the owner the same sentence — *"could not reach your model. Check
llm.endpoint in your own config"* — whatever had actually gone wrong. A companion that hit
OpenRouter's free daily cap therefore told its owner to go and debug an endpoint that was working
perfectly. Naming the wrong cause is worse than naming none: it sends someone to fix the one thing
that is fine.

The client already sends its error text back; it was being dropped on the way to the player. Rate
limits, refused keys, exhausted credit and unreachable endpoints now each say what they are and what
to do, and an unrecognised failure is quoted rather than guessed at.

⚠️ **OpenRouter's free models allow 50 requests per day** without credits, resetting at 00:00 UTC;
10 credits raises it to 1000. A companion spends one per turn and a second per turn when memory
extraction is on, so 50 is about twenty minutes of conversation. The Model tooltip and the readme now
say so.

---

## 0.3.0 — It remembers, it thinks on your machine, and it works for everyone on the server

Bundles PlayerEngine 1.1.16. Self-contained jar as always — don't install a standalone engine
alongside it.

The largest release so far, in three pieces: the companion gained a memory, that memory and the
thinking behind it can run on your own machine, and the mod stopped being operator-only.

**After updating, an operator should read the new `server` block in `config/aicompanion.json`.** Your
existing values are moved into it automatically and the old file is kept as `aicompanion.json.bak`.
**Memory and extraction are both off by default.**

### It remembers things you tell it

The headline. A companion can hold facts about you across sessions, look up the ones relevant to what
you just said, and put them in front of the model without being asked to.

**`memory.enabled`, off by default**, and it needs an embedder — `embeddings.endpoint`, its own block
with its own model.

- **`/companion remember <fact>`** stores something true of you everywhere; **`rememberhere`** stores
  something true only in this world. Getting scope wrong is invisible: "I prefer cobblestone" is the
  first, "my base is in the taiga" the second, and storing the second as the first asserts it in
  every save.
- **`rememberhere` captures where you stood.** Asked "where's our home?" against a placeless memory
  whose whole text was "home", a companion once answered "near our original spawn at (0, 65, 0)" — a
  real coordinate, taken from the world spawn sitting elsewhere in the same packet.
- **Restating a fact confirms it; moving it supersedes it.** Re-running `rememberhere home` from a new
  position used to keep the old record and discard the new coordinates, while printing the position
  it had just thrown away.
- **`memory.extractionEnabled`, off by default**, learns from conversation: after a reply goes out,
  one JSON call reads the exchange and reports what was stated. About $0.00013 a turn on your own
  key, which is why it is opt-in.
- **It cannot cite itself.** Asked what wood you liked, a companion answered "Spruce! You always pick
  spruce" and recorded its own answer as evidence. A stored fact must now share a content word with
  what you actually said.
- **It says so in chat when memory breaks.** Every failure degrades to "no memories", so an outage
  was invisible and left the companion denying facts it held. Five distinct problems now report once
  each, and every turn memory declines to act on now leaves a reason in the log — a refused turn used
  to log at DEBUG against a server running at INFO, so it left no trace at any prefix and read as
  "ran and found nothing", which wants the opposite fix.
- **A pet is not pinned to one save.** Possessions belong to a world — a pickaxe, a stack of bones —
  so `owns` is world-scoped, and the model was reaching for it to describe a dog. Pets and people are
  now described with `related_to` instead, which travels with you.
- ⚠️ **The world id was never written to disk.** Registering it did not mark it dirty, so it was
  minted fresh on every load and no file ever appeared. Memories attached to the previous id become
  unreachable — if you ran an earlier 0.3.0 build, world-scoped memories from it may not come back.

### Setting memory up

An embedder turns a sentence into a vector so the right memory can be found again — a second thing to
run. **Ollama serves both it and the brain from one process:**

```bash
ollama pull qwen2.5:14b          # the brain
ollama pull nomic-embed-text     # the embedder, ~275 MB
export OLLAMA_MAX_LOADED_MODELS=2   # keep both resident
export OLLAMA_KEEP_ALIVE=-1         # don't unload them
ollama serve
```

Point `llm.endpoint` and `embeddings.endpoint` at `http://localhost:11434` — the same address twice
is correct. Those environment variables are not polish: at `MAX_LOADED_MODELS=1` Ollama evicts the
brain to embed and reloads it to answer on every turn, and without `KEEP_ALIVE` the embedder unloads
while you play.

The mod talks plain OpenAI, so LM Studio, vLLM and hosted providers work the same way. A plain
llama.cpp is the one that cannot do the embedder's half — one model per process, and it answers `501`
— so pair it with Ollama.

### It can think on your machine, with your key

**`llm.clientBrain`, off by default, needs `llm.localMode`.** The server sends the ingredients of a
prompt; your client recalls from *its* corpus, builds the prompt, calls *your* model with *your* key,
and returns only what the companion says and does. Your memories never reach the server and the
server's token bill is untouched. A vanilla client is never asked, and one that goes quiet times out.
The server also stops loading corpora it will never read: its copy freezes the moment the switch is
flipped while the client's moves on, so answering from it later reads as "it forgot the last month"
rather than "it is reading the wrong file". A player whose client cannot think still gets a store,
loaded on demand.

> ⚠️ **Commands coming back from a client are not yet validated.** This is why it defaults off. Don't
> enable it on a server whose clients you don't control.

### It costs a fraction of what it did

91% of every request was one system prompt — 14,684 characters, byte-identical on every call of a
measured session. Providers bill a repeated prefix at a steep discount and it was going unclaimed,
because cache entries are per server and nothing pinned a companion to one. A chat turn went from
~5,000 uncached input tokens to **633**, with the cache covering 85–88% in steady state.

**`llm.maxPromptChars` was below its own floor** — 16,000 could not fit the system prompt plus one
turn, so every turn silently discarded all history and was still over budget. Now 20,000. And history
was not being saved at all: the save interval was a coincidence check a message count could step
over, so a four-exchange session wrote nothing. **Existing configs keep their own values — check
yours.**

### The mod works for everyone on the server, not just the operator

`/companion` was gated on being an operator, so nobody else could use the mod at all. Permissions are
now per-subcommand at level 0 — spawn, come, where, stats, list, remember, the HUD toggles — while
`reload` and `skills reset` stay operator-only. LuckPerms nodes work by group
(`aicompanion.command.spawn`, `aicompanion.admin`); with no permissions mod everything falls back to
the vanilla operator level, so a family LAN sees no change. `server.allowPlayerCommands: false` closes
it again.

**Your companions are yours.** Identity, model, API key, memories, voice and `behavior.triggerPrefix`
are read from **your own** config on whatever server you play. Your client announces your roster and
prefix on join, and they spawn as yours — two players can both have a Vetta. The config screen no
longer opens on a server saying in red that it can change nothing; what the operator controls is on a
read-only **Server** tab showing that server's real values.

**And only yours.** Every targeting command matched on display name and enforced no ownership, so
`/companion despawn Vetta` from a stranger worked — the owner was recorded at spawn and simply never
checked. Chat routing was pure proximity too: anyone within 64 blocks drove any companion, and the
turn was billed to the *owner*, whose memories then learned the stranger's sentence under the owner's
name. Being an operator used to widen the *default* target, so a bare `/companion despawn` picked the
nearest companion belonging to anyone; unnamed commands now always mean your own. Reaching someone
else's still works by naming it, and says so:

```
Ava belongs to Alex — acting on it as an operator.
```

Caps: `server.maxCompanionsPerPlayer` (2) and `server.globalCompanionCap` (20) — nothing bounded this
before, and each companion is a pathfinder on the server thread.
`server.companionsAnswerAnyone: true` restores open chat for a LAN where that is the point. In
singleplayer and as LAN host, whoever opened the world keeps operator access without "Allow Cheats";
guests don't inherit it.

### A guest's broken endpoint no longer spends the operator's key

If a client announced it could think and then couldn't reach its model, the server quietly answered
for it — on the **operator's** key, every turn. Measured 2026-08-21: a guest pointed at a llama.cpp
that wasn't running produced `Connection refused` seven times and the server answered all of them,
25,144 tokens. She saw normal replies; the operator saw a bill with no cause.

A client that **never announced** is still answered — that was always the server's job. One that
announced and then failed is not; its owner is told instead, with the endpoint named.
`server.serverAnswersWhenClientFails: true` restores the old behaviour.

### Companions are put away while you are offline

Left alone, a companion is an entity in the world save — one spawned months ago is still standing
there, drawn as default Steve, answering nobody, holding a slot in the cap. Yours are now stored to
disk on disconnect and brought back where you left them. `server.parkWhenOwnerOffline: false` turns it
off.

⚠️ **A companion is never removed unless its file was written *and read back* first.** One that fails
to park is left standing and tries again — much the better failure than somebody's fully-kitted
companion vanishing with its inventory.

### Settings you could edit but nothing would read

Four bugs, one shape: the value was saved, and then never reached the thing that needed it.

- **`tts.endpoint` was the server's, not yours.** Your client fetches and plays the audio, but the URL
  came from the *server's* config — so a dedicated server shipped its own `http://localhost:8880` to
  everybody and `TTS playback failed (http://localhost:8880)` no matter what you typed. Now read from
  **your** file. `model`, `voice` and `speed` still come from the server; those describe the
  companion, not your network.
- **The config screen saved without applying on a remote server.** Save looked for a local server to
  hand the file to, and on a dedicated server there isn't one — so the values in force stayed the ones
  read at JVM startup, and changing `llm.endpoint` genuinely required quitting the game.
- **`/companion reload` was operator-only.** Right about the server's half, wrong about everyone's
  own. Reload is now open to all and does the half belonging to the caller, saying which ran; the
  server's half still needs permission. It also re-announces your roster — **a companion added while
  connected previously could not be spawned until you reconnected**, because the roster is announced
  on join, not read on demand.
- **Memory failures were silent on the machine that had them.** `MemoryHealth` puts problems in your
  chat, but the only thing draining it ran on the server. With `clientBrain` on, the corpus and
  embedder are both on your machine, so an unreachable embedder warned where nothing read it. Your
  client now reports its own.

### Memory was working and looked broken

The `memory` help text and the Memory tab both still described a prototype — "nothing is stored,
nothing is learned, and nothing you say is remembered", untrue for two releases and exactly the wrong
thing to read while working out why memory looks inert. Worse, `memory.extractionEnabled` wasn't on
that tab at all, so the one switch that makes a companion learn could only be reached by hand-editing
JSON. There is now a **Learn From Conversation** toggle beside Enabled, and both texts say what
actually gates it: enabling memory stores nothing on its own, the `memories/` folder is created by the
first write rather than by the setting, and `llm.clientBrain` **on the server** decides which machine
holds the corpus.

Help text shipped in `aicompanion.json` no longer quotes real phrases or player names from testing.

### Choosing a model, and what tells you when it's wrong

`https://openrouter.ai/api` was in the endpoint list with no model suggestions — on a provider with
hundreds of ids, the same as no support. The Model box now suggests OpenRouter's **free** models, but
only those that can be asked for JSON. Every reply must be a JSON object, and a model that ignores
`response_format` answers in prose, so nothing it decides runs. From OpenRouter's live model API on
2026-08-22: 85% of all 421 models advertise `response_format` and only **7 of the 18 free ones** do.
Keep the `:free` suffix or you are billed.

**Qwen2.5-14B-Instruct-Q4_K_M is the recommended local default.** It was carried as a floor and
described as "not very capable" at strict command output; extended testing says it holds the format
reliably on consumer hardware. Instruction-following matters more than parameter count here. The
llama.cpp launch flags come down with it — `-c 262144` to `-c 8192`, and `--mlock` dropped, because a
256k context reserves gigabytes of KV cache before generating a token and the companion sends small
situation packets. The readme gains a section on the failure that follows from getting this wrong:
a local model and Minecraft compete for the same VRAM, shader packs make it far likelier, and what
goes down is usually the whole machine.

**`llm.maxTokens` now ships at 2000.** It was 1000 — also the floor the mod warns below — so the
default had no headroom, and both ways of exhausting it turned up in one morning: a reasoning model
spends the budget on hidden thinking before writing a word, and a small model that fails to emit a
stop token runs to the cap and gets retried. It's a cap, not a budget; a higher ceiling costs nothing
on short replies. **Existing configs keep 1000.** Two README examples were shipping `200`, below the
floor that same README warns about.

Two diagnostics that used to mislead:

- **"Not valid JSON" never said whether JSON had been asked for.** A model that ignores the envelope
  and an endpoint that drops `response_format` log identically and want opposite fixes. The failure
  now names which, and notes that a backend honouring constrained decoding *cannot* return prose.
- **"Cut off by the token limit" advised raising the cap, which is wrong half the time.** A free 9B
  model produced a complete, correct reply and then several hundred blank lines and `</</</…` until it
  hit the cap — the closing brace never arrived. It hadn't run out of room; it failed to *stop*.
  Raising the cap buys more garbage, and bills you for it. The message now checks the tail for a
  repeated character.
- **The client's reload line reported the wrong brain**, printing `brain=server` on the machine
  running every turn, because it read the local `llm.clientBrain` — a wish on a dedicated server,
  where the server's copy decides. It now reports what has actually been observed. The server names
  its brain too, on the `config loaded` line that prints at boot rather than only on reload — a
  server that boots and is never reloaded, which is every server, used to print nothing.

### Building

- **It builds the part of the house it can afford.** A 728-cell house billed at 486 planks, 180 stairs
  and 34 glass doesn't fit an inventory, so the pre-flight refused it — and kept refusing, for
  twenty-three minutes. It now starts whenever it can pay for some of what's left, stops when
  materials run out, and resumes on the same description.
- **It builds with the wood you have.** Carrying 193 birch logs and no oak, it refused a "small wooden
  home" until told birch would do. Naming a species still wins.
- **It crafts slabs instead of taking apart a village.** Asked for 200 oak slabs while carrying the
  planks to make them, it walked to the nearest village and started removing roofs.
- **Two build livelocks fixed** — one where it stood in the cell it was trying to fill and deferred it
  forever, one where a stalled station couldn't time out because the counter measured walking rather
  than progress.

### Smaller things

- **Fetching a companion that went too far.** Minecraft stops ticking an entity outside simulation
  distance, so a companion 198 blocks away couldn't run a task, couldn't walk, and wouldn't come back
  — four `/companion come` commands over nineteen minutes moved one less than two blocks. `come` now
  teleports when the companion isn't ticking.
- **Your companion can wear a real face.** A roster entry takes `skin.username` as well as
  `skin.file`. The name resolves once on the server through the same path player heads use and rides
  to clients as tracked data, so nothing needs installing per machine. An explicit `skin.file` wins.
- **`behavior.cannedFallback` is gone.** It was in the example config with three sample values and
  had never had a single line of Java reading it — anyone who set it was editing a dead string. A
  dead brain wants a diagnostic, not a persona line saying everything is fine.
- **`/companion remember` stopped writing to the wrong machine.** With `clientBrain` on,
  conversational memories went to your client while `remember` wrote to the server — split across two
  machines, and asking about one recalled nothing, silently.
- **The token HUD read zero all session.** Server counters are per-process, so with `clientBrain` on
  they stay at zero while your machine spends; each packet overwrote the real figures your client
  had, and a session that spent 101,101 tokens showed nothing. The client now counts its own, and the
  server stays quiet when it isn't paying.
- **Recall silently dropped memories on a cold embedder.** The 250 ms budget exists because recall
  normally runs on the server tick loop. On a client there's no tick to miss and no prefetch, so every
  recall embeds cold inside a ceiling built for the opposite case — two of seven recalls lost, the
  session's first turn and the first after a four-minute gap, which is exactly when you ask whether it
  remembers. Warm recalls took 45–56 ms. The client now uses the embedder's own timeout.

### Conversation history

- Two players whose companions shared a name shared **one history file**, reading and overwriting each
  other's conversations. History is now filed per owner.
- `server.persistHistory` (default on) can stop history surviving restarts. It overlaps the memory
  corpus and does the cross-session job worse — a companion re-reads its own paraphrases and comes to
  cite them as fact. Leave it on until you've confirmed the same details are landing in memory.
- The 64-message summary is written as a system note rather than as something the companion said.
- "Welcome back" is decided by whether the companion has met you, not by whether a file exists.

---

## 0.2.9 — Unfinished builds, part 2: it remembers where

Bundles PlayerEngine 1.0.79. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating.

### It can carry on with yesterday's house

0.2.8 stopped a returning build paying twice for the wall it had already put up. It still could not
*find* that wall again: what the companion knew about a half-built structure lived in memory for ten
minutes, and the things that interrupt a big build — quitting the world, a long gathering trip —
outlast that. Asked afterwards to carry on, it had only the conversation to go on, so it guessed. In
one measured case the half-built house was at (180, 62, -40) and its "continuation" was planned at
(191, 64, -39): a second house, eleven blocks away, priced as very nearly a whole new building.

A build that stops short is now written to disk — the plan, the position, and how much of it is
standing — **while it runs**, not only when it ends, since quitting and cancelling tear the task down
without running any of its endings. There is no expiry; the record is cleared when the build finishes.

```
Picking up where I left off — 379 of 550 blocks were already up.
```

Its status carries that record too, so it knows the house exists without having to remember the
conversation. Matching is by position rather than wording, so a rephrased request — or a typo — still
finds it.

One unfinished build is remembered per companion, and ignored in another dimension, since a plan pins
absolute coordinates.

---

## 0.2.8 — Voice switches itself on · Unfinished builds, part 1

Bundles PlayerEngine 1.0.74. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. Voice is on by default now; an existing config saying `"tts":
{"enabled": false}` stays off, so set it to `true` once if you want it.

### Starting the container is the whole setup

Voice needed two steps: run the Kokoro container, then find the config file and enable it. The second
is the one nobody got to — and on a dedicated server it meant shell access, since the settings screen
edits your own machine's copy rather than the server's.

The setup now ships with the mod, unpacked to `config/aicompanion/tts/` on first launch. The whole
procedure:

```
cd config/aicompanion/tts
docker compose up -d
```

Companions speak as soon as it answers. No config edit, no restart.

### It was waiting out speech that never happened

Voice was off by default for a reason: leaving it on made every companion slower.

The server could not know whether a line had actually been spoken — your machine fetches and plays the
audio, and the server cannot see whether anything there is listening on the Kokoro port. So it guessed:
a companion was treated as mid-sentence for roughly one second per 25 characters, and it will not start
its next reply while it believes it is still talking. With no container running that was pure loss —
five to nine seconds of silence per line, waiting out audio that never played.

Your machine now reports back, either "that line has finished" or "I have nowhere to play this", and
that is what releases the companion. It waits exactly as long as it is really speaking, and a machine
with no Kokoro server costs nothing: one refused connection, then it is left alone for five minutes.
`/companion reload` retries immediately.

**The container belongs on the player's machine, not the server's** — `http://localhost:8880` has to
mean something where you are sitting. On a dedicated server, either each player runs their own or you
point `tts.endpoint` at one everybody can reach.

### A half-built house asked to be paid for twice

Interrupt a big build and ask for it again, and the companion refused — insisting on hundreds of planks
it had already put into the walls. One case: a 13×15 house stopped at 287 of 550 blocks, then demanded
319 oak planks. Collecting them did not help, because the bill grew back to the full amount every time.

The affordability check ran before the companion had looked at the site, so it priced all 550 cells
against an inventory that had spent a third of itself on the first 287. It now walks to the site and
prices only what is genuinely left. A build holding enough for the remainder carries on; one that is
really short says something true — *"I've got 287 of 550 blocks up — I need 40 oak planks to finish
it"*.

Finding that house again after a restart is **0.2.9**.

---

## 0.2.7 — Healing costs food, and it knows when to run

Bundles PlayerEngine 1.0.69. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating — four new `behavior.*` settings are added to your existing config file
automatically, at the defaults described below. **Your companions will need feeding now** — read on.

### It was healing faster than anything could hurt it

A companion regenerated a full heart every half second, forever, for free. Near-death to full in ten
seconds. That is vanilla's *fastest* healing — the burst you get at full food and full saturation — and
for a companion it was the permanent baseline rather than a brief window.

The cause was a shortcut with a sting in it. Healing was ticked, but the hunger cost of healing wasn't,
on the reasoning that a companion with no reliable way to eat would starve. Except hunger draining is
the *only* thing that makes food fall, so food sat at 20/20 forever, which is exactly the condition for
the fastest regeneration — and, at the same time, the condition under which every attempt to eat is
refused as unnecessary. The shortcut created the problem it was protecting against.

Healing now costs food, the way it does for you.

### Eating, which turned out to have never worked at all

Making food drain revealed that nothing about eating actually functioned. Three separate faults, each
hidden by the one before it:

- **A meal gave nothing back.** Filling a hunger bar is something only players do, and a companion
  isn't one — so every item eaten was destroyed for no benefit.
- **The part of the mod that decides when to eat had never once run.** It was only ever consulted when
  it already had a job on, and the only thing that could give it a job was being consulted — a circle
  it could never get inside. Everything to do with feeding itself sat behind that.
- **Feeding itself never happened.** Automatic eating also worked by pretending to right-click, and
  that route is broken for a companion in a way that fails silently.
- **`eat` always refused.** With food pinned at 20/20 there was never a reason to eat, so the command
  answered "already full" every time it was asked.

All three are fixed. A companion now eats by itself — visibly, over the normal few seconds, with the
sound and the crumbs — and keeps going until it's full rather than taking one bite. Asking it to `eat`
does the same thing in one go instead of once per mouthful.

**It tops up while it's safe, rather than waiting until it's desperate.** The old rules only counted a
companion as hungry once it was badly hurt or nearly starving. Watching one stand at full health with
food to spare and a pack full of meat for the best part of a minute, then walk into a fight and die
still holding it, made the problem plain. It now tops up between fights instead.

**And it can eat mid-fight when it needs to.** Eating means holding food for a second and a half, and
until now anything else that wanted a weapon in hand would cancel the mouthful before it finished — so
a companion in trouble could reach for food repeatedly and never actually swallow. Combat now pauses
for the bite and picks straight back up, the way you'd swap, eat, and swap back yourself. It won't
stand there topping off to full while something is shooting at it, though; in a fight it takes what it
needs and gets back to work.

**Rotten flesh is good food for a companion**, and it now knows that. It never made them ill in the
first place — the nausea is something the game only applies to players — but the companion had
inherited a person's instinct to avoid it and treated it as a last resort. Since it's what actually
drops from the things a companion fights, that made it needlessly fussy. Spider eyes are still avoided,
and correctly: poison does affect them.

**It eats the moment hunger is what's stopping it healing.** Below 18 food nothing regenerates, so a
companion sitting just under that line is not peckish — it's stuck at whatever health it has until it
eats. It now treats that as urgent and eats regardless of how much of the meal goes to waste; three
points of pork is worth less than four hearts.

**And it understands why it isn't healing.** Below 18 food nothing regenerates at all, so a companion
that's hurt and hungry sits at the same health indefinitely. It couldn't work out that eating was what
unblocked it — health and hunger were both in front of it, but not the connection between them. It's
now told plainly when it isn't healing and what to do about it, so a hurt companion reaches for its own
food instead of standing there waiting to feel better.

**It picks up food it walks past, and clears up after a kill.** A companion only ever collected what it
was standing directly on, so anything that landed a step away stayed there — it could clear out a herd
of pigs and go hungry later beside the pork it earned. It now walks over and collects dropped food
within 16 blocks. Only food, only once nothing is hunting it, only with a free slot, and never in
preference to a job you've given it, so the tidying happens when the work is done rather than instead
of it. Spider eyes are left alone; poison affects them even though rotten flesh doesn't. Turn it off
with `behavior.scavengeFood`, or change the range with `behavior.scavengeRadius`.

It still won't go foraging on its own — `food` remains something you ask for. That's deliberate for
now; a companion that wanders off hunting on its own initiative is a bigger change than this one.

**Effort makes them hungry, not just injury.** Healing was the only thing that cost a companion
anything, which meant one that stayed unhurt could cross the world, mine out a hillside and win a run
of fights entirely for free — a test run had saturation sitting at exactly the same number through
eight minutes of hard work. Sprinting, swimming, jumping and swinging a weapon now cost what they cost
you. Plain walking is still free, same as it is for you.

**A hungry companion stops healing and waits. It will not starve to death.** Running out of food has a
real consequence without turning "you went to bed" into a corpse in the morning.

Hunger resets when the world reloads, deliberately. The cost that matters is the one inside a session,
and starting a session already starving through nobody's fault is worse than the exploit of quitting to
top your companions up.

### A readout for how they're doing

New panel in the top-right corner, one line per companion:

```
  Ava    ██████████ 15   ██████████ 17
  Rook   ██████████ 20   ████░░░░░░  9
```

Health on the left, hunger on the right, with the number beside each. The health bar goes green →
amber → red as it drops. The hunger bar carries a thin lighter line across the top for saturation —
that's the hidden buffer healing spends before hunger itself moves, so it's the first thing to watch if
you want to see a fight costing them something.

It stays out of the way by default, appearing only when somebody is hurt or getting hungry and fading
again once they're fine. `/companion hud` cycles that to always-on, then off entirely, then back.

Works at any distance — it rides the same signal as the locator bar, so a companion off working across
the map still reports in. A row greys out and marks itself if it's gone quiet or is in another
dimension, rather than silently showing you a stale reading.

### It wears the armour you give it

Hand a companion a full set of diamond and it used to carry it around and keep fighting in its shirt.
Armour was only ever put on if you asked for it by name, and nothing said otherwise — the set just sat
in the pack.

It now puts on anything better than what it's wearing, checking about once a second. Defence, toughness
and Protection all count, so it won't swap a good piece for a worse one, and whatever comes off goes
back into the pack rather than being lost. Durability is ignored on purpose: a nearly-broken diamond
helmet protects exactly as well as a fresh one right up until it breaks.

Turn it off with `behavior.autoEquipArmor` if you'd rather decide yourself.

### And it can finally use a shield

A companion has always had the code to raise a shield — against a swelling creeper, against incoming
arrows, against something in melee range. None of it had ever worked. Give a companion a shield and it
would keep it in the pack forever and take every hit unblocked, while the mod believed it was blocking.

Three faults, stacked:

- **The shield never reached the offhand.** The routine meant to put it there had an off-by-a-whole-list
  error and was writing into the first slot of the pack instead. So every shield check sat permanently
  at "no shield in the offhand yet", shuffling the pack each tick and never getting anywhere.
- **Raising it did nothing.** Blocking was done by pretending to hold right-click — the same dead route
  that stopped companions eating, in a second place. It only ever does anything while the companion is
  looking directly at a block, and even then it fails silently.
- **It braced for a hit it wasn't blocking.** Whenever raising the shield failed, the companion still
  stopped moving, crouched, and stood its ground in front of whatever was attacking it. All of the
  commitment, none of the protection. It now only holds still if the shield is actually up.

All three are fixed, and a carried shield now lives in the offhand permanently rather than being
scrambled for once the arrow is already in the air. If you've put something else in the offhand, that
stays — the shield waits.

**Shields wear out now, too.** Blocking worked out to be free: damage was stopped exactly as it is for
you, but the shield never lost durability and could never break. That's a permanent advantage no player
has. A companion's shield now takes the same wear yours does, and breaks the same way.

**And eating beats blocking.** Raising a shield and eating are the same kind of action underneath, and
only one can be happening at a time — so the first thing working shields did was stop a companion mid-
fight from ever getting a mouthful in. It would set out to eat, be quietly refused, and stand there
hungry with the shield up. Food wins that tie now: the shield drops for the second and a half the meal
takes and is back up straight after.

`behavior.defenseUseShield` turns the whole thing off, and correctly makes it fight more cautiously,
since it knows it's going in without one.

### It runs when it's hurt, not when its gear looks weak

The decision to retreat never once looked at how injured it was. It compared its equipment against how
many things were attacking it — so a companion on its last heart with a diamond sword would stand and
fight a zombie, while an uninjured one with empty hands would run from the same zombie.

That was survivable while it could stunlock anything it touched. 0.2.6 took that away, and left nothing
that made a hurt companion disengage.

Injury now counts. The same equipment picks a smaller fight when the companion is half dead, and below
a quarter health it runs regardless of what it's holding.

It's also braver than it was. The sums behind that decision came from a speedrunning bot, whose best
play is to dodge every fight it possibly can — taken at face value it reckoned an unarmoured companion
with a wooden sword could handle exactly one hostile. In testing that meant fleeing, at full health,
from a spider and a zombie it then killed without difficulty the moment being cornered forced it to
try. Companions have a player's stat line now, and a player at full health handles two ordinary mobs
comfortably, so the maths now credits the body itself and not just the kit. Tune it with
`behavior.defenseBravery` — 2.0 by default, 0 for the old caution, higher for a companion that stands
and fights.

There's no "return to battle" behaviour and deliberately so. It heals as it retreats, and the same
judgement runs continuously — so if something chases it and it has recovered enough to win, it turns
and fights, exactly as it would have to begin with. Still hurt, it keeps going. What you'd expect
happens without a system built to make it happen.

### Talking to all of them at once

Addressing one companion by name has worked for a while — `Ava: follow me` reaches Ava and nobody
else. There was no way to say something to the whole group short of saying it twice.

Open with **`all:`** and every companion in earshot gets it:

```
all: team up and fight together
everyone: back to base
both: stop what you're doing
team: spread out and look for the cave
```

`all`, `everyone`, `both` and `team` all work, and each companion is told the line went to the group
rather than to them alone — so they divide the work instead of both doing the same thing, and don't
answer on each other's behalf.

The colon (or a comma) is required, and that's deliberate: those four are ordinary ways to start a
sentence, and without it "all good" or "both of us made it" would fan out to the whole roster and buy
a reply from every one of them. A name doesn't need the colon — `Ava follow me` still works — because
a name rarely opens a sentence that isn't aimed at that companion.

Worth knowing this is the one form that costs a reply per companion, so the game tells you who it went
to when you use it.

### Cornered means fight

Backed into a dead end, walled in, or fenced, a retreat that gets nowhere for two seconds turns into a
fight. Standing still while something hits you is strictly worse than swinging back.

This is decided instantly and locally rather than by asking the model — a companion that has to think
about whether to defend itself is dead before it finishes thinking.

### `stand_ground` — telling it a fight is worth it

New command, for the model rather than for you:

```
stand_ground        # 30 seconds
stand_ground 60     # up to 300
```

It suppresses retreat for that long, then expires on its own. This is the part worth handing to the
companion's judgement: it can't react inside a fight, but it can decide *beforehand* that this one
matters — holding a doorway while you get clear, protecting something, buying time. Self-preservation
returns by itself, so a companion can't be talked into dying for a fight everyone has forgotten about.

It's told plainly that it doesn't need this for being cornered; that's already handled.

You'll also see it mention when it retreats and why, so its decisions aren't silent.

**Note:** all of the above only bites if `behavior.defenseFleeFromHostiles` is on, which it still isn't
by default. Turn it on when you want a companion that runs.

---

## 0.2.6 — It fights like a person now

Bundles PlayerEngine 1.0.56. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. A new `combat` block appears in `aicompanion.json` with your existing
settings untouched. **Companions you already have will get weaker**, on purpose — read on.

### It was fighting with a zombie's stat line

A companion is built on a zombie's attributes, which is a sensible starting point for health and
movement and a bad one for combat. A zombie has **3.0 attack damage** where a player has 1.0, and
**2.0 armour** where a player has 0.0. Nothing announced this and nothing asked for it.

So before picking anything up, a companion hit three times as hard bare-handed as its owner, and that
advantage rode on top of every weapon — a diamond sword in its hand did 10 damage where the same sword
in yours does 8. It also walked around wearing two invisible points of armour.

All three are now exactly a player's. A companion is dangerous because of what it's holding, which is
the only reason it should ever have been dangerous.

Follow range came down too, from 35 blocks to 16. That number decides how far away it will notice
something worth fighting; 35 is a monster's aggro radius and is far enough that a companion standing
still starts fights with things nobody has seen yet.

### It was swinging four times a second, at full strength

Separate defect, same symptom, and this was the one making combat look like one-hit kills.

A weapon has a cooldown — the reason a real sword fight is a rhythm rather than a blur, and the reason
spam-clicking in vanilla does almost nothing. The companion's melee routine was told that cooldown was
a flat five ticks no matter what it held. A diamond sword's actual figure is twelve and a half.

The effect wasn't quite what it looked like. It wasn't hitting harder per swing; it was hitting **two
and a half times as often as the weapon allows, every one of them fully charged**. And because knockback
lands on every swing, whatever it was fighting got knocked back before it could act, then knocked back
again, and never got a turn. Nothing was being one-shot. Things were being held down until they died,
which is arguably worse to watch — the fight never looked contested.

It now reads the weapon's real attack speed, so a companion swings at the rate a player holding the
same thing would. The "hit everything in range" strategy, which isn't on by default, was also skipping
the cooldown entirely; it no longer does.

Deflecting ghast fireballs is deliberately left alone. That has no charge-up, a player does it by
spam-clicking, and the window is short enough that waiting out a cooldown means eating the fireball.

**Expect your companion to be noticeably worse in a fight, and to start taking real damage.** That is
the change working. It also means the eating, shielding and defensive behaviour is being properly
exercised for the first time — if something there misbehaves, this release is why it surfaced.

### You can make it stronger, on purpose

The four numbers are yours now, under a new `combat` block:

```
combat.attackDamageBase   (1.0 — player parity)
combat.armorBase          (0.0)
combat.maxHealth          (20.0)
combat.followRange        (16.0)
```

Raise them if you want a tougher companion for a hard modpack — that's a perfectly reasonable thing to
want. The point of this release isn't that companions must be weak, it's that the advantage has to be
asked for rather than handed over quietly. Changes apply to live companions on `/companion reload`, no
restart needed.

### It can no longer reset its own memory

Four commands were being offered to the companion as things it might choose to do: wiping its own
conversation memory, switching its own brain off, reloading settings, and a leftover beat-the-game
routine. Two of them already carried the note "can ONLY be run by the user (NOT the agent)" — which was
the only thing enforcing it, i.e. nothing was.

A companion talking itself into a fresh start and forgetting the afternoon is not a hypothetical worth
waiting for. They're no longer listed to the model, and they're refused if it asks for one anyway. You
can still run all four yourself; nothing you could do before has been taken away.

Smaller side benefit: four fewer commands described in full on every single request to the model.

---

## 0.2.5 — It can dig, it goes where you send it, and two of them stop taking turns

Bundles PlayerEngine 1.0.55. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. A new `staircase-mine` skill appears alongside the others; your existing
skill files are untouched.

### `dig` — a staircase you can walk back up

New command:

```
dig east 30     # cuts east, ending 30 blocks lower
dig 15          # 15 down, in whichever direction it's already facing
dig             # facing direction, 30 down
```

It descends at 45° — one block down for every block along — so the result is a stairwell you can walk
out of, not a pit you're stuck at the bottom of. It stops by itself at lava, at water, or near
bedrock, and tells you where it got to and how deep.

There was no digging command before. Excavation happened as a side effect of walking somewhere, which
worked but was discoverable by nobody — including the companion, which tried inventing `dig`, then
reached for `build_structure` (the opposite operation: it *places* blocks and costs materials out of
its inventory), then tried to run a skill name as a command. None of it produced a staircase.

The bundled **staircase-mine** skill now just asks where you want the entrance and which way, walks
there, checks it has a pickaxe, and issues one `dig`. Everything else is done for it.

### It goes where you send it

**Every `goto` in the mod travelled to world origin.** The destination was read before it had been
filled in, so the coordinates were always zero, while the command otherwise looked entirely
successful. Anything built on movement inherited it: "go to these coordinates" walked to spawn, and
the old staircase procedure aimed every step at the same wrong place — which is what was seen as a
companion setting off toward the horizon and never coming back.

Coordinates are also read more forgivingly now. Asked to go to a position it had just been shown, the
companion would echo it back in the game's own format — brackets, commas, long decimals — which the
command then rejected; one session spent 39 turns re-sending the same rejected string. Brackets,
commas and decimals are all accepted, and positions are reported as plain block coordinates.

### Two companions no longer take turns

With more than one out, only one could think at a time. Whichever was busiest held the floor: a
companion working a long task kept every other one frozen for the duration, so the second looked
broken while the first worked. Speech was part of it — one companion talking stopped every other one
thinking until it finished the sentence.

Each companion now thinks independently, and how many can do so at once is configurable
(`llm.maxConcurrentRequests`, default 2 — raise it for a hosted model or a bigger roster, since every
extra slot is another request running at the same time).

### It stops claiming to do things it isn't

Three separate versions of the same problem, all fixed:

- **"I'm following you"** while standing still. Asked to follow you, the companion would write your
  name in lowercase, the lookup was case-sensitive, and it matched nobody — then waited forever
  without saying so. Names now match regardless of case, and if the player genuinely can't be found it
  says so after a few seconds instead of pretending.
- **Talking sensibly and then doing nothing at all.** On a long session the companion would answer
  perfectly and never act. Its instructions had grown past what the model could hold, and the first
  thing lost was the part telling it how to phrase a command. Conversations are now trimmed to fit
  (`llm.maxPromptChars`), and a well-reasoned answer in the wrong format is recovered rather than
  discarded.
- **Repeating a command that could never work.** A command that failed was retried indefinitely — one
  session logged 39 identical attempts, another 30. After three the companion is told plainly that it
  will not work and to try something else or explain itself.

### Fewer wasted turns

- Invented a command that doesn't exist? The error now lists the ones that do, so it can correct
  itself on the next turn instead of guessing again.
- The list of your skills read to the companion as a second menu of commands, and it tried to run the
  skill names. It now says plainly that those are routines *you* start.

---

## 0.2.4 — It runs on a real server now, and they don't all sound alike

Bundles PlayerEngine 1.0.48. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating.

### It works on a dedicated server

Until now it didn't, and it failed badly: the companion spawned, stood there doing nothing, and the
first time you walked into it the server dropped you with *"Internal server error"*. Every version up
to this one was single-player-only in practice, whatever the description said.

The cause was code that only exists in the Minecraft client being reached by the server. In single
player the two run in the same process, so nothing ever noticed. On a dedicated server the companion's
AI threw on its very first tick and could never take one, and a separate piece of the same problem
crashed the player's connection on contact.

Both are fixed, along with two more of the same kind waiting further along — one in placing blocks,
one in the engine's logging. A companion on a dedicated server now spawns, paths, fights, gathers,
completes tasks and survives a restart.

If you run a server, note that its timings are measured in server ticks. A server running below 20
ticks per second stretches every wait the companion makes, so it will feel slower under load than it
does on your own machine. That is expected, not a fault.

### Each companion can have its own voice

Voice used to be one setting shared by everyone, so a roster of three was three identical voices —
which rather undoes having given them separate names, faces and personalities.

It now belongs to the companion. **`/companion config` → Companions**, and each one has a **Voice**
picker beside its skin, with the common Kokoro voices listed and searchable. `af_`/`am_` are American
female/male, `bf_`/`bm_` British, and you can type any voice id your setup serves if it isn't in the
list.

Leave it on *(use global tts.voice)* and nothing changes — that companion uses the shared setting
exactly as before. The Voice tab keeps that shared setting as the fallback.

### The config screen now admits when it can't reach the server

`/companion config` opens on a multiplayer server, lets you change anything, saves without complaint
— and changes nothing. It edits the copy of the config on *your* machine, and the server reads its
own.

It said nothing about this, and the log even claimed the changes would apply on the next world load,
which they never would. Every tab now says which file it is editing, in red when that file is going
nowhere.

To configure companions on a server, edit `config/aicompanion.json` **on the server** and run
`/companion reload`. Voice, personality, skins, LLM settings — all of it lives there. In single player
and on a LAN host the screen works exactly as it always has.

---

## 0.2.3 — More than one of them, and a way to hand them things

Bundles PlayerEngine 1.0.47. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. Your existing companion keeps its name, personality and skin — its
settings are moved into the new list format for you on first launch, and your previous config file is
kept as `aicompanion.json.bak` in case you want it back.

### Right-click a companion to open its inventory

Empty hand, no sneaking, and a window opens: all 36 of its storage slots, its four armour slots and
its offhand, with your own inventory below. Drag items across, shift-click them across, take things
back.

Until now there was no way to hand a companion anything. Items reached it only by being dropped on
the floor for it to notice, or by asking it to go and fetch them itself — and armour had no route at
all, despite the companion wearing armour, taking less damage for it, and wearing it out. Now you can
kit one out in ten seconds.

Shift-clicking armour or a shield from your side puts it straight into the slot it belongs in rather
than the first free storage slot. The window closes itself if the companion walks out of reach, and
only its owner can open it.

### You can have more than one, and tell them apart

Two companions used to be two copies of the same character: same name, same personality, same face,
both answering to everything you said. Chat showed the same name for both, and every command —
`stats`, `come`, `despawn`, `skill` — picked one of them arbitrarily and didn't tell you which.

Now identity comes from a list, and the easiest place to edit it is in game: **`/companion config` →
Companions**. Each configured companion gets its own section — name, description, personality, skin —
and there's an "Add a companion" box at the bottom. Type a name, save, and you can spawn it:

```
/companion spawn Rook
```

A bare `/companion spawn` takes the first companion that isn't already out, so with two configured
you can spawn both without naming either.

Once they're out:

- **Talk to one of them.** "Rook, go and scout north" reaches Rook and nobody else — the name is
  stripped before the model sees it. An unaddressed line goes to whichever is nearest, rather than to
  all of them at once, so asking a question once costs one reply instead of two.
- **Target commands.** Every subcommand takes an optional name: `/companion stats Rook`,
  `/companion come Rook`, `/companion skill Rook Harvest`. Without one you get the nearest of your
  own, chosen consistently rather than at random. And the replies now say who they mean — "Rook
  coming to 74, 64, 349" instead of "Companion coming to 74, 64, 349".
- **See who's out.** `/companion list` gives you every companion, how far away, how healthy, and what
  it's currently working on. Spawning one that's already out is refused, with a note on where it is —
  in a previous session someone assumed their companion had died, spawned a second, and ended up with
  two identical ones wandering the same valley.
- **The radar handles all of them.** One labelled marker each, instead of a single marker flickering
  between two bodies.

Configure one companion and nothing changes — it behaves exactly as before.

*(For anyone who hand-edits the JSON: identity used to live in a `companion` block. That is now the
`companions` list, and your old block is folded into it automatically. `/companion config` no longer
has an Identity tab because it edits the list directly.)*

### Companions no longer talk each other into circles

Two companions standing near each other used to overhear and answer each other, and each answer
prompted another answer. Every one of those is a full request to the model, with nobody talking to
them. One session logged 382 of them — including four near-identical sentences in ninety seconds as
each reply set off the next.

This is now off by default (`behavior.aiCrossTalk`). Turn it on if you want them chatting between
themselves and you're running a local model where turns are free.

---

## 0.2.2 — Plays by the same rules you do

Bundles PlayerEngine 1.0.46. Self-contained jar as always — don't install a standalone engine
alongside it.

Nothing to do after updating. The new settings are added to `config/aicompanion.json` automatically and
all default to sensible values.

### Mobs can see it now

- **Hostile mobs hunt the companion the way they hunt you.** They never did before. The companion is a
  `LivingEntity` rather than a real player, and every vanilla mob looks for targets through a
  hard-coded player filter — so zombies, skeletons, spiders and creepers walked straight past it. The
  only way it ever got attacked was retaliation after it swung first. Covers the goal-based hostiles
  plus piglins and hoglins; piglins honour the gold-armour truce, so kit it out and it can walk the
  Nether. Endermen still only aggro on real players staring at them.
- This also switched on the whole defence system, which had **never run once** in a real world: all of
  it was gated on "is a mob targeting the companion", which was permanently false. Rather than ship
  that untested, it is behind four settings — fighting back and shield use are on, **running away is
  off**. The companion stands its ground and keeps working instead of abandoning a farm to sprint over
  the horizon. Turn `behavior.defenseFleeFromHostiles` on when you want to see the other behaviour.

### Gear wears out

- **Fixed: swords and axes never lost durability.** Mining wore tools down correctly, but melee did
  not — the attack path was hand-written and never called the hook that damages a weapon on hit. A
  companion's sword was effectively unbreakable.
- **Fixed: armour never wore out either**, for the same class of reason. It absorbed damage forever.
- **Fixed: it swung far too fast.** Attack cadence was a flat 5 ticks regardless of weapon, at full
  damage every time — roughly 4 swings a second where you get 1.6 with the same diamond sword. It now
  uses the real attack-speed attribute and scales damage by cooldown like a player does.
- Melee reach tightened from 4 blocks to the vanilla 3.
- Known gap: **shields still don't wear out.** Fishing rods, shears, hoes and flint & steel always did.

### Buildings get built instead of appearing

- **`build_structure` now builds with its hands.** It walks to the site, picks somewhere to stand that
  the plan doesn't need to fill and that it can get back out of, and places a couple of blocks a tick
  within arm's reach — swinging, making placement noise, and moving along as each spot is used up.
  Previously up to 256 blocks a tick with no reach check and no walking, which meant a small house
  materialised in a single tick at coordinates the model named from anywhere on the map.
- Courses go bottom-up and outside-in, so it never stands where a block has to go and a flat roof
  partly scaffolds itself. Doorways and windows are carved **first** — that's what stops it sealing
  itself inside a house cut into a hillside.
- If it genuinely can't reach the rest (the middle of a wide roof), it says so and finishes those from
  where it stands rather than stalling. If it gets shut in anyway, it digs out — and isn't charged
  twice for the blocks it has to replace.
- **A partly-finished build now says it's partly finished** and can be resumed by repeating the same
  description; it places only what's missing. Interrupting a build mid-way keeps the plan instead of
  throwing it away.
- Duplicate positions in a generated plan are collapsed, which also stops the material bill charging
  twice for one block.
- Fixed a bug this exposed: retrying a build re-measured the ground against the part already standing,
  read its own roof as ground level, and could refuse a job that was half done.
- `behavior.buildPhysicalPlacement: false` restores the old instant behaviour exactly, if you want it.

### Farming and harvesting replant again

- **Fixed: harvested tiles were left bare, and the companion insisted it was out of seeds while
  carrying hundreds of them.** Ask it to tend a field and it would strip the wheat, plant nothing, and
  answer *"I need to get more wheat seeds"* with 418 of them in its pockets. Telling it otherwise
  didn't help — it genuinely could not see them.
- The cause was that the farm only ever looked at the companion's **hotbar**, nine of its thirty-six
  slots. Seeds anywhere else were invisible, and since planting needs the stack in hand, a full
  backpack of seeds could never be used. Anything the companion picked up while working — which is
  most of what it carries — landed out of view.
- It now searches the whole inventory and moves a stack into its hand when it finds one, the way you
  would. Bone meal had exactly the same blind spot and is fixed with it, so it will fertilise from the
  backpack too.

---

## 0.2.1 — Skills that can finish a sentence, and building underground

Bundles PlayerEngine 1.0.44. Self-contained jar as always — don't install a standalone engine
alongside it.

### ⚠️ Two things to do after updating

**1. Run this command:**

```
/companion skills reset farming
```

The Farming skill changed in this version, and a copy already on disk is never overwritten
automatically — that protects skills you have edited yourself. Without this it keeps tending the wrong
field (see below). The command backs the old one up to `farming.md.bak`. Fresh installs need nothing.

**2. Open `config/aicompanion.json` and set:**

```json
"llm": { "maxTokens": 1000 }
```

The old default of `200` was too small for the skills to work — see below. New installs get 1000
automatically; an existing config keeps whatever it has, so this one is manual. The companion now
tells you in red, on every message, while the value is too low.

### The farming skill could never run

- **Fixed: skills no longer get cut off mid-reply.** A skill hands the companion a command to
  reproduce word for word, and the farming one is about 700 characters. At the old default token
  limit the reply was truncated part-way through that command every single time, so no command ever
  ran — the companion simply stood there having said it was starting. Four attempts, nothing built,
  and the only clue was a JSON parse error in the log.
- **A cut-off reply now says so.** It was previously reported — to you and to the companion — as
  "not valid JSON", which is wrong and unfixable: the reply was fine, it just stopped early. The
  companion would re-send the same too-long command and be cut off again. It's now named properly in
  chat and in the log, and the companion is asked to be brief instead.
- **The default token limit is now 1000, up from 200.** This costs nothing: the limit is a ceiling,
  not a budget — you're billed for what's actually generated, and a short reply is short either way.
  Use `llm.maxRequests` and `behavior.maxAutonomousTurns` to control spend.

### Farming stops talking over you

- **A tended field no longer repeats itself every few seconds.** Waiting for crops to regrow used to
  print "standing by for regrowth" and "pass complete: harvested=0, sown=0" on a loop — about 120 lines
  in half an hour, all saying the same nothing, and loud enough to bury messages that mattered. It now
  says why it's standing still once, then stays quiet until something actually changes. A *different*
  reason, like running out of seeds, still comes through straight away, and progress reports during an
  active pass are unchanged.

### It tends the field it just built

- **Fixed: after building a field it would tend whichever one happened to be nearest.** Between
  finishing the build and starting to farm there's a pause while it thinks, and it walks back to you
  during that pause — so "now tending the crops" could mean a field 37 blocks from the one it had just
  made. This went unnoticed whenever an older field was nearby; build somewhere new and it would find
  nothing to do while reporting success. `farm` now takes the field's coordinates, and the skill passes
  the ones it built at.
- Tending several fields at once still works exactly as before — the range applies around that point,
  so everything close to it gets worked in the same pass.

### A full inventory no longer brings it to a halt

- **It makes room for itself instead of getting stuck.** With a full inventory it could not pick
  anything up, and the gather that needed one more log just retried — in one session, 3,814 times over
  27 minutes, without placing a single block of the farm it was building. The one piece of code that
  frees a slot could only run if it was already touching the item it was trying to collect, which a
  full inventory is exactly what prevents. It now runs whenever there's no room.
- **It tells you.** That whole 27 minutes produced nothing in chat at all — you had to notice and ask.
  The companion now says "I can't pick anything up — my inventory is full", and tells its own reasoning
  the same thing, so it goes and deposits something rather than repeating the same request.
- **"It's unreachable" no longer means "I'm full".** Those are different problems and needed different
  answers; only one of them was ever reported.
- **It stops hoovering junk once full.** Walking around, it picks up every stray cobblestone and leaf.
  Once there's no free slot it will now only take things that stack onto what it's already carrying, so
  it stops spending its last slots on debris. Below that, collecting is unchanged.

### Building in caves

- **It can place blocks underground.** Ask for a crafting table in a cave and you got "I can't build
  that here — the plan came out 28 blocks underground" every single time, however you phrased it. It
  was measuring the ground from the sky, so anything with a roof over it — a cave, a mineshaft, a
  ravine, the inside of your base — read as buried under the whole hillside and was refused. It now
  measures the floor it is actually standing on. Building above ground is unchanged.
- **It stops arguing with itself about where the ground is.** The refusal used to quote a ground level
  that contradicted the reason it gave, so it would "correct" itself one block lower and fail again —
  six times in a row in one session before the owner told it to stop.
- **`groundLevel` is now the block it is standing on**, as the prompt always claimed. Underground it
  was reporting the surface far overhead, so every height it reasoned about down there was wrong —
  and standing on a rooftop over water it reported the water, seven blocks down, so a field built
  "at ground level" would have gone in the lake. It now asks the game which block is holding it up,
  which is also right when it's stood on the very edge of something.

### Also fixed

- **You get told when it's too far away to hear you.** Past 64 blocks your messages were silently
  dropped — indistinguishable from a dead companion or a broken model. It now says
  `(<name> is 154 blocks away and can't hear you — /companion come)`, at most once every 30 seconds.

---

## 0.2.0 — Farming that actually farms

Bundles PlayerEngine 1.0.39. Self-contained jar as always — don't install a standalone engine
alongside it.

### ⚠️ Run one command after updating

```
/companion skills reset harvest
```

The Harvest skill is rewritten in this version, and an existing copy on disk is never overwritten
automatically — that protects skills you have edited yourself. The command backs the old one up to
`harvest.md.bak`. Fresh installs need nothing.

### Farming

- **Every tile it harvests gets a seed back.** It now works the field in a zig-zag — up one row, back
  down the next — harvesting and replanting each tile before moving to the one beside it, instead of
  wandering the field and leaving it stripped bare.
- **It sees the whole field**, not just the part nearest to it.
- **Nothing stalls silently.** A tile it cannot reach is skipped and reported, and it says whether it
  is waiting for crops to regrow or genuinely stuck.
- **Dropped wheat and seeds get collected** instead of left to despawn.

### Also fixed

- It no longer swaps the item in its hand many times a second while farming, which was pulling seeds
  out of its own hand as it tried to plant them.
- `scan` works — it previously could not find any block at all.
- Telling it something mid-thought now takes precedence over whatever it was about to do.
- It stops inventing chores once your request is done. Tune with `behavior.maxAutonomousTurns`
  (default `2`, `0` = unlimited).

Tested end to end against a local `Qwen2.5-14B-Instruct-Q4_K_M.gguf` — a quantised 14B model on
consumer hardware handles all of this comfortably.

---

## 0.1.9 — Builds that finish, and a companion that dies like a player

Bundles PlayerEngine 1.0.38. Self-contained jar as always — don't install a standalone engine
alongside it.

The headline is that **builds actually complete now**. Three separate defects were stopping them, and
one of them could take your world down with it.

### ⚠️ Existing configs — one line worth changing

If you have been running an earlier version, open `config/aicompanion.json` and set:

```json
"llm": { "useGrammar": true }
```

It became the default in 0.1.8, but your existing config is preserved on upgrade rather than
overwritten, so an older install is still running with it **off**. It matters: in a measured run, 9 of
21 turns came back unparseable without it and 0 of 6 with it. The symptom is the companion cheerfully
narrating work it never actually starts. Fresh installs already get the right value.

### Fixed

- **A crash that closed your world.** If the companion was holding stairs, doors, fences, chests or any
  other block that has a facing, block placement could throw and end the session — the integrated
  server died and the world shut. Fixed at the cause; placement now also orients blocks correctly
  instead of always facing north.
- **A bug in the companion can no longer take the server down with it.** Anything thrown by the AI is
  caught, logged and reported, and that tick is skipped. After repeated failures the companion switches
  its own AI off and tells you to run `/companion reload`, rather than throwing twenty times a second.
- **Builds could not gather wood.** A defect in the resource catalogue meant `oak_planks`, doors,
  stairs, slabs, fences, trapdoors, boats and signs did not exist as gatherable resources for *any*
  wood type — so a house that needed planks would fetch a torch and give up. `get oak_planks 8` did not
  work either. Generic `get planks` and `get log` always worked, which is why this hid for so long.
- **The companion no longer reports work it did not do.** A build that failed on materials could report
  as finished, and the companion would tell you the house was done when not one block had been placed.
  The outcome now travels with the completion event, so a failure survives into the conversation
  instead of being visible for a single turn.
- **A build that runs out of materials keeps its plan.** Previously each retry designed a *different*
  building with a different bill of materials, so fetching what it asked for never helped — by the time
  you had the logs it wanted cobblestone. Collecting the shortfall and asking again now finishes the
  same structure.

### New

- **The companion drops its inventory when it dies**, exactly like you do. Everything it was carrying
  used to vanish with it, which got worse in 0.1.8 when building started costing real materials — a
  companion killed on the way home took the whole load with it. It now drops the lot (inventory, armour
  and offhand) where it fell, and tells you the coordinates and how many stacks are there so you can go
  and collect them. Note that `keepInventory` does not apply: a companion has no respawn to carry items
  over to, so it always drops.
- **Common structures are built without asking the model.** Houses, crop fields, and walls, paths and
  bridges are now generated in-process. On a paid endpoint that saves roughly 4,000 tokens of prompt
  plus the reply and a full round-trip on every one of those builds; on a local model it is simply
  faster. Anything the templates don't recognise — an L-shape, multiple rooms, a request naming stairs
  or slabs — still goes to the model exactly as before, so nothing you could ask for previously stopped
  working. Templates place full blocks only: doorways are open gaps and roofs are flat.
- **You can see what it's doing.** Gathering reports progress in chat, shortfalls say what is missing
  (*"I can't build that yet — I still need 100 oak planks and 1 red bed"*), and a finished build reports
  how many blocks it placed.

### Known issues

- **Freeform builds still don't converge.** Anything the templates decline goes to the model, which
  designs it without being able to see the inventory — so "build it with the materials you have" cannot
  be honoured, and the companion goes gathering instead. L-shaped and multi-room houses are the common
  case. Being worked on; templated shapes are reliable in the meantime.
- **Incidental pickup fills the inventory.** The companion picks up anything that lands near it, so it
  accumulates cobblestone and leaf drops until it has no room to gather what a build needs. Clearing it
  out means asking for one item type at a time. A bulk drop is planned.
- **`farm` does not stop on its own** — it runs until you tell it to stop.
- **Doors and stairs in model-designed builds place wrong** (a door as its broken lower half, stairs
  always facing north). Build plans cannot carry block states yet. The built-in templates avoid this by
  using full blocks only.
