import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const languages=['en_us','zh_cn'].map(language=>JSON.parse(fs.readFileSync(path.join(root,
    `src/main/resources/assets/magicaland_gameplay/lang/${language}.json`),'utf8')));
let checks=0;
function check(value,message){checks++;assert.ok(value,message);}
check(JSON.stringify(Object.keys(languages[0]).sort())===JSON.stringify(Object.keys(languages[1]).sort()),'both languages expose identical keys');
for(const key of Object.keys(languages[0]))check((languages[0][key].match(/%s/g)||[]).length===(languages[1][key].match(/%s/g)||[]).length,`matching placeholders: ${key}`);

// Conservative advances for the default Minecraft fonts; this is not a rendered game screenshot.
const width=text=>[...text].reduce((sum,char)=>sum+(char.codePointAt(0)>127?9:char===' '?4:6),0);
function lines(text,maxWidth){
    const tokens=text.match(/[\x21-\x7e]+| |[^\x00-\x7f]/gu)||[];
    let count=1,used=0;
    for(const token of tokens){
        const next=width(token);
        if(used&&used+next>maxWidth){count++;used=token===' '?0:next;}
        else used+=next;
    }
    return count;
}
for(const language of languages){
    const value=key=>language['text.magicaland_gameplay.race.'+key];
    for(const key of ['details.unicorn','details.pegasus','details.earth_pony'])
        check(lines(value(key),288)*9<=27,`selection detail stays above status at 320x240: ${key}`);
    for(const key of ['no_server','unsupported','loading','waiting','not_applied','timeout','disabled','unknown_hint',
        'mode.potion.hint','mode.free.hint','mode.locked.hint','rules.stale','rules.edit_hint','rules.read_only','rules_saved',
        'error.unknown','error.disabled','error.same','error.locked','error.potion_required','error.stance','error.stale','error.permission','error.invalid_rules'])
        check(lines(value(key),288)*9<=18,`status stays above buttons at 320x240: ${key}`);
    check(lines(value('rules.appearance.hint'),288)*9<=18,'appearance hint stays above status at 320x240');
    for(const key of ['card.current','card.available','card.planned','disabled'])
        check(width(value(key))<=90,`card badge fits minimum card: ${key}`);
}
console.log(`PASS RaceLanguageLayoutTest: ${checks} localization and conservative wrapping checks`);
