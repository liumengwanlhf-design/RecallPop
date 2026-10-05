import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

const contractPath=process.argv[2];
if(!contractPath)throw new Error('Pass the official AnkiDroid v2.24.0 FlashCardsContract.kt path');
const official=fs.readFileSync(contractPath,'utf8');
const api=fs.readFileSync(path.join(import.meta.dirname,'../app/src/main/java/dev/ankigate/AnkiApi.java'),'utf8');
const fields=new Map([...official.matchAll(/const val (\w+): String = "([^"]+)"/g)].map(m=>[m[1],m[2]]));
for(const match of api.matchAll(/v\.put\("([^"]+)"/g))assert([...fields.values()].includes(match[1]),`Unknown submitted field ${match[1]}`);
const projected=['NOTE_ID','CARD_ORD','BUTTON_COUNT','MEDIA_FILES','QUESTION','ANSWER','REPS','MID','CSS','DECK_ID','DECK_NAME'];
for(const field of projected)assert(api.includes(`"${fields.get(field)}"`),`Missing official ${field} value`);
assert(api.includes(`v.put("${fields.get('EASE')}",ease)`),'Ease must use the official write-only field');
assert(!api.includes('"card_ord"')&&!api.includes('"ease"'),'Unsupported legacy guesses');
assert(official.includes('Uri.withAppendedPath(AUTHORITY_URI, "selected_deck")')&&api.includes('Uri.withAppendedPath(BASE,"selected_deck")'),'Selected deck URI follows the official contract');
console.log('Official v2.24.0 projection and submission fields verified');
