import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const client='src/client/java/top/csituka/magicaland/gameplay/client/';
const mixin='src/client/java/top/csituka/magicaland/gameplay/mixin/client/';
const sense=read(client+'sense/EarthSenseClient.java');
const remote=read(client+'RemoteToolClient.java');
const focus=read(client+'sense/EarthSenseFocusInput.java');
const inputMixin=read(mixin+'EarthSenseInputMixin.java');
const playerMixin=read(mixin+'EarthSensePlayerInputMixin.java');
const mixins=JSON.parse(read('src/client/resources/magicaland.gameplay.client.mixins.json'));
let checks=0;
function check(value,message){checks++;assert.ok(value,message);}
function method(source,signature){
    const found=source.indexOf(signature);check(found>=0,`method exists: ${signature}`);
    const start=source.indexOf('{',found);let level=1,end=start+1;
    while(end<source.length&&level){if(source[end]==='{')level++;if(source[end]==='}')level--;end++;}
    check(level===0,`method braces balance: ${signature}`);return source.slice(start+1,end-1);
}
const wheelStart=remote.indexOf('while (wheel.wasPressed())');
const wheelEnd=remote.indexOf('while (activate.wasPressed())',wheelStart);
check(wheelStart>=0&&wheelEnd>wheelStart,'wheel-open input block is independently located');
const wheelOpen=remote.slice(wheelStart,wheelEnd);
check(wheelOpen.indexOf('EarthSenseClient.stop()')>=0&&wheelOpen.indexOf('EarthSenseClient.stop()')<wheelOpen.indexOf('new AbilityWheelScreen()'),'opening ability wheel ends focus before installing input-blocking screen');
const apply=method(sense,'public static void applyFocusInput(');
const menuGuard=apply.indexOf('currentScreen != null');
check(menuGuard>=0&&menuGuard<apply.indexOf('EarthSenseFocusInput.apply('),'any open screen is checked before forced crouch');
check(/currentScreen\s*!=\s*null\)\s*\{\s*stop\(\);\s*return;\s*\}/.test(apply),'menu guard stops focus and exits without competing with remote input suppression');
const tick=method(sense,'private static void tick(');
check(tick.includes('client.currentScreen != null')&&tick.indexOf('client.currentScreen != null')<tick.indexOf(') stop();'),'end-of-tick cleanup also ends focus for menus that skip normal input');
check(tick.includes('client.isPaused()')&&tick.includes('!client.isWindowFocused()')&&tick.includes('client.player.hurtTime > 0'),'pause, focus loss and damage remain explicit cancellation reasons');
const mutation=method(focus,'public static boolean apply(');
check(mutation.indexOf('if (requestedJump(input)) return true;')<mutation.indexOf('input.sneaking = true;'),'jump exits focus before synthetic crouch mutates current input');
check(!sense.includes('requestedMovement')&&!focus.includes('pressingForward ='),'horizontal input never ends focus or loses direction keys');
check(inputMixin.includes('applyFocusInput(this, slowDown, factor)')&&mutation.includes('slowDown ? 1 : crouch'),'tail passes vanilla slowdown context to prevent a second crouch multiplier');
check(sense.includes('signalQuality(float delta)')&&sense.includes('new EarthSenseViewerMotion(')&&sense.includes('getVelocity().horizontalLength()'),'actual movement quality has a separate smoothed signal channel');
check(!method(sense,'public static float opacity(').includes('quality')&&!/EarthSenseAudio\.tick\([^;]*quality/.test(sense),'movement clarity never weakens base filter, FOV, or audio');
check(sense.includes('ENVELOPE.observe(++visualTicks, target)')&&sense.includes('ENVELOPE.reset()'),'independent focus envelope follows target and resets with lifecycle');
check(!focus.includes('setPressed('),'focus helper does not modify physical or toggle key bindings');
check(inputMixin.replace(/\s+/g,'').includes('@Inject(method="tick",at=@At("TAIL"))'),'focus injection occurs after vanilla keyboard fields have been refreshed');
check(/else if \(focusing\(\) && input != null\) focusedInput = input;/.test(apply),'focus owns only input that was actually forced into crouch');
const restore=method(sense,'private static void restoreFocusInput(');
check(restore.indexOf('Input previous = focusedInput;')>=0&&restore.indexOf('focusedInput = null;')<restore.indexOf('if (previous == null'),'restoration releases ownership even when the player input has been replaced');
check(restore.includes('previous == null || client.player == null || client.player.input != previous'),'restoration cannot overwrite a new player or a foreign input instance');
check(restore.includes('boolean held = client.options.sneakKey.isPressed();')&&restore.includes('previous.sneaking = held;'),'restoration uses the actual manual sneak binding instead of unconditionally releasing it');
check(restore.indexOf('previous.sneaking = held;')<restore.indexOf('state.magicaland$restoreSneak(held);'),'local input is restored before the immediate packet-state hook');
check(restore.includes('client.getNetworkHandler() == client.player.networkHandler'),'restoration does not send a command through a stale connection');
const stop=method(sense,'public static void stop(');
check(stop.indexOf('restoreFocusInput();')>=0&&stop.indexOf('restoreFocusInput();')<stop.indexOf('if (!SESSION.wanted()'),'manual and duplicate stops restore owned input before session early returns');
const reset=method(sense,'private static void reset(');
check(reset.indexOf('restoreFocusInput();')>=0&&reset.indexOf('restoreFocusInput();')<reset.indexOf('SESSION.clear();'),'disconnect and world reset restore owned input before clearing session');
check(sense.indexOf('if (!state.active()) restoreFocusInput();')>sense.indexOf('if (!SESSION.accept(state,'),'accepted server termination restores input, without letting rejected packets release focus');
check(playerMixin.includes('@Mixin(ClientPlayerEntity.class)')&&playerMixin.includes('implements EarthSenseClient.SneakState'),'immediate sneak-state hook targets the vanilla local player');
check(mixins.client.includes('EarthSensePlayerInputMixin'),'immediate sneak-state mixin is registered');
const restorePacket=method(playerMixin,'public void magicaland$restoreSneak(');
check(restorePacket.indexOf('if (lastSneaking == held) return;')>=0&&restorePacket.indexOf('if (lastSneaking == held) return;')<restorePacket.indexOf('networkHandler.sendPacket('),'already-synchronized sneak states do not send duplicate commands');
check(restorePacket.includes('held ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY'),'hook preserves manual crouch as well as releasing synthetic crouch');
check(restorePacket.indexOf('networkHandler.sendPacket(')>=0&&restorePacket.indexOf('lastSneaking = held;')>restorePacket.indexOf('networkHandler.sendPacket('),'hook updates vanilla last-sent state after sending so the next ordinary tick remains consistent');
check(!/setPose\(|setSneaking\(|setPressed\(|unpressAll\(/.test(restore+playerMixin),'cleanup leaves collision pose and physical key bindings to vanilla');

const dependencyFile=process.argv[2];
const javap=process.argv[3];
check(Boolean(dependencyFile&&javap),'real Minecraft dependency classpath and javap executable are required');
const jars=fs.readFileSync(dependencyFile,'utf8').trim().split(path.delimiter)
    .filter(file=>path.basename(file).includes('minecraft'));
check(jars.length>=1&&jars.every(file=>fs.statSync(file).isFile()),'existing Minecraft bytecode is available for read-only order checks');
function bytecode(name){return execFileSync(javap,['-classpath',jars.join(path.delimiter),'-c','-p',name],{encoding:'utf8',windowsHide:true,maxBuffer:4*1024*1024});}
function compiledMethod(source,signature){
    const start=source.indexOf(signature);check(start>=0,`real Minecraft method exists: ${signature}`);
    const body=source.slice(start+signature.length);const next=body.search(/\r?\n  (?:public|private|protected) /);
    return next<0?body:body.slice(0,next);
}
const keyboard=compiledMethod(bytecode('net.minecraft.client.input.KeyboardInput'),'  public void tick(boolean, float);');
const sneakRead=keyboard.indexOf('GameOptions.sneakKey:');
const sneakWrite=keyboard.indexOf('Field sneaking:Z');
check(sneakRead>=0&&sneakWrite>sneakRead&&keyboard.indexOf('return',sneakWrite)>sneakWrite,'vanilla reads sneak key and rewrites input.sneaking before tail injections');
check(keyboard.slice(sneakWrite).includes('fmul')&&keyboard.slice(sneakWrite).includes('fload_2'),'real KeyboardInput multiplies axes by factor before TAIL, only when slowDown');
for(const key of ['forwardKey','backKey','leftKey','rightKey','jumpKey'])check(keyboard.includes('GameOptions.'+key+':'),'vanilla refreshes physical '+key+' before focus hook');
const player=bytecode('net.minecraft.client.network.ClientPlayerEntity');
check(player.includes('private boolean lastSneaking;'),'last-sent sneak shadow matches the real Minecraft private boolean field');
check(player.includes('public final net.minecraft.client.network.ClientPlayNetworkHandler networkHandler;'),'network handler shadow matches the real Minecraft final public field');
const playerTick=compiledMethod(player,'  public void tick();');
check(playerTick.indexOf('AbstractClientPlayerEntity.tick:()V')>=0&&playerTick.indexOf('AbstractClientPlayerEntity.tick:()V')<playerTick.indexOf('sendMovementPackets:()V'),'local player completes inherited movement tick before sending movement/sneak packets');
const movement=compiledMethod(player,'  public void tickMovement();');
check(movement.includes('net/minecraft/client/input/Input.tick:(ZF)V'),'movement tick executes keyboard input update and its tail mixins');
const sneaking=compiledMethod(player,'  public boolean isSneaking();');
check(sneaking.includes('net/minecraft/client/input/Input.sneaking:Z'),'packet sneak state comes from the post-input sneaking field');
const send=compiledMethod(player,'  private void sendMovementPackets();');
const readSneaking=send.indexOf('Method isSneaking:()Z'),press=send.indexOf('PRESS_SHIFT_KEY:'),release=send.indexOf('RELEASE_SHIFT_KEY:');
check(readSneaking>=0&&press>readSneaking&&release>readSneaking,'vanilla chooses press/release sneak packet after reading current input');
check(send.indexOf('ClientPlayNetworkHandler.sendPacket:')>release&&/putfield\s+[^\r\n]*Field lastSneaking:Z/.test(send),'vanilla sends state command and tracks last sent sneaking value');
const gameTick=compiledMethod(bytecode('net.minecraft.client.MinecraftClient'),'  public void tick();');
const entities=gameTick.indexOf('ClientWorld.tickEntities:()V');
check(entities>=0&&/Field paused:Z[\s\S]*?ifne/.test(gameTick.slice(Math.max(0,entities-600),entities)),'vanilla skips player/entity ticks while paused, requiring explicit owned-input cleanup');
console.log(`PASS EarthSenseFocusStructureTest: ${checks} screen guards and real Minecraft input/sneak-packet order checks (read only)`);
