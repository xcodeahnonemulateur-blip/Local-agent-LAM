package com.nathandev.localagent;

import android.content.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, atomic sync ledger; imports preserve local conflicts and unsupported record kinds. */
final class BridgeSyncStore {
    private final Context context;
    private final SharedPreferences settings;
    private final String origin;
    private final File directory;
    private final AtomicFile file;
    private JSONObject state;
    BridgeSyncStore(Context c,SharedPreferences p,String deviceId,String namespace)throws Exception{
        context=c;settings=p;origin=deviceId;directory=new File(c.getNoBackupFilesDir(),"desktop-sync"+namespace);
        if(!directory.exists()&&!directory.mkdirs())throw BridgeProtocol.error("Stockage de synchronisation indisponible.");
        file=new AtomicFile(new File(directory,"ledger.json"));
        state=file.getBaseFile().exists()?new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8)):new JSONObject().put("records",new JSONArray()).put("conflicts",new JSONArray());
        BridgeProtocol.validate(new JSONObject().put("schemaVersion",1).put("records",state.getJSONArray("records")));
    }
    private Map<String,JSONObject> records()throws Exception{
        TreeMap<String,JSONObject> map=new TreeMap<>();JSONArray arr=state.getJSONArray("records");
        for(int i=0;i<arr.length();i++){JSONObject r=arr.getJSONObject(i);map.put(r.getString("id"),r);}return map;
    }
    private void persist(JSONObject next)throws Exception{
        byte[] bytes=next.toString().getBytes(StandardCharsets.UTF_8);FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);state=next;}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    private void backup()throws Exception{
        File backups=new File(directory,"backups");if(!backups.exists()&&!backups.mkdirs())throw BridgeProtocol.error("Sauvegarde locale indisponible.");
        JSONObject snapshot=new JSONObject().put("ledger",state).put("chats",settings.getString("sarah_chats_v3","[]")).put("current",settings.getString("sarah_current_chat",""));
        FileOutputStream out=new FileOutputStream(new File(backups,System.currentTimeMillis()+"-"+UUID.randomUUID()+".json"));
        try{out.write(snapshot.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}finally{out.close();}
    }
    static String wireAgent(String name){return "Raphaël".equals(name)?"raphael":name.toLowerCase(Locale.ROOT);}
    static String nativeAgent(String name){return "raphael".equals(name)?"Raphaël":name.substring(0,1).toUpperCase(Locale.ROOT)+name.substring(1);}
    private void upsert(Map<String,JSONObject> map,String id,String kind,JSONObject data,boolean deleted)throws Exception{
        JSONObject old=map.get(id);
        if(old!=null&&!kind.equals(old.getString("kind")))throw BridgeProtocol.error("Collision de type de donnée.");
        if(old!=null&&old.getBoolean("deleted")==deleted&&BridgeProtocol.canonical(old.getJSONObject("data")).equals(BridgeProtocol.canonical(data)))return;
        map.put(id,new JSONObject().put("id",id).put("kind",kind).put("version",old==null?1:old.getLong("version")+1)
            .put("updatedAt",BridgeProtocol.timestamp(System.currentTimeMillis())).put("origin",origin).put("deleted",deleted).put("data",data));
    }
    private Map<String,JSONObject> capture()throws Exception{
        ChatHistory book=new ChatHistory(settings);if(!book.saveBlocking())throw BridgeProtocol.error("Impossible d'enregistrer les discussions.");
        Map<String,JSONObject> map=records();HashSet<String> present=new HashSet<>();
        for(ChatHistory.Chat chat:book.chats){
            present.add(chat.id);JSONObject old=map.get(chat.id),oldData=old==null?null:old.getJSONObject("data");
            JSONObject data=new JSONObject().put("title",chat.title).put("agentId",wireAgent(chat.agent))
                .put("projectId",oldData==null?JSONObject.NULL:oldData.opt("projectId")).put("createdAt",BridgeProtocol.timestamp(chat.created));
            upsert(map,chat.id,"conversation",data,false);
            for(String[] m:chat.messages){
                present.add(m[3]);JSONObject d=new JSONObject().put("conversationId",chat.id).put("role","Toi".equals(m[0])?"user":"assistant")
                    .put("content",m[1]).put("createdAt",m[4]).put("attachments",new JSONArray(m[5])).put("status",m.length>6?m[6]:"complete");
                upsert(map,m[3],"message",d,false);
            }
        }
        for(JSONObject old:new ArrayList<>(map.values())){
            String kind=old.getString("kind");
            if(("conversation".equals(kind)||"message".equals(kind))&&!old.getBoolean("deleted")&&!present.contains(old.getString("id")))
                upsert(map,old.getString("id"),kind,old.getJSONObject("data"),true);
        }
        return map;
    }
    private JSONObject payload(Map<String,JSONObject> map)throws Exception{
        JSONArray all=new JSONArray();for(JSONObject r:map.values())all.put(r);
        JSONObject p=new JSONObject().put("schemaVersion",1).put("records",all);BridgeProtocol.validate(p);return p;
    }
    synchronized JSONObject exportPayload()throws Exception{
        Map<String,JSONObject> map=capture();JSONObject p=payload(map);
        persist(new JSONObject().put("records",p.getJSONArray("records")).put("conflicts",state.getJSONArray("conflicts")));return p;
    }
    synchronized JSONObject merge(JSONObject incoming)throws Exception{
        BridgeProtocol.validate(incoming);Map<String,JSONObject> map=capture();
        JSONArray received=incoming.getJSONArray("records"),conflicts=new JSONArray(state.getJSONArray("conflicts").toString());
        Map<String,JSONObject> combined=new HashMap<>(map);
        for(int i=0;i<received.length();i++)combined.put(received.getJSONObject(i).getString("id"),received.getJSONObject(i));
        for(int i=0;i<received.length();i++){
            JSONObject r=received.getJSONObject(i),old=map.get(r.getString("id"));
            if(old!=null&&!old.getString("kind").equals(r.getString("kind")))throw BridgeProtocol.error("Collision de type : transfert refusé.");
            if("message".equals(r.getString("kind"))){
                JSONObject parent=combined.get(r.getJSONObject("data").getString("conversationId"));
                if(parent==null||!"conversation".equals(parent.getString("kind")))throw BridgeProtocol.error("Message sans discussion : transfert refusé.");
            }
        }
        int added=0,updated=0,ignored=0;
        for(int i=0;i<received.length();i++){
            JSONObject r=received.getJSONObject(i),old=map.get(r.getString("id"));
            if(old==null){map.put(r.getString("id"),r);added++;continue;}
            int order=BridgeProtocol.compare(r,old);if(order==0){ignored++;continue;}
            conflicts.put(new JSONObject().put("recordedAt",BridgeProtocol.timestamp(System.currentTimeMillis())).put("local",old).put("incoming",r));
            if(order>0){map.put(r.getString("id"),r);updated++;}else ignored++;
        }
        JSONObject outgoing=payload(map);backup();
        // Keep the complete winning ledger first; a native import can always be recovered from it.
        persist(new JSONObject().put("records",outgoing.getJSONArray("records")).put("conflicts",conflicts));
        ChatHistory book=new ChatHistory(settings);Map<String,ChatHistory.Chat> chats=new LinkedHashMap<>();
        for(ChatHistory.Chat c:book.chats)chats.put(c.id,c);
        for(JSONObject r:map.values())if("conversation".equals(r.getString("kind"))){
            String id=r.getString("id");if(r.getBoolean("deleted")){chats.remove(id);continue;}
            ChatHistory.Chat c=chats.get(id);if(c==null){c=new ChatHistory.Chat();c.id=id;chats.put(id,c);}
            JSONObject d=r.getJSONObject("data");c.title=d.getString("title");c.agent=nativeAgent(d.getString("agentId"));
            c.created=BridgeProtocol.date(d.getString("createdAt"));c.updated=BridgeProtocol.date(r.getString("updatedAt"));c.messages.clear();
        }
        ArrayList<JSONObject> sorted=new ArrayList<>(map.values());
        Collections.sort(sorted,(a,b)->{try{int cmp=a.getJSONObject("data").optString("createdAt").compareTo(b.getJSONObject("data").optString("createdAt"));return cmp==0?a.optString("id").compareTo(b.optString("id")):cmp;}catch(Exception e){return 0;}});
        for(JSONObject r:sorted)if("message".equals(r.getString("kind"))&&!r.getBoolean("deleted")){
            JSONObject d=r.getJSONObject("data");ChatHistory.Chat c=chats.get(d.getString("conversationId"));if(c==null)continue;
            java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("HH:mm",Locale.getDefault());
            c.messages.add(new String[]{"user".equals(d.getString("role"))?"Toi":c.agent,d.getString("content"),clock.format(new Date(BridgeProtocol.date(d.getString("createdAt")))),
                r.getString("id"),d.getString("createdAt"),d.getJSONArray("attachments").toString(),d.optString("status","complete")});
        }
        String active=book.current.id;book.chats.clear();book.chats.addAll(chats.values());
        book.current=chats.containsKey(active)?chats.get(active):book.chats.isEmpty()?new ChatHistory.Chat():book.chats.get(0);
        if(!book.saveBlocking())throw BridgeProtocol.error("La copie reçue reste sauvegardée, mais son affichage local n'a pas pu être enregistré.");
        return new JSONObject().put("added",added).put("updated",updated).put("ignored",ignored).put("conflicts",conflicts.length());
    }
}
