import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const client='src/client/java/top/csituka/magicaland/gameplay/client/';
const wheel=read(client+'AbilityWheelScreen.java');
const race=read(client+'race/RaceClient.java');
let checks=0;
function check(value,message){checks++;assert.ok(value,message);}
function method(source,signature){
    const found=source.indexOf(signature);check(found>=0,`method exists: ${signature}`);
    const start=source.indexOf('{',found);let level=1,end=start+1;
    while(end<source.length&&level){if(source[end]==='{')level++;if(source[end]==='}')level--;end++;}
    check(level===0,`method braces balance: ${signature}`);return source.slice(start+1,end-1);
}
const stateStart=race.indexOf('registerGlobalReceiver(RaceProtocol.STATE');
const stateEnd=race.indexOf('registerGlobalReceiver(RaceProtocol.EFFECT',stateStart);
check(stateStart>=0&&stateEnd>stateStart,'state receiver is independently located');
const state=race.slice(stateStart,stateEnd);
const changed=state.indexOf('AbilityClient.raceChanged(');
check(changed>=0,'authoritative race state calls selection invalidation hook');
check(/AbilityClient\.raceChanged\(view\s*==\s*null\s*\?\s*null\s*:\s*view\.ownRace\(\),\s*incoming\.ownRace\(\)\)/.test(state),'race hook compares prior and incoming own identity, not rule revision or remote players');
check(state.indexOf('client.getNetworkHandler() != handler')<changed&&state.indexOf('client.getNetworkHandler() != handler')>=0,'stale connection state cannot invalidate current selection');
check(changed<state.indexOf('view = incoming'),'previous own identity is read before authoritative view replacement');
const clear=method(race,'private static void clear()');
check(clear.includes('AbilityClient.reset()'),'connection cleanup resets selection and hover revision');

const refresh=method(wheel,'private void refreshRace()');
check(/raceRevision\s*!=\s*AbilityClient\.revision\(\)/.test(refresh),'wheel compares recorded revision with ability revision');
check(/raceRevision\s*=\s*AbilityClient\.revision\(\)/.test(refresh),'wheel records updated revision after invalidation');
check(/hovered\s*=\s*hoveredAbility\s*=\s*-1/.test(refresh),'race change clears both geometric and mapped hover');
const tick=method(wheel,'public void tick()');
check(tick.indexOf('refreshRace()')>=0&&tick.indexOf('refreshRace()')<tick.indexOf('wheelHeld()'),'release invalidates stale hover before selection');
check(tick.includes('AbilityClient.select(hoveredAbility)')&&!tick.includes('AbilityClient.select(hovered)'),'release selects stable ability ID from mapped hover');
const render=method(wheel,'public void render(');
check(render.indexOf('refreshRace()')>=0&&render.indexOf('refreshRace()')<render.indexOf('hoveredAbility='),'render invalidates old hover before calculating new visible mapping');
check(render.includes('hoveredAbility=AbilityClient.wheelAbility(hovered)'),'geometric hover uses visible slot mapping');
check(render.includes('visibleCount=AbilityClient.wheelCount()'),'visible labels use current-race count');
check(/for\s*\(int slot=0;slot<visibleCount;slot\+\+\)/.test(render),'label loop excludes unowned abilities');
check(render.includes('AbilityClient.name(AbilityClient.wheelAbility(slot))'),'visible labels use mapped stable ability ID');
check(render.includes('AbilityClient.wheelAbility(sector)>=0'),'sector highlight follows visible mapping, including Earth pony slot zero');
check(!wheel.includes('AbilityClient.count()')&&!wheel.includes('AbilityClient.available(sector)'),'wheel never treats stable registry positions as visible positions');
check(render.includes('AbilityClient.wheelEmptyMessage()'),'empty race wheel displays an explanation');
for(const language of ['en_us','zh_cn']) {
    const texts=JSON.parse(read(`src/main/resources/assets/magicaland_gameplay/lang/${language}.json`));
    check(typeof texts['text.magicaland_gameplay.wheel.empty']==='string'&&texts['text.magicaland_gameplay.wheel.empty'].length>0,`${language} empty wheel message is translated`);
}
console.log(`PASS AbilityWheelStructureTest: ${checks} state-hook and visible-wheel integration checks (not a rendered game test)`);
