import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const exists = file => fs.existsSync(path.join(root, file));
const json = file => JSON.parse(read(file));
const walk = dir => fs.readdirSync(path.join(root, dir), {withFileTypes: true})
    .flatMap(entry => entry.isDirectory() ? walk(`${dir}/${entry.name}`) : [`${dir}/${entry.name}`]);
const properties = Object.fromEntries(read('gradle.properties').split(/\r?\n/)
    .filter(line => line && !line.startsWith('#')).map(line => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator), line.slice(separator + 1)];
    }));
let checks = 0;
function check(condition, message) { checks++; assert.ok(condition, message); }

check(exists('src/main/java') && exists('src/client/java'), 'root contains common and client sources');
check(!exists('appearance') && !exists('gameplay'), 'no nested legacy modules');
check(!/^\s*include(?:Build)?\b/m.test(read('settings.gradle')), 'single independent Gradle project');
const build = read('build.gradle');
check(!/evaluationDependsOn|project\s*\(/.test(build), 'build has no sibling project dependencies');
check(!/^\s*include\s*(?:\(|["'])/m.test(build), 'appearance and API are not embedded');
check(build.includes("version '1.16.3'"), 'Loom version is pinned');
check(/^\d+\.\d+\.\d+(?:[-+].+)?$/.test(properties.mod_version), 'gameplay has an independent semantic release version');
check(/^\d+\.\d+\.\d+(?:[-+].+)?$/.test(properties.appearance_version)
    && read('build.gradle').includes('project.appearance_version'), 'dependency version has its own property, even if versions happen to match');
check(properties.archives_base_name === 'magicaland-gameplay', 'gameplay artifact coordinate');
check(build.includes('modCompileOnly("${appearanceArtifact}:api") { transitive = false }'), 'compile surface is only the external API artifact');
check(build.includes('modRuntimeOnly(appearanceArtifact) { transitive = false }'), 'main appearance mod is the runtime implementation');
check(!/mod(?:Implementation|Api)\([^\n]*appearanceArtifact/.test(build), 'appearance internals are not on the compile classpath');
check(!/mod(?:RuntimeOnly|LocalRuntime)\([^\n]*:api/.test(build), 'API classifier is not a second runtime copy');
check(build.includes("gradleProperty('appearanceMavenRepo')"), 'external repository location is configurable');

const metadata = json('src/main/resources/fabric.mod.json');
check(metadata.id === 'magicaland_gameplay', 'gameplay has its own mod ID');
check(metadata.environment === '*', 'addon has both client and server behavior');
check(metadata.depends.magicaland === '${appearance_compatibility}', 'Fabric dependency uses a separate compatibility range');
check(properties.appearance_compatibility === '>=0.3.2 <0.4.0', 'API v1.2 appearance compatibility is bounded');
check(metadata.contact.sources.endsWith('/Magical-Land-Gameplay'), 'metadata points to this repository');

const classes = new Set();
for (const file of walk('src').filter(file => file.endsWith('.java'))) {
    const source = read(file);
    const pkg = source.match(/^package ([\w.]+);/m)?.[1];
    check(pkg?.startsWith('top.csituka.magicaland.gameplay'), `only addon classes: ${file}`);
    check(pkg && file.endsWith(pkg.replaceAll('.', '/') + '/' + path.basename(file)), `package path: ${file}`);
    const name = pkg + '.' + path.basename(file, '.java');
    check(!classes.has(name), `no duplicate class: ${name}`);
    classes.add(name);
    check(!/top\.csituka\.magicaland\.(?!(?:gameplay|api)\b)/.test(source), `appearance access only through public API: ${file}`);
    if (file.startsWith('src/main/')) {
        check(!/^import (?:net\.minecraft\.client\.|top\.csituka\.magicaland\.(?:api\.client|gameplay\.client)\.)/m.test(source), `common code avoids client APIs: ${file}`);
    }
}
for (const entries of Object.values(metadata.entrypoints)) {
    for (const entry of entries) check(classes.has(typeof entry === 'string' ? entry : entry.value), `entrypoint exists: ${entry}`);
}
for (const entry of metadata.mixins) {
    const name = typeof entry === 'string' ? entry : entry.config;
    const candidates = ['main', 'client'].map(side => `src/${side}/resources/${name}`).filter(exists);
    check(candidates.length === 1, `unique mixin config: ${name}`);
    const config = json(candidates[0]);
    for (const name of [...(config.mixins ?? []), ...(config.client ?? []), ...(config.server ?? [])]) {
        check(classes.has(config.package + '.' + name), `mixin class exists: ${name}`);
    }
}
const advancement = json('src/main/resources/data/magicaland/advancements/not_what_i_meant.json');
check(advancement.criteria.misunderstanding.trigger === 'minecraft:impossible', 'legacy advancement criterion preserved');
check(read('src/main/java/top/csituka/magicaland/gameplay/MagicalLandGameplay.java').includes('CarrotMisunderstanding.register();'), 'encounter still registered');
for (const file of walk('src').filter(file => file.endsWith('.json'))) { json(file); checks++; }
for (const file of ['gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar']) check(exists(file), `wrapper present: ${file}`);
check(read('gradle/wrapper/gradle-wrapper.properties').includes('distributionSha256Sum='), 'wrapper distribution has checksum');
console.log(`PASS Gameplay RepositoryArchitectureTest: ${checks} checks (source/configuration only)`);
