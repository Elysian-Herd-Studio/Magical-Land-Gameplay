import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const output = path.resolve(process.argv[2] || path.join(os.tmpdir(), 'magicaland-levitation-hints'));
if (output === repo || output.startsWith(repo + path.sep)) throw new Error('Use isolated output outside the repository');
fs.mkdirSync(output, { recursive: true });
const run = fs.mkdtempSync(path.join(output, 'check-'));
const source = fs.readFileSync(path.join(repo, 'src/client/java/top/csituka/magicaland/gameplay/client/levitation/UnicornLevitationClient.java'), 'utf8');
const marker = 'public static Text unavailable()';
const start = source.indexOf(marker);
if (start < 0) throw new Error('Missing unavailable method');
let open = source.indexOf('{', start), depth = 1, end = open + 1;
for (; end < source.length && depth; end++) { if (source[end] === '{') depth++; if (source[end] === '}') depth--; }
const method = source.slice(start, end);
let checks = 0;
for (const language of ['zh_cn', 'en_us']) {
    const values = JSON.parse(fs.readFileSync(path.join(repo, 'src/main/resources/assets/magicaland_gameplay/lang', language + '.json'), 'utf8'));
    for (const key of ['hint', 'unavailable', 'server']) {
        if (!values['text.magicaland_gameplay.levitation.' + key]) throw new Error('Missing translation ' + language + ':' + key);
        checks++;
    }
    for (const obsolete of ['creative', 'flight_permission', 'mana', 'recovering']) {
        if (values['text.magicaland_gameplay.levitation.' + obsolete]) throw new Error('Obsolete refusal/HUD translation ' + obsolete);
        checks++;
    }
}
for (const guard of ['!player.isSpectator()', '(permitNativeFlightExit || !player.getAbilities().flying)', 'scope(client, true)']) {
    if (!source.includes(guard)) throw new Error('Missing native/creative scope guard: ' + guard);
    checks++;
}
if (/!player\.isCreative\(\)|!player\.getAbilities\(\)\.allowFlying/.test(source)) throw new Error('Creative or flight permission incorrectly denied');
checks++;
const harness = `public final class LevitationUnavailableHarness {
  record Text(String value) {}
  static boolean race, server;
  static class RaceClient { static boolean canUseUnicornAbility(){return race;} static Text abilityUnavailable(){return text("race");} }
  static class ClientPlayNetworking { static boolean canSend(Object channel){return server;} }
  static class UnicornLevitationProtocol { static final Object CONTROL=new Object(); }
  static class Abilities { boolean allowFlying,flying; }
  static class Player { boolean creative,spectator; final Abilities abilities=new Abilities(); boolean isCreative(){return creative;} boolean isSpectator(){return spectator;} Abilities getAbilities(){return abilities;} }
  static class MinecraftClient { static final MinecraftClient INSTANCE=new MinecraftClient(); Player player; static MinecraftClient getInstance(){return INSTANCE;} }
  static Text text(String suffix){return new Text(suffix);}
  ${method}
  public static void main(String[] args){
    int checks=0;
    for(int flags=0;flags<128;flags++){
      race=(flags&1)!=0; server=(flags&2)!=0;
      var p=new Player(); p.creative=(flags&4)!=0; p.spectator=(flags&8)!=0; p.abilities.allowFlying=(flags&16)!=0; p.abilities.flying=(flags&32)!=0;
      MinecraftClient.INSTANCE.player=(flags&64)!=0?p:null;
      String expected=!race?"race":!server?"server":"unavailable";
      if(!unavailable().value().equals(expected))throw new AssertionError("flags="+flags+" expected="+expected+" actual="+unavailable()); checks++;
    }
    System.out.println("PASS actual unavailable() body: "+checks+" null/race/server/creative/spectator/permission combinations");
  }
}`;
const java = path.join(run, 'LevitationUnavailableHarness.java');
fs.writeFileSync(java, harness);
const jdk = process.env.LEVITATION_TEST_JDK || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin') : '');
for (const [tool, args] of [['javac', ['--release', '17', '-encoding', 'UTF-8', '-d', run, java]], ['java', ['-ea', '-cp', run, 'LevitationUnavailableHarness']]]) {
    const executable = tool + (process.platform === 'win32' ? '.exe' : '');
    const result = spawnSync(jdk ? path.join(jdk, executable) : executable, args, { encoding: 'utf8', windowsHide: true, cwd: run });
    if (result.error) throw result.error;
    process.stdout.write(result.stdout || ''); process.stderr.write(result.stderr || '');
    if (result.status !== 0) throw new Error(tool + ' failed');
}
console.log('PASS language keys: ' + checks + '; Java body extracted verbatim with minimal API fixtures, not a Minecraft bootstrap');
