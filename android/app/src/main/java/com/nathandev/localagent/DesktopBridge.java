package com.nathandev.localagent;

import android.content.*;
import android.os.*;
import android.security.keystore.*;
import android.util.*;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;

/** Pinned HTTPS client for the square Saraya QR, with Android Keystore protected trust. */
final class DesktopBridge implements AutoCloseable {
    interface Callback { void done(JSONObject status,String error); }
    private final Context context;
    private final SharedPreferences settings;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicFile trustFile;
    private final String alias,deviceId;
    private final BridgeSyncStore ledger;
    private JSONArray desktops;
    private volatile boolean busy,connected;
    private JSONObject active;
    private String activeEndpoint="",detail="Aucun ordinateur associé";
    private JSONObject pending;

    DesktopBridge(Context c,SharedPreferences p)throws Exception{this(c,p,"");}
    DesktopBridge(Context c,SharedPreferences p,String testNamespace)throws Exception{
        context=c.getApplicationContext();settings=p;alias="local_agent_bridge_v1"+testNamespace;
        trustFile=new AtomicFile(new File(c.getNoBackupFilesDir(),"desktop-trust"+testNamespace+".enc"));
        deviceId=p.getString("bridge_device_id",UUID.randomUUID().toString());p.edit().putString("bridge_device_id",deviceId).apply();
        desktops=loadTrust();ledger=new BridgeSyncStore(c,p,deviceId,testNamespace);
        if(desktops.length()>0){active=desktops.getJSONObject(0);detail="Ordinateur enregistré : "+active.getString("name");}
    }
    String deviceId(){return deviceId;}
    boolean isBusy(){return busy;}
    synchronized JSONObject status(){
        try{return new JSONObject().put("connected",connected).put("detail",detail).put("name",active==null?"":active.optString("name"))
            .put("pending",pending==null?JSONObject.NULL:pending).put("paired",desktops.length()>0);}catch(Exception e){return new JSONObject();}
    }
    private javax.crypto.SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return ((KeyStore.SecretKeyEntry)store.getEntry(alias,null)).getSecretKey();
        if(trustFile.getBaseFile().exists())throw BridgeProtocol.error("La clé locale du jumelage n'est plus disponible.");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private JSONArray loadTrust()throws Exception{
        if(!trustFile.getBaseFile().exists())return new JSONArray();
        byte[] encoded=trustFile.readFully();if(encoded.length<29)throw BridgeProtocol.error("Stockage du jumelage endommagé.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(encoded,0,12)));
        return new JSONArray(new String(cipher.doFinal(Arrays.copyOfRange(encoded,12,encoded.length)),StandardCharsets.UTF_8));
    }
    private void saveTrust(JSONArray next)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(next.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;
        try{out=trustFile.startWrite();out.write(cipher.getIV());out.write(encrypted);trustFile.finishWrite(out);desktops=next;}
        catch(Exception e){if(out!=null)trustFile.failWrite(out);throw e;}
    }
    private void async(Callable<JSONObject> task,Callback callback){
        if(busy){callback.done(status(),"Une opération est déjà en cours.");return;}busy=true;
        worker.execute(()->{String error=null;try{task.call();}catch(Exception e){error=e.getMessage()==null?"Connexion impossible.":e.getMessage();}
            busy=false;String result=error;main.post(()->callback.done(status(),result));});
    }
    void pair(BridgeProtocol.Invitation invitation,Callback callback){async(()->pairSync(invitation),callback);}
    void refresh(Callback callback){async(this::refreshSync,callback);}
    void respond(String requestId,boolean accepted,Callback callback){async(()->respondSync(requestId,accepted),callback);}

    synchronized JSONObject pairSync(BridgeProtocol.Invitation invitation)throws Exception{
        if(invitation.expires<=System.currentTimeMillis())throw BridgeProtocol.error("QR expiré. Scanne le nouveau code.");
        JSONObject known=null;
        for(int i=0;i<desktops.length();i++)if(invitation.id.equals(desktops.getJSONObject(i).getString("id")))known=desktops.getJSONObject(i);
        if(known!=null&&!invitation.pin.equals(known.getString("certificateSha256")))throw BridgeProtocol.error("L'identité du PC a changé. Révoque l'ancien jumelage avant de le refaire.");
        Exception last=BridgeProtocol.error("PC indisponible. Vérifie le même réseau Wi-Fi.");
        for(int i=0;i<invitation.endpoints.length();i++){
            String endpoint=invitation.endpoints.getString(i);
            try{
                JSONObject computer;
                if(known!=null){
                    JSONObject reply=request(endpoint,invitation.pin,"/v1/status",known.getString("token"),null);
                    verifyIdentity(reply,invitation.id);computer=new JSONObject(known.toString());computer.put("endpoints",invitation.endpoints);
                }else{
                    JSONObject body=new JSONObject().put("secret",invitation.secret).put("deviceId",deviceId).put("name",Build.MANUFACTURER+" "+Build.MODEL+" · Android");
                    JSONObject reply=request(endpoint,invitation.pin,"/v1/pair",null,body);verifyIdentity(reply,invitation.id);
                    String token=reply.getString("token");if(!token.matches("[A-Za-z0-9_-]{43}"))throw BridgeProtocol.error("Réponse de jumelage invalide.");
                    computer=new JSONObject().put("id",invitation.id).put("name",reply.getString("name")).put("certificateSha256",invitation.pin).put("token",token).put("endpoints",invitation.endpoints);
                }
                JSONArray next=new JSONArray();next.put(computer);for(int n=0;n<desktops.length();n++)if(!invitation.id.equals(desktops.getJSONObject(n).getString("id")))next.put(desktops.getJSONObject(n));
                saveTrust(next);active=computer;activeEndpoint=endpoint;connected=true;detail="Connecté à "+computer.getString("name");return status();
            }catch(Exception e){last=e;}
        }
        throw last;
    }
    private void verifyIdentity(JSONObject reply,String id)throws Exception{
        if(reply.optInt("protocolVersion")!=1||!id.equals(reply.optString("desktopId")))throw BridgeProtocol.error("Identité ou version du PC invalide.");
    }
    synchronized JSONObject refreshSync()throws Exception{
        pending=null;
        for(int n=0;n<desktops.length();n++){
            JSONObject pc=desktops.getJSONObject(n);JSONArray endpoints=pc.getJSONArray("endpoints");ArrayList<String> candidates=new ArrayList<>();
            if(active!=null&&active.optString("id").equals(pc.getString("id"))&&!activeEndpoint.isEmpty())candidates.add(activeEndpoint);
            for(int i=0;i<endpoints.length();i++)if(!candidates.contains(endpoints.getString(i)))candidates.add(endpoints.getString(i));
            for(String endpoint:candidates){
                try{
                    JSONObject reply=request(endpoint,pc.getString("certificateSha256"),"/v1/status",pc.getString("token"),null);verifyIdentity(reply,pc.getString("id"));
                    active=pc;activeEndpoint=endpoint;connected=true;detail="Connecté à "+pc.getString("name");
                    JSONObject incoming=reply.optJSONObject("pendingSync");
                    if(incoming!=null&&BridgeProtocol.uuid(incoming.optString("requestId"))&&BridgeProtocol.date(incoming.getString("expiresAt"))>System.currentTimeMillis())pending=incoming;
                    return status();
                }catch(Exception ignored){}
            }
        }
        connected=false;detail=desktops.length()==0?"Aucun ordinateur associé":"PC indisponible · tes données restent sur Android";return status();
    }
    synchronized JSONObject respondSync(String requestId,boolean accepted)throws Exception{
        if(active==null||activeEndpoint.isEmpty()||!BridgeProtocol.uuid(requestId))throw BridgeProtocol.error("Aucune demande active.");
        JSONObject pc=active;String endpoint=activeEndpoint;String pin=pc.getString("certificateSha256"),token=pc.getString("token");
        JSONObject body=new JSONObject().put("requestId",requestId).put("accepted",accepted);
        JSONObject consent=request(endpoint,pin,"/v1/sync/consent",token,body);
        if(!requestId.equals(consent.optString("requestId"))||consent.getBoolean("accepted")!=accepted)throw BridgeProtocol.error("Consentement non confirmé par le PC.");
        pending=null;
        if(!accepted){detail="Synchronisation refusée";return status();}
        try{
            JSONObject payload=ledger.exportPayload();JSONObject syncBody=new JSONObject().put("requestId",requestId).put("payload",payload);
            if(syncBody.toString().getBytes(StandardCharsets.UTF_8).length>BridgeProtocol.MAX_BYTES)throw BridgeProtocol.error("Transfert limité à 16 Mio.");
            JSONObject response=request(endpoint,pin,"/v1/sync",token,syncBody);
            if(!requestId.equals(response.optString("requestId")))throw BridgeProtocol.error("Réponse de synchronisation incohérente.");
            JSONObject report=ledger.merge(response.getJSONObject("payload"));
            detail="Synchronisation terminée · "+report.getInt("added")+" ajout(s), "+report.getInt("updated")+" modification(s)";
            return status();
        }catch(Exception e){
            try{request(endpoint,pin,"/v1/sync/consent",token,new JSONObject().put("requestId",requestId).put("accepted",false));}catch(Exception ignored){}
            throw e;
        }
    }
    synchronized void forget()throws Exception{
        saveTrust(new JSONArray());active=null;pending=null;activeEndpoint="";connected=false;detail="Association oubliée sur Android. Révoque aussi cet appareil sur le PC.";
    }
    static JSONObject request(String endpoint,String pin,String path,String token,JSONObject body)throws Exception{
        URI checked=BridgeProtocol.endpoint(endpoint);
        if(!pin.matches("[a-f0-9]{64}")||!path.matches("/v1/(?:pair|status|sync|sync/consent)"))throw BridgeProtocol.error("Requête de jumelage invalide.");
        if(token!=null&&!token.matches("[A-Za-z0-9_-]{43}"))throw BridgeProtocol.error("Jeton invalide.");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("Client certificate unsupported");}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
                try{
                    if(chain==null||chain.length==0)throw new CertificateException("Missing certificate");
                    chain[0].checkValidity();String actual=BridgeProtocol.sha(chain[0].getEncoded());
                    if(!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),pin.getBytes(StandardCharsets.US_ASCII)))throw new CertificateException("L'identité chiffrée du PC ne correspond pas au QR.");
                }catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException("Certificat du PC invalide.",e);}
            }
        };
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,new TrustManager[]{trust},new SecureRandom());
        HttpsURLConnection connection=(HttpsURLConnection)checked.resolve(path).toURL().openConnection();
        connection.setSSLSocketFactory(ssl.getSocketFactory());
        // The QR pins the full DER certificate, including for a local IP absent from its SAN.
        connection.setHostnameVerifier((host,session)->host.equals(checked.getHost()));
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(4500);connection.setReadTimeout(25000);
        connection.setRequestProperty("Accept","application/json");
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null){
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            }
            int code=connection.getResponseCode();
            if(code<200||code>=300)throw BridgeProtocol.error(code==401?"Le jumelage a été révoqué sur le PC.":code==403?"Invitation expirée ou synchronisation non autorisée.":"Le PC a refusé la requête (HTTP "+code+").");
            int max="/v1/sync".equals(path)?BridgeProtocol.MAX_BYTES:65536;
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] chunk=new byte[8192];int count;
                while((count=in.read(chunk))!=-1){if(buffer.size()+count>max)throw BridgeProtocol.error("Réponse du PC trop volumineuse.");buffer.write(chunk,0,count);}
            }
            return new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
        }finally{connection.disconnect();}
    }
    @Override public void close(){worker.shutdownNow();}
}
