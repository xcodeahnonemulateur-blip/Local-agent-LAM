import { createRequire } from 'node:module';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createHash } from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const require=createRequire(path.resolve(root,'..','Desktop','package.json'));
const sharp=require('sharp');
const source=await readFile(path.join(root,'branding','lam-master.png'));
async function png(relative,size){
  const dest=path.join(root,relative);await mkdir(path.dirname(dest),{recursive:true});
  await sharp(source).resize(size,size,{fit:'contain',background:'#000000'}).flatten({background:'#000000'}).toColourspace('srgb').ensureAlpha().png().toFile(dest);
}
for(const [dpi,size] of [['mdpi',48],['hdpi',72],['xhdpi',96],['xxhdpi',144],['xxxhdpi',192]]){
  await png('app/src/main/res/mipmap-'+dpi+'/ic_launcher.png',size);
  await png('app/src/main/res/mipmap-'+dpi+'/ic_launcher_round.png',size);
}
await png('app/src/main/res/drawable-nodpi/ic_launcher_foreground.png',432);
await png('app/src/main/res/drawable-nodpi/lam_brand.png',256);
await png('store/icon-512.png',512);
await png('branding/lam-master-1024.png',1024);
await writeFile(path.join(root,'branding','lam-source.json'),JSON.stringify({
  product:'Local Agent LLM',letters:'LAM',generator:'built-in image_gen',
  source:'android/branding/lam-master.png',sha256:createHash('sha256').update(source).digest('hex'),
  prompt:'Premium geometric monogram: exactly one capital L, one A and one M. Pure white on pure black, flat, centered, no other text, no shadows or borders; generous Android adaptive-icon safe padding.',
  generatedFor:['Android launcher','Google Play icon'],date:'2026-11-13'
},null,2)+'\n');
console.log('Generated Android LAM launcher and 512px Google Play icon.');
