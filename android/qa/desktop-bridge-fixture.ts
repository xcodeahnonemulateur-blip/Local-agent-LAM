import { NetworkService } from '../../Desktop/src/main/network';
import { Store } from '../../Desktop/src/main/store';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import QRCode from 'qrcode';

async function main() {
  const root=resolve(process.argv[2]);mkdirSync(root,{recursive:true});
  const store=await Store.open(join(root,'fixture.db'),resolve(process.argv[3]));
  const conversation=store.createConversation('sarah');
  store.addMessage(conversation.id,'assistant','Hello from the real Desktop sync engine');
  let devices:any[]=[];let requests=0;let merges=0;
  const service=new NetworkService({desktopId:store.desktopId,name:'Android interoperability fixture',dataDir:root,
    loadDevices:()=>devices,saveDevices:value=>{devices=value},exportPayload:()=>store.exportPayload(),
    mergePayload:value=>{const report=store.mergePayload(value);merges++;
      writeFileSync(join(root,'result.json'),JSON.stringify({merges,androidMessageReceived:store.records('message').some(r=>r.data.content==='Hello from Android interoperability test'),report}));return report;},
    onChange:()=>{},sealSecret:value=>value,unsealSecret:value=>value,enableDiscovery:false,invitationTtlMs:300000});
  await service.start();const view=await service.regeneratePairing();
  const port=new URL(view.invitation.endpoints[0]).port;
  const invitation={...view.invitation,endpoints:[`https://10.0.2.2:${port}`]};
  await QRCode.toFile(join(root,'invitation.png'),JSON.stringify(invitation),{errorCorrectionLevel:'H',margin:4,scale:6});
  const ticker=setInterval(()=>{const state=service.getState();const device=state.devices.find(d=>d.online);
    if(device&&!state.requestId&&requests<3){service.requestSync(device.id);requests++;}},150);
  console.log('READY: real Desktop HTTPS fixture, QR lifetime 5 minutes');
  const stop=async()=>{clearInterval(ticker);await service.stop();process.exit(0)};
  process.on('SIGINT',stop);process.on('SIGTERM',stop);setTimeout(stop,280000);
}
main().catch(e=>{console.error(e.message);process.exit(1)});
