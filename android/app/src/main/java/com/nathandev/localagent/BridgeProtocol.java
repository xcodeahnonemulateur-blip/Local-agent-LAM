package com.nathandev.localagent;

import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.*;
import java.util.*;

/** Wire contract shared with Desktop/src/main/network.ts and DesktopBridge/Protocol.swift. */
final class BridgeProtocol {
    static final int MAX_BYTES=16*1024*1024;
    static final String PREFERENCE_ID="00000000-0000-4000-8000-000000000001";
    static final class Invitation {
        final JSONObject json;
        final String id,name,pin,secret;
        final JSONArray endpoints;
        final long expires;
        Invitation(String text)throws Exception{
            if(text.getBytes(StandardCharsets.UTF_8).length>8192)throw error("QR code trop volumineux.");
            json=new JSONObject(text);
            id=json.getString("desktopId");name=json.getString("name");pin=json.getString("certificateSha256");
            secret=json.getString("secret");endpoints=json.getJSONArray("endpoints");expires=date(json.getString("expiresAt"));
            if(!"saraya-pair".equals(json.getString("protocol"))||json.getInt("version")!=1||!uuid(id)||
                name.trim().isEmpty()||name.length()>120||name.matches("(?s).*[\\x00-\\x1f].*")||
                !pin.matches("[a-f0-9]{64}")||!secret.matches("(?:[A-Za-z0-9_-]{22}|[A-Za-z0-9_-]{43})")||
                endpoints.length()<1||endpoints.length()>9)throw error("Ce QR ne contient pas une invitation Local Agent valide.");
            long left=expires-System.currentTimeMillis();
            if(left<=0||left>300000)throw error("QR expiré. Affiche un nouveau QR carré sur le PC.");
            for(int i=0;i<endpoints.length();i++)endpoint(endpoints.getString(i));
        }
    }
    static URI endpoint(String value)throws Exception{
        URI uri=new URI(value);String host=uri.getHost(),path=uri.getRawPath();
        if(!"https".equals(uri.getScheme())||host==null||!localIpv4(host)||uri.getPort()<1||uri.getPort()>65535||
            uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||
            !(path==null||path.isEmpty()||"/".equals(path)))throw error("Seule une adresse HTTPS du réseau local est autorisée.");
        return uri;
    }
    static boolean localIpv4(String host){
        String[] parts=host.split("\\.",-1);if(parts.length!=4)return false;
        int[] n=new int[4];
        for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}
        return n[0]==10||n[0]==127||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==169&&n[1]==254);
    }
    static long date(String text)throws Exception{
        if(!text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z"))throw error("Date invalide.");
        String base=text.substring(0,19),fraction=text.length()>20?text.substring(20,text.length()-1):"";
        String normalized=base+"."+((fraction+"000").substring(0,3))+"Z";
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.parse(normalized).getTime();
    }
    static String timestamp(long value){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(value));
    }
    static boolean uuid(String s){return s!=null&&s.matches("(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    static String sha(byte[] data)throws Exception{
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))s.append(String.format(Locale.US,"%02x",b&255));return s.toString();
    }
    static Exception error(String message){return new Exception(message);}
    static String canonical(Object value)throws JSONException{
        if(value==null||value==JSONObject.NULL)return "null";
        if(value instanceof JSONObject){
            JSONObject j=(JSONObject)value;ArrayList<String> keys=new ArrayList<>();j.keys().forEachRemaining(keys::add);Collections.sort(keys);
            ArrayList<String> fields=new ArrayList<>();for(String key:keys)fields.add(JSONObject.quote(key)+":"+canonical(j.get(key)));return "{"+android.text.TextUtils.join(",",fields)+"}";
        }
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;ArrayList<String> out=new ArrayList<>();for(int i=0;i<a.length();i++)out.add(canonical(a.get(i)));return "["+android.text.TextUtils.join(",",out)+"]";}
        if(value instanceof String)return JSONObject.quote((String)value);
        return value.toString();
    }
    static int compare(JSONObject a,JSONObject b)throws Exception{
        int version=Long.compare(a.getLong("version"),b.getLong("version"));if(version!=0)return version;
        int time=Long.compare(date(a.getString("updatedAt")),date(b.getString("updatedAt")));if(time!=0)return time;
        int origin=a.getString("origin").compareTo(b.getString("origin"));return origin!=0?origin:canonical(a).compareTo(canonical(b));
    }
    static void keys(JSONObject o,String required,String optional)throws Exception{
        Set<String> allowed=new HashSet<>(Arrays.asList((required+","+optional).split(",")));
        for(String key:required.split(","))if(!key.isEmpty()&&!o.has(key))throw error("Champ absent : "+key);
        Iterator<String> it=o.keys();while(it.hasNext())if(!allowed.contains(it.next()))throw error("Champ inconnu dans le transfert.");
    }
    static void string(JSONObject o,String key,int min,int max)throws Exception{
        Object v=o.get(key);if(!(v instanceof String)||((String)v).length()<min||((String)v).length()>max)throw error("Champ invalide : "+key);
    }
    static void agent(String name)throws Exception{
        if(!Arrays.asList("sarah","raphael","tom","yohan","nathan","ethel").contains(name))throw error("Agent inconnu.");
    }
    static void attachment(JSONObject a)throws Exception{
        keys(a,"id,name,mime,size,sha256","contentBase64");string(a,"name",1,250);string(a,"mime",0,120);
        if(!uuid(a.getString("id"))||a.getLong("size")<0||a.getLong("size")>4*1024*1024||
            !a.getString("sha256").matches("[a-f0-9]{64}"))throw error("Pièce jointe invalide.");
        if(a.has("contentBase64")){
            String encoded=a.getString("contentBase64");if(encoded.length()>6*1024*1024||
                !encoded.matches("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?"))throw error("Encodage de pièce jointe invalide.");
            byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
            if(bytes.length!=a.getLong("size")||!sha(bytes).equals(a.getString("sha256")))throw error("Pièce jointe corrompue.");
        }
    }
    static void validate(JSONObject payload)throws Exception{
        keys(payload,"schemaVersion,records","");if(payload.getInt("schemaVersion")!=1)throw error("Version de synchronisation incompatible.");
        JSONArray records=payload.getJSONArray("records");if(records.length()>20000||payload.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw error("Transfert limité à 16 Mio.");
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<records.length();i++){
            JSONObject r=records.getJSONObject(i);keys(r,"id,kind,version,updatedAt,origin,deleted,data","");
            String id=r.getString("id"),kind=r.getString("kind");JSONObject d=r.getJSONObject("data");
            if(!uuid(id)||!uuid(r.getString("origin"))||!ids.add(id)||r.getLong("version")<1||r.getLong("version")>=9007199254740991L||
                !(r.get("deleted") instanceof Boolean)||("preference".equals(kind)!=PREFERENCE_ID.equals(id)))throw error("Enregistrement invalide.");
            date(r.getString("updatedAt"));
            switch(kind){
                case "conversation":keys(d,"title,agentId,projectId,createdAt","");string(d,"title",1,180);agent(d.getString("agentId"));date(d.getString("createdAt"));
                    if(!d.isNull("projectId")&&!uuid(d.getString("projectId")))throw error("Projet invalide.");break;
                case "message":keys(d,"conversationId,role,content,createdAt,attachments","status");string(d,"content",0,500000);date(d.getString("createdAt"));
                    if(!uuid(d.getString("conversationId"))||!Arrays.asList("user","assistant").contains(d.getString("role")))throw error("Message invalide.");
                    if(d.has("status")&&!Arrays.asList("complete","interrupted","error").contains(d.getString("status")))throw error("Statut invalide.");
                    JSONArray files=d.getJSONArray("attachments");if(files.length()>10)throw error("Trop de pièces jointes.");for(int n=0;n<files.length();n++)attachment(files.getJSONObject(n));break;
                case "project":keys(d,"name,color","");string(d,"name",1,100);if(!d.getString("color").matches("#[a-fA-F0-9]{6}"))throw error("Couleur invalide.");break;
                case "attachment":attachment(d);if(!id.equals(d.getString("id")))throw error("Identifiant de pièce jointe incohérent.");break;
                case "preference":keys(d,"theme,defaultAgent,voiceEnabled","");agent(d.getString("defaultAgent"));
                    if(!Arrays.asList("dark","light","system").contains(d.getString("theme"))||!(d.get("voiceEnabled") instanceof Boolean))throw error("Préférences invalides.");break;
                default:throw error("Type de donnée inconnu.");
            }
        }
    }
}
