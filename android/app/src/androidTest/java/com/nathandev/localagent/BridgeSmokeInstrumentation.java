package com.nathandev.localagent;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real TLS interoperability test; isolated preferences and keys never touch user history. */
public final class BridgeSmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private int checks;
    private void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);checks++;}
    private void rejects(String text)throws Exception{
        boolean rejected=false;try{new BridgeProtocol.Invitation(text);}catch(Exception expected){rejected=true;}
        check(rejected,"Invalid invitation was accepted");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();DesktopBridge bridge=null;
        try{
            Context c=getTargetContext();File dir=new File(c.getFilesDir(),"bridge-qa");
            String decoded=SquareQrReader.decode(BitmapFactory.decodeFile(new File(dir,"invitation.png").getPath()));
            BridgeProtocol.Invitation invitation=new BridgeProtocol.Invitation(decoded);check(invitation!=null,"QR decoding");
            JSONObject bad=new JSONObject(decoded).put("expiresAt",BridgeProtocol.timestamp(System.currentTimeMillis()-1000));rejects(bad.toString());
            bad=new JSONObject(decoded).put("version",2);rejects(bad.toString());
            bad=new JSONObject(decoded).put("endpoints",new JSONArray().put("https://8.8.8.8:443"));rejects(bad.toString());
            boolean rejected=false;try{DesktopBridge.request(invitation.endpoints.getString(0),new String(new char[64]).replace('\0','0'),"/v1/status",null,null);}catch(Exception expected){rejected=true;}
            check(rejected,"Wrong certificate pin must fail");
            String namespace="-qa-"+UUID.randomUUID();SharedPreferences p=c.getSharedPreferences(namespace,Context.MODE_PRIVATE);
            ChatHistory history=new ChatHistory(p);history.current.messages.add(new String[]{"Toi","Hello from Android interoperability test","10:00",UUID.randomUUID().toString(),BridgeProtocol.timestamp(System.currentTimeMillis()),"[]","complete"});
            check(history.saveBlocking(),"Seed Android conversation");
            bridge=new DesktopBridge(c,p,namespace);bridge.pairSync(invitation);check(bridge.status().getBoolean("connected"),"Pair over pinned TLS");
            bridge.close();bridge=new DesktopBridge(c,p,namespace);bridge.refreshSync();check(bridge.status().getBoolean("connected"),"Encrypted trust survives reload");
            int original=new ChatHistory(p).chats.size();
            JSONObject pending=waitPending(bridge);bridge.respondSync(pending.getString("requestId"),false);
            check(new ChatHistory(p).chats.size()==original,"Declining preserves Android history");
            pending=waitPending(bridge);bridge.respondSync(pending.getString("requestId"),true);
            ChatHistory imported=new ChatHistory(p);boolean found=false;
            for(ChatHistory.Chat chat:imported.chats)for(String[] message:chat.messages)if(message[1].equals("Hello from the real Desktop sync engine"))found=true;
            check(found,"Import real Desktop message");
            BridgeSyncStore ledger=new BridgeSyncStore(c,p,bridge.deviceId(),namespace);
            JSONObject before=ledger.exportPayload();pending=waitPending(bridge);bridge.respondSync(pending.getString("requestId"),true);
            JSONObject after=ledger.exportPayload();
            // Reload the ledger after the second sync, because it belongs to another client instance.
            after=new BridgeSyncStore(c,p,bridge.deviceId(),namespace).exportPayload();
            check(BridgeProtocol.canonical(before).equals(BridgeProtocol.canonical(after)),"Second sync is idempotent");
            bridge.forget();check(!bridge.status().getBoolean("paired"),"Forget removes local trust");
            result.putString("result","PASS: "+checks+" checks; square QR, pin rejection, pairing, encrypted trust, consent, bidirectional sync, idempotence");finish(-1,result);
        }catch(Exception e){result.putString("error",e.toString());finish(1,result);}
        finally{if(bridge!=null)bridge.close();}
    }
    private JSONObject waitPending(DesktopBridge bridge)throws Exception{
        for(int i=0;i<30;i++){bridge.refreshSync();JSONObject pending=bridge.status().optJSONObject("pending");if(pending!=null)return pending;Thread.sleep(250);}
        throw new Exception("Desktop did not request consent");
    }
}
