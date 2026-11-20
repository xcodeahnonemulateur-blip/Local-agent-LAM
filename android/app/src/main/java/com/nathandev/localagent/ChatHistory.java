package com.nathandev.localagent;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Local conversation store. Existing conversation_v2 is migrated once and preserved. */
final class ChatHistory {
    static final class Chat {
        String id=UUID.randomUUID().toString(), title="Nouvelle discussion", agent="Sarah";
        long updated=System.currentTimeMillis(), created=updated;
        boolean pinned, archived;
        final ArrayList<String[]> messages=new ArrayList<>();
    }
    private final SharedPreferences prefs;
    final ArrayList<Chat> chats=new ArrayList<>();
    Chat current;
    ChatHistory(SharedPreferences p) {
        prefs=p;
        try {
            JSONArray items=new JSONArray(p.getString("sarah_chats_v3","[]"));
            for(int i=0;i<items.length();i++) {
                JSONObject j=items.getJSONObject(i);Chat c=new Chat();
                c.id=j.getString("id");c.title=j.optString("title","Discussion");c.agent=j.optString("agent","Sarah");
                c.updated=j.optLong("updated");c.created=j.optLong("created",c.updated);c.pinned=j.optBoolean("pinned");c.archived=j.optBoolean("archived");
                readMessages(j.optJSONArray("messages"),c.messages);chats.add(c);
                if(c.id.equals(p.getString("sarah_current_chat","")))current=c;
            }
            if(!p.contains("sarah_chats_v3")) {
                JSONArray legacy=new JSONArray(p.getString("conversation_v2","[]"));
                if(legacy.length()>0){Chat c=new Chat();readMessages(legacy,c.messages);c.title="Discussion précédente";chats.add(c);current=c;}
            }
        } catch(JSONException ignored) { /* Leave the original preferences untouched on invalid data. */ }
        if(current==null)current=new Chat();
    }
    private void readMessages(JSONArray a,ArrayList<String[]> out)throws JSONException {
        if(a==null)return;
        for(int i=0;i<a.length();i++){
            JSONObject m=a.getJSONObject(i);
            out.add(new String[]{m.optString("role","Sarah"),m.optString("text",""),m.optString("time",""),m.optString("id",UUID.randomUUID().toString()),m.optString("createdAt",BridgeProtocol.timestamp(System.currentTimeMillis()+i)),m.optJSONArray("attachments")==null?"[]":m.optJSONArray("attachments").toString(),m.optString("status","complete")});
        }
    }
    void newChat(){current=new Chat();save();}
    void save(){persist(false);}
    boolean saveBlocking(){return persist(true);}
    private boolean persist(boolean synchronous){
        if(!current.messages.isEmpty()&&!chats.contains(current))chats.add(current);
        if(current.title.equals("Nouvelle discussion")) {
            for(String[] m:current.messages) if(m[0].equals("Toi")){
                current.title=m[1].replace('\n',' ').trim();
                if(current.title.length()>60)current.title=current.title.substring(0,60)+"…";
                break;
            }
        }
        JSONArray items=new JSONArray();
        try {
            for(Chat c:chats){
                JSONObject j=new JSONObject();j.put("id",c.id);j.put("title",c.title);j.put("agent",c.agent);
                j.put("updated",c.updated);j.put("created",c.created);j.put("pinned",c.pinned);j.put("archived",c.archived);
                JSONArray a=new JSONArray();
                for(String[] m:c.messages){JSONObject q=new JSONObject();q.put("role",m[0]);q.put("text",m[1]);q.put("time",m.length>2?m[2]:"");q.put("id",m[3]);q.put("createdAt",m[4]);q.put("attachments",new JSONArray(m[5]));q.put("status",m.length>6?m[6]:"complete");a.put(q);}
                j.put("messages",a);items.put(j);
            }
            SharedPreferences.Editor edit=prefs.edit().putString("sarah_chats_v3",items.toString()).putString("sarah_current_chat",current.id);
            if(synchronous)return edit.commit();edit.apply();return true;
        }catch(JSONException ignored){return false;}
    }
    List<Chat> filter(String query,boolean archived){
        ArrayList<Chat> out=new ArrayList<>();
        for(Chat c:chats){
            if(c.archived!=archived)continue;
            boolean hit=c.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
            if(!hit)for(String[] m:c.messages)if(m[1].toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))){hit=true;break;}
            if(hit)out.add(c);
        }
        Collections.sort(out,(a,b)->a.pinned!=b.pinned?(a.pinned?-1:1):Long.compare(b.updated,a.updated));
        return out;
    }
}
