# Sense client regression tests

These standalone Java 17 source checks do not run Gradle or start Minecraft. Run them with an existing Minecraft/Fabric dependency classpath file and a dedicated output directory outside the repository:

```powershell
./tests/sense-client/run-tests.ps1 -DependencyClasspathFile <existing-classpath-file> -OutputDirectory <isolated-output-directory>
```

`EarthSenseSessionTest` compiles the production session and protocol against the real Minecraft `PacketByteBuf`. Every state fixture passes through the wire encoder and decoder. Cases cover token and sequence replay, dimension mismatch, cancellation followed by late starts, terminal-state resurrection, heartbeat expiry and renewal, retained signals during airborne grace and shutdown fade, reconnect cleanup, and token overflow.

`EarthSenseTransitionLifecycleTest` combines the production session and transition state with real wire roundtrips. It covers silent pending requests, denials and pending timeouts; one entry after authoritative confirmation; no entry on duplicate/old/foreign states or contact changes; exactly one exit on normal token-incrementing stops or server termination; silent disconnect/world resets; and rapid confirmed/unconfirmed switches. `EarthSenseTransitionStructureTest.mjs` checks that the production receiver, stop and end-tick paths use those same boundaries, including silent world cleanup before accepting a new-world packet that could otherwise borrow the previous session. These checks do not exercise the sound device or asynchronous audio decoder.

`AbilityClientTest` compiles the production ability dispatcher against small fake clients. It checks stable ability IDs independently from visible wheel slots, all built-in and unknown races, unsynchronized and empty states, Earth pony slot zero dispatch, explicit selection, server-acknowledgement boundaries, rapid A → B → A changes without an intervening activation, revision invalidation, same-race synchronization, reconnect cleanup, cancellation of pending sense, and remote/sense stop priority when switching abilities. The fake clients count start requests; they never grant active state automatically.

`AbilityWheelStructureTest.mjs` checks the production integration points: authoritative own-race updates and connection cleanup invalidate selection; both render and key-release paths refresh the revision; labels, highlights and selection use the visible-slot mapping. It also checks both translations of the empty-wheel explanation. The runner uses Node.js from `PATH`, or an explicit `-NodeBin`.

`EarthSenseFocusStructureTest.mjs` verifies that opening the wheel stops focus before the screen appears, any open screen bypasses forced crouch, and end-of-tick cleanup also covers menus. It checks owned-input restoration on stop, reset and accepted server termination, preservation of the manual sneak binding, connection identity, mixin registration, and duplicate-free press/release commands that update vanilla's last-sent state without forcing collision poses. It reads the existing Minecraft classes with `javap` to verify the physical keyboard update, tail injection, movement tick, vanilla sneak-packet order and paused entity-tick guard. This validates why the explicit cleanup is needed when normal player ticking is paused; it does not compile or run Minecraft.

`EarthSenseFocusInputTest` checks slow crouch movement, preservation of direction keys, the first-tick crouch factor, no repeated crouch penalty, jump-to-exit behavior and invalid inputs. `EarthSenseFocusEnvelopeTest` covers the 9-tick entry and 5-tick exit curves, reversal from the current value, and motion quality independent of focus opacity. These durations are this mod's tuning values, not measurements from another game.

The existing `tests/config/run-tests.ps1` also runs `GameplaySenseFilterTest`. It covers the 0 / 0.4 / 0.8 presets, numeric clamp and invalid values, load without rewriting existing files, preservation of unknown fields and appearance configuration, failed saves and retry, and conservative bilingual text/control collision checks at 320×240, 400×240, 640×360 and 854×480.

The layout checks use conservative default-font advances, not rendered screenshots. These tests do not validate in-game shader output, world vibration detection, server permission enforcement, latency in a live connection or resource-pack fonts. Logs, exact Java arguments and source hashes are written under the requested isolated output directory.

`EarthSenseViewMathTest` checks the temporary +6° FOV and 85% look scaling throughout the fade, including invalid values and camera scope. `EarthSenseViewHookTest` reads real Minecraft bytecode to verify world-versus-hand FOV calls and the final mouse delta after vanilla smoothing, spyglass handling and inversion. It also checks menu, remote-camera and option-write boundaries. Both run through the same entry point above; neither starts Minecraft.
