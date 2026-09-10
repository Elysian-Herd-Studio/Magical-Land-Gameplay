import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const server=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/remote/RemoteToolServer.java');
const client=read('gameplay/src/client/java/top/csituka/magicaland/gameplay/client/RemoteToolClient.java');
const entity=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/remote/RemoteToolEntity.java');
let checks=0;
function check(value,message) { checks++; assert.ok(value,message); }
for (const guard of ['Float.isFinite(yaw)','Float.isFinite(pitch)','buf.isReadable()',
    'session.tool.getId() != id','getCommandTags().contains(GRANT)','ACTIVE.size()>=64',
    'isChunkLoaded','getWorldBorder().contains','MovementType.SELF','visible(p,tool.getEyePos())',
    'now-s.inputTick > 40','p.getInventory().selectedSlot!=s.slot','ItemStack.areEqual',
    'AttackBlockCallback.EVENT','AttackEntityCallback.EVENT','UseBlockCallback.EVENT',
    'interactionManager.tryBreakBlock','getAttackCooldownProgress(0)>=1','world.canPlayerModifyAt'])
    check(server.includes(guard),`server guard: ${guard}`);
check(!/new ItemEntity|\.copyAndEmpty\(/.test(server),'no cloned item entity');
check(server.includes('s.cargo.addStack(item.getStack())') && server.includes('item.setStack(remainder)'), 'only accepted items leave the world stack');
check(!server.includes('setNoGravity(') && !server.includes('cargo.setPosition'), 'no attached dropped item or cross-dimension teleport');
check(server.includes('session.cargo.returnTo') && server.includes('GameRules.KEEP_INVENTORY'), 'return and death inventory rules');
const cargo=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/remote/RemoteCargoInventory.java');
check(cargo.includes('extends SimpleInventory') && cargo.includes('super(1)') && cargo.includes('CAPACITY = 8'), 'one vanilla inventory slot, eight item cap');
check(cargo.includes('source.getMaxCount()') && cargo.includes('super.addStack(offered)') && cargo.includes('slot.insertStack(remainder)'), 'bounded vanilla insertion and non-destructive return');
const saved=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/remote/RemoteCargoState.java');
check(saved.includes('extends PersistentState') && saved.includes('toNbtList()') && saved.includes('readNbtList'), 'world-owned persistent cargo');
const body=read('gameplay/src/client/java/top/csituka/magicaland/gameplay/client/RemoteBodyRenderer.java');
check(body.includes('AFTER_ENTITIES') && body.includes('getFocusedEntity() instanceof RemoteToolEntity') && body.includes('player.getUuid().equals(tool.owner())'), 'local body only redrawn for its own projection camera');
check(body.includes('renderer.render(player') && body.includes('renderer.shouldRender'), 'existing renderer and frustum preserved');
check(server.includes('item.cannotPickup()') && server.includes('item.getOwner()'), 'pickup delay and ownership respected');
check(server.includes('SERVER_STOPPING') && server.includes('DISCONNECT'), 'normal lifecycle cleanup');
check(server.includes('disableSaving().disableSummon()'), 'projection cannot persist or be summoned');
check(!/writeNbt|put\("Stack"/.test(entity), 'projection does not serialize another item inventory');
for (const guard of ['setCameraEntity(camera)','setCameraEntity(client.player)','previousPerspective',
    'client.currentScreen!=null','!client.isWindowFocused()','camera.getWorld()!=client.world',
    'camera.localSteering=true','held(options.attackKey)||attackQueued?64:0','held(options.useKey)||useQueued?128:0'])
    check(client.includes(guard),`client boundary: ${guard}`);
const orb=read('appearance/src/client/java/top/csituka/magicaland/client/render/MagicOrb.java');
check(orb.includes('ItemAuraGeometry.Mesh') && orb.includes('HornAuraPass.submit'), 'orb reuses existing aura shader and depth pipeline');
check(orb.includes('float[][][] faces=') && !orb.includes('latitude'), 'six-face cube instead of sphere');
check(client.includes('CARGO_SLOT.getStack()') && client.includes('drawItemInSlot') && client.includes('remote.cargo'), 'vanilla slot display with capacity');
check(server.includes('if (tool.carriesOriginal()) break') && server.includes('reject(player,"occupied")'), 'held tool occupies the only slot and cannot coexist with retained cargo');
check(server.includes('session.tool.carriesOriginal()?session.tool.stack()'), 'HUD reports the held tool in that same slot');
check(server.includes('RemoteActionContext.open(p,tool)'), 'remote effects scoped to validated interaction');
const feedback=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/mixin/RemoteWorldFeedbackMixin.java');
check(feedback.includes('WorldEventS2CPacket') && feedback.includes('PlaySoundS2CPacket') && feedback.includes('boolean missed='), 'missing owner feedback sent without duplicating normal broadcasts');
const attack=read('gameplay/src/main/java/top/csituka/magicaland/gameplay/mixin/RemoteAttackEffectsMixin.java');
check(attack.includes('tool.getEyePos().add') && attack.includes('ci.cancel()') && attack.includes('tool.squaredDistanceTo(target)'), 'sweep visuals and range originate at projection');
const hand=read('gameplay/src/client/java/top/csituka/magicaland/gameplay/mixin/client/RemoteHandMixin.java');
check(hand.includes('FirstPersonItemView.open') && hand.includes('magicaland$renderFirstPersonItem') && hand.includes('finally'), 'reuse original first-person renderer with scoped cleanup');
check(client.includes('ExternalGaze.setProvider(TOOLS::get)'), 'projection overrides other gaze targets');
const renderer=read('appearance/src/client/java/top/csituka/magicaland/client/render/PonyRenderer.java');
check(renderer.includes('forced!=null?forced:ClientGaze.targetFor(player)'), 'normal entity gaze resumes when override absent');
check(orb.includes('RADIUS=.075f') && orb.includes('SOFT_OPACITY,4.5f'), 'smaller core has independent wider soft layers');
check(!orb.includes('magicaland.gameplay'), 'appearance has no gameplay dependency');
const zh=JSON.parse(read('gameplay/src/main/resources/assets/magicaland_gameplay/lang/zh_cn.json'));
const en=JSON.parse(read('gameplay/src/main/resources/assets/magicaland_gameplay/lang/en_us.json'));
check(JSON.stringify(Object.keys(zh).sort())===JSON.stringify(Object.keys(en).sort()),'language keys match');
for (const key of Object.keys(zh)) check((zh[key].match(/%s/g)||[]).length===(en[key].match(/%s/g)||[]).length,`placeholder count: ${key}`);
console.log(`PASS RemoteStructureTest: ${checks} source-boundary checks (not a world interaction test)`);
