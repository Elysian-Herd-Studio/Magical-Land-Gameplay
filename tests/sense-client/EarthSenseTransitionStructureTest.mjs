import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const sense=read('src/client/java/top/csituka/magicaland/gameplay/client/sense/EarthSenseClient.java');
let checks=0;
function check(value,message){checks++;assert.ok(value,message);}
function method(signature){
    const found=sense.indexOf(signature);check(found>=0,`method exists: ${signature}`);
    const start=sense.indexOf('{',found);let level=1,end=start+1;
    while(end<sense.length&&level){if(sense[end]==='{')level++;if(sense[end]==='}')level--;end++;}
    check(level===0,`method braces balance: ${signature}`);return sense.slice(start+1,end-1);
}
const init=method('public static void init(');
check(init.includes('ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset())'),'joining starts with silent transition cleanup');
check(init.includes('ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset())'),'disconnect cleanup does not route through an audible stop');
const callback=init.slice(init.indexOf('client.execute(() -> {'));
const identity=callback.indexOf('client.getNetworkHandler() != handler || client.world == null');
const world=callback.indexOf('world != client.world');
const accept=callback.indexOf('if (!SESSION.accept(');
const transition=callback.indexOf('EarthSenseTransitionSound.tick(SESSION.active(), SESSION.token());');
check(identity>=0&&world>identity&&accept>world,'connection and world identity are checked before accepting state');
check(/if \(world != client\.world\)\s*\{\s*reset\(\);\s*world = client\.world;\s*return;\s*\}/.test(callback.slice(0,accept)),'a state arriving in a changed world clears silently and cannot borrow the previous session');
check(accept>=0&&transition>accept&&/if \(!SESSION\.accept\([^;]+\)\) return;/.test(callback.slice(accept,transition)),'only accepted authoritative state can trigger a transition');
const toggle=method('public static void toggle(');
check(toggle.includes('SESSION.begin(true);')&&!toggle.includes('EarthSenseTransitionSound.'),'a pending start never plays an entry sound before acknowledgement');
const stop=method('public static void stop(');
check(stop.indexOf('if (!SESSION.wanted() && !SESSION.active()) return;')<stop.indexOf('SESSION.begin(false);'),'duplicate local stops leave the transition state untouched');
check(stop.indexOf('SESSION.begin(false);')>=0&&stop.indexOf('EarthSenseTransitionSound.tick(false, SESSION.token());')>stop.indexOf('SESSION.begin(false);'),'local stop transitions after token increment, allowing one exit for the preceding active token');
const tick=method('private static void tick(');
check(/if \(world != client\.world\)\s*\{\s*EarthSenseTransitionSound\.reset\(\);\s*stop\(\);\s*reset\(\);\s*world = client\.world;\s*\}/.test(tick),'world changes silence transition state before stop can produce an exit');
check(/if \(client\.player == null \|\| client\.world == null\)\s*\{[\s\S]*?EarthSenseTransitionSound\.reset\(\);\s*return;\s*\}/.test(tick),'missing player or world silently clears any owned sound');
check(tick.includes('EarthSenseTransitionSound.tick(SESSION.active(), SESSION.token());'),'end tick follows authoritative active state, never pending or visual fade opacity');
check(!/EarthSenseTransitionSound\.tick\([^;]*(?:wanted\(|pending\(|opacity)/.test(sense),'transition triggers are independent of pending requests and lingering visual fade');
const reset=method('private static void reset(');
check(reset.includes('SESSION.clear();')&&reset.includes('EarthSenseTransitionSound.reset();'),'reset invalidates session and stops transition audio');
check(!reset.includes('EarthSenseTransitionSound.tick(')&&!reset.includes('stop();'),'reset never emits an exit transition');
console.log(`PASS EarthSenseTransitionStructureTest: ${checks} authoritative-state, token and silent-world-cleanup integration checks`);
