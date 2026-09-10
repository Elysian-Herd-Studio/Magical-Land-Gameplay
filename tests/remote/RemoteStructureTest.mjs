import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const main='src/main/java/top/csituka/magicaland/gameplay/';
const clientRoot='src/client/java/top/csituka/magicaland/gameplay/';
const server=read(main+'remote/RemoteToolServer.java');
const entity=read(main+'remote/RemoteToolEntity.java');
const cargo=read(main+'remote/RemoteCargoInventory.java');
const state=read(main+'remote/RemoteCargoState.java');
const protocol=read(main+'remote/RemoteProtocol.java');
const rules=read(main+'remote/RemoteSessionRules.java');
const scope=read(main+'remote/RemoteActionContext.java');
const client=read(clientRoot+'client/RemoteToolClient.java');
const hud=read(clientRoot+'client/RemoteToolHud.java');
const renderer=read(clientRoot+'client/RemoteToolRenderer.java');
const hand=read(clientRoot+'mixin/client/RemoteHandMixin.java');
let checks=0;
function check(value,message) { checks++; assert.ok(value,message); }
for (const guard of ['remote_control_v2','remote_state_v2','RemoteProtocol.readControl','server.execute',
    's.token!=input.session()','s.tool.getId()!=input.entity()','s.rules.acceptInput',
    'getCommandTags().contains(GRANT)','ACTIVE.size()>=64','isChunkLoaded','getWorldBorder().contains',
    'MovementType.SELF','now-s.inputTick>40','p.getInventory().selectedSlot!=s.sourceSlot',
    'AttackBlockCallback.EVENT','AttackEntityCallback.EVENT','UseBlockCallback.EVENT',
    'interactionManager.tryBreakBlock','getAttackCooldownProgress(0)>=1','world.canPlayerModifyAt',
    'p.isBlockBreakingRestricted','player.shouldDamagePlayer'])
    check(server.includes(guard),`server boundary: ${guard}`);
check(protocol.includes('Float.isFinite') && protocol.includes('buffer.isReadable()') && protocol.includes('MAX_SLOTS'),'strict bounded protocol');
check(!/\b(?:p|player)\.(?:setPosition|teleport|refreshPositionAndAngles|setStackInHand)\s*\(/.test(server+scope),'no body relocation or temporary real hand replacement');
check(!/new ServerPlayerEntity|FakePlayer/.test(server+scope),'real owner identity preserved');
check(!server.includes('carriesOriginal') && !entity.includes('ORIGINAL'),'no borrowed-hand inventory split');
check(server.includes('cargo.loadFrom(player.getInventory(),s.sourceSlot)') && cargo.includes('source.removeStack(sourceSlot,accepted)'),'real item transfer');
const pickup=read(main+'remote/RemotePickup.java');
const pickupOwner=read(main+'mixin/RemoteItemPickupOwnerAccessor.java');
check(server.includes('RemotePickup.collect(s.cargo,item,p.getUuid())') && pickup.includes('cargo.addStack(original)') && pickup.includes('item.setStack(remainder)'),'only accepted pickup leaves world stack');
check(server.includes('RemotePickup.canPickup(item,p.getUuid())') && server.includes('RemotePickup.clearPath(world,tool,from,item)') && pickup.includes('item.cannotPickup()') && pickup.includes('owner.equals(player)') && pickup.includes('item.getBoundingBox().getCenter()'),'pickup delay ownership and target occlusion');
check(pickupOwner.includes('@Accessor("owner")') && !server.includes('item.getOwner()') && !pickup.includes('item.getOwner()'),'pickup recipient is distinct from thrower entity');
check(JSON.parse(read('src/main/resources/magicaland.gameplay.mixins.json')).mixins.includes('RemoteItemPickupOwnerAccessor'),'pickup owner accessor is registered');
check(/if \(coverage<1\) \{\s*drop\(s\);\s*interact\(s,now\);/.test(server) && server.includes('RemoteVisibility.occlusion'),'full optical blockage disables actions, drops and pickup');
check(protocol.includes('DROP_BYTES=19') && protocol.includes('INPUT_BYTES=27') && protocol.includes('c.flags<=1'),'drop packet leaves movement layout unchanged and bounds flags');
check(server.includes('input.operation()==3?DROPS:PACKETS') && server.includes('s.pendingDrop=input'),'drop rate limiting is separate from movement and stop');
check(server.includes('s.cargo.dropFrom(request.selected(),request.flags()==1') && server.includes('s.pendingDrop=null'),'queued drop retains its requested slot and clears each tick');
check(server.includes('world.isSpaceEmpty(item)') && server.includes('item.setPickupDelay(40)') && server.includes('return world.spawnEntity(item)'),'drop uses safe world spawn and pickup delay');
check(cargo.indexOf('if (!spawn.test(dropped)) return false;')<cargo.indexOf('removeStack(slot,count)'),'failed spawn never removes cargo');
check(rules.includes('RECALL_DEPTH') && rules.includes('forwardX') && rules.includes('active>') && rules.includes('actual>'),'recall requires active movement along entry axis');
check(!/blocked\s*(?:\+\+|>=)|\+\+s\.blocked/.test(server),'no fixed blindness timeout');
check(server.includes('s.rules.close()') && server.includes('ACTIVE.remove') && server.includes('s.cargo.returnTo'),'idempotent session settlement');
check(server.includes('GameRules.KEEP_INVENTORY') && server.includes('cargo.takeAll()') && server.includes('hasVanishingCurse'),'death inventory policy');
check(server.includes('SERVER_STOPPING') && server.includes('DISCONNECT'),'lifecycle cleanup');
check(server.includes('disableSaving().disableSummon()') && !entity.includes('put("Stack"'),'projection does not own a second persisted inventory');
check(cargo.includes('CAPACITY=8, MAX_SLOTS=9') && cargo.includes('unlocked=1') && cargo.includes('selectedSlot()'),'one unlocked slot with future bounded hotbar');
check(cargo.includes('slot.insertStack(remainder)') && cargo.includes('stack.getMaxCount()'),'vanilla stack compatibility and per-item limits');
check(cargo.includes('entry.contains("Slot"') && cargo.includes('recovery.add') && cargo.includes('readSaved'),'sparse v2 and legacy recovery path');
check(state.includes('magicaland_remote_cargo') && state.includes('putInt("Version",2)') && state.includes('readSaved(entry)'),'in-place saved inventory migration');
check(scope.includes('previous=CURRENT.get()') && scope.includes('CURRENT.remove()') && scope.includes('action.player==player'),'owner-scoped nested action context');
const attrs=read(main+'remote/RemoteAttributes.java');
check(attrs.includes('projected.setFrom(original)') && attrs.includes('projected.removeModifier') && attrs.includes('carried.getAttributeModifiers'),'attributes copied and projected without equipment mutation');
const inventoryContext=read(main+'mixin/RemoteInventoryContextMixin.java');
check(inventoryContext.includes('getMainHandStack') && inventoryContext.includes('getBlockBreakingSpeed') && inventoryContext.includes('forPlayer(player)'),'vanilla mining and harvest read remote tool');
const playerContext=read(main+'mixin/RemotePlayerContextMixin.java');
check(playerContext.includes('getEquippedStack') && playerContext.includes('equipStack') && playerContext.includes('resetLastAttackedTicks'),'equipment and cooldown routed in action scope');
const damage=read(main+'mixin/RemoteDamageSourceMixin.java');
check(damage.includes('DamageTypes.PLAYER_ATTACK') && damage.includes('source.getAttacker()==action.player') && damage.includes('action.tool.getPos()'),'shield damage origin preserves actual owner');
const attack=read(main+'mixin/RemoteAttackEffectsMixin.java');
check(attack.includes('tool.getEyePos().add') && attack.includes('RemoteToolServer.canAttack') && attack.includes('recordHit'),'remote feedback and sweep permissions');
const feedback=read(main+'mixin/RemoteWorldFeedbackMixin.java');
check(feedback.includes('WorldEventS2CPacket') && feedback.includes('PlaySoundS2CPacket') && feedback.includes('boolean missed='),'missing feedback is supplemented without duplicate broadcasts');
for (const guard of ['actionSequence()','actionStartedTick()','occlusion()','selectedSlot()','capacity()','attackCooldown()'])
    check(entity.includes(guard),`authoritative entity presentation: ${guard}`);
check(client.includes('ApiVersion.requireCompatible(1,2)'),'client API 1.2 contract');
check(client.includes('setCameraEntity') && client.includes('previousPerspective'),'camera ownership and perspective recovery');
check(client.includes('!client.isWindowFocused()') && client.includes('currentScreen'),'focus and screen boundaries');
check(client.includes('AppearanceOverrides.registerGaze') && client.includes('gazeOverride.close()'),'owned removable gaze override');
check(!client.includes('registerMainHandVisibility'),'real transferred tool needs no body-item hiding');
check(hud.includes('textures/gui/widgets.png') && hud.includes('hotbarWidth') && hud.includes('drawItemInSlot'),'vanilla hotbar style and stack metadata');
check(renderer.includes('renderLevitatingItem') && renderer.includes('renderOrb'),'public appearance API drives orb and remote item');
check(hand.includes('AppearanceVisuals.renderFirstPerson') && hand.includes('magicaland$renderFirstPersonItem'),'scoped first-person item render');
check(!/top\.csituka\.magicaland\.client\./.test(client+renderer+hand),'addon avoids appearance internals');
const zh=JSON.parse(read('src/main/resources/assets/magicaland_gameplay/lang/zh_cn.json'));
const en=JSON.parse(read('src/main/resources/assets/magicaland_gameplay/lang/en_us.json'));
check(JSON.stringify(Object.keys(zh).sort())===JSON.stringify(Object.keys(en).sort()),'language keys match');
for (const key of Object.keys(zh)) check((zh[key].match(/%s/g)||[]).length===(en[key].match(/%s/g)||[]).length,`placeholder count: ${key}`);
console.log(`PASS RemoteStructureTest: ${checks} source-boundary checks (not a world interaction test)`);
