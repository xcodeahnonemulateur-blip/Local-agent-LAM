package com.nathandev.localagent;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.view.inputmethod.EditorInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Native Android shell. This does not claim to reproduce unfinished iOS-only engines. */
public final class MainActivity extends Activity {
    private static final int RECORD_AUDIO_REQUEST = 20;
    private static final String PREFS = "sarah_android_settings";
    private static final String HISTORY = "conversation_v2";
    private SarahEngine engine;
    private SharedPreferences preferences;
    private SpeechRecognizer recognizer;
    private TextToSpeech speaker;
    private LinearLayout conversation;
    private EditText input;
    private ScrollView scroll;
    private final Handler handler = new Handler();
    private boolean listening;
    private boolean speakerReady;
    private boolean dark;
    private int panel;
    private boolean voiceMini = false;
    private String activeAgent="Sarah";
    private ChatHistory history;
    private TextView voiceStatus, voiceSubtitle;
    private PhoneStyle.Orb voiceOrb;
    private PhoneStyle.Icon voiceMic, composerAction;
    private String draft="";
    private Dialog drawer;
    private boolean archivedView;
    private String pendingFile="";
    private android.window.OnBackInvokedCallback predictiveBack;
    private View welcome;
    private DesktopBridge bridge;
    private TextView bridgeStatus;
    private boolean resumed;
    private final java.util.HashSet<String> handledSync=new java.util.HashSet<>();
    private final Runnable bridgePoll=new Runnable(){public void run(){
        if(!resumed||bridge==null)return;
        if(bridge.isBusy()){handler.postDelayed(this,5000);return;}
        bridge.refresh((state,error)->{updateBridgeUi(state);if(resumed)handler.postDelayed(this,5000);});
    }};
    private View agentAnchor;
    private android.widget.PopupWindow agentPopup;
    private final ArrayList<String[]> messages = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        dark = preferences.getBoolean("dark", true);
        engine = new SarahEngine(this);
        history = new ChatHistory(preferences);
        loadHistory();
        try{bridge=new DesktopBridge(this,preferences);}catch(Exception e){toast(e.getMessage());}
        if(Build.VERSION.SDK_INT>=33)predictiveBack=()->{hideKeyboard();if(panel==1)voiceMini=true;showChat();};
        if(state!=null)draft=state.getString("draft","");
        speaker = new TextToSpeech(this, status -> speakerReady = (status == TextToSpeech.SUCCESS));
        showChat();
    }

    private int bg() { return dark ? Color.BLACK : Color.rgb(242, 244, 248); }
    private int surface() { return dark ? 0xff29292e : Color.WHITE; }
    private int fg() { return dark ? Color.WHITE : Color.rgb(15, 23, 42); }
    private int muted() { return dark ? Color.rgb(139, 149, 165) : Color.rgb(100, 116, 139); }
    private int accent() { return PhoneStyle.color(activeAgent); }
    private android.graphics.drawable.Drawable phoneGlass(int radius){return PhoneStyle.glass(this,accent(),radius,dark);}
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setBackground(rounded(accent(), 24));
        b.setOnClickListener(listener);
        return b;
    }

    @Override public void setContentView(View page) {
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(bg());
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels,dp(840)),-1,Gravity.CENTER_HORIZONTAL);
        frame.addView(page,p);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            frame.setOnApplyWindowInsetsListener((v,insets)->{
                Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
                frame.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));
                return insets;
            });
        }
        getWindow().setStatusBarColor(bg());
        getWindow().setNavigationBarColor(bg());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        super.setContentView(frame);
        if(Build.VERSION.SDK_INT>=33&&predictiveBack!=null){
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(predictiveBack);
            if(panel!=0)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,predictiveBack);
        }
        frame.requestApplyInsets();
    }

    private PhoneStyle.Icon icon(String name,String label,int size,boolean glass,View.OnClickListener action){
        PhoneStyle.Icon v=new PhoneStyle.Icon(this,name,fg(),label);
        if(glass)v.setBackground(phoneGlass(size/2));
        v.setOnClickListener(action);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));
        return v;
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView title(String value,int size){TextView t=text(value,size,fg());t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private void hideKeyboard(){
        if(input!=null){draft=input.getText().toString();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}
    }
    private LinearLayout screen(String heading,String detail){
        LinearLayout root=column();root.setBackgroundColor(bg());root.setPadding(dp(16),dp(8),dp(16),dp(12));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(icon("back","Retour au chat",44,true,v->showChat()));
        TextView headingView=title(heading,20);headingView.setGravity(Gravity.CENTER);headingView.setPadding(dp(8),0,dp(8),0);
        bar.addView(headingView,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(new View(this),new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(bar);
        if(!detail.isEmpty()){TextView sub=text(detail,13,muted());sub.setPadding(dp(4),dp(15),dp(4),dp(15));root.addView(sub);}
        return root;
    }

    private void navigation(LinearLayout root) {
        if (panel == 0) return;
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, dp(10), 0, 0);
        String[] labels = {"💬 Chat", "🎙 Voix", "▦ PC", "🗺 Cartes"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = text(labels[i], 12, i == panel ? Color.WHITE : muted());
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(3), dp(13), dp(3), dp(13));
            item.setBackground(rounded(i == panel ? accent() : surface(), 19));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(47), 1);
            if (i > 0) p.leftMargin = dp(5);
            bottom.addView(item, p);
            item.setOnClickListener(v -> {
                if (index == 0) showChat();
                else if (index == 1) showVoice();
                else if (index == 2) showPairing();
                else showMaps();
            });
        }
        root.addView(bottom);
    }

    private void showChat() {
        panel=0;
        FrameLayout page=new FrameLayout(this);
        page.addView(new PhoneStyle.Background(this,accent(),dark),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=column();
        page.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setPadding(dp(12),dp(6),dp(12),dp(8));top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon("menu","Ouvrir le menu",44,true,v->{hideKeyboard();showDrawer();}));
        FrameLayout center=new FrameLayout(this);
        LinearLayout capsule=new LinearLayout(this);capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(dp(16),0,dp(12),0);capsule.setBackground(phoneGlass(22));
        TextView dot=text("●",13,accent());capsule.addView(dot);
        TextView name=title(activeAgent,16);name.setPadding(dp(8),0,dp(6),0);capsule.addView(name);
        PhoneStyle.Icon down=icon("down","Choisir un agent",26,false,v->showAgents());down.setMinimumHeight(0);capsule.addView(down);
        agentAnchor=capsule;
        capsule.setContentDescription("Choisir un agent : "+activeAgent);capsule.setOnClickListener(v->{hideKeyboard();showAgents();});
        center.addView(capsule,new FrameLayout.LayoutParams(-2,dp(44),Gravity.CENTER));
        top.addView(center,new LinearLayout.LayoutParams(0,dp(44),1));
        top.addView(new View(this),new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(top);

        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        conversation=column();conversation.setPadding(dp(16),dp(10),dp(16),dp(12));
        conversation.setBackgroundColor(Color.TRANSPARENT);scroll.addView(conversation,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String[] m:messages)renderMessage(m[0],m[1]);
        if(messages.isEmpty()){
            welcome=welcomeContent();
            conversation.addView(welcome,new LinearLayout.LayoutParams(-1,-1));
        }else welcome=null;
        if(voiceMini){
            LinearLayout mini=new LinearLayout(this);mini.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);mini.setPadding(dp(16),0,dp(16),dp(6));
            TextView status=text(listening?"Sarah écoute":"Mode vocal",12,fg());status.setPadding(dp(14),dp(12),dp(14),dp(12));status.setBackground(phoneGlass(24));status.setOnClickListener(v->showVoice());
            mini.addView(icon("stop","Arrêter le mode vocal",44,true,v->{stopListening();voiceMini=false;showChat();}));
            mini.addView(status);root.addView(mini);
        }
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.BOTTOM);composer.setPadding(dp(16),dp(3),dp(16),dp(6));
        composer.addView(icon("plus","Joindre une photo ou un fichier",50,true,v->{hideKeyboard();showAttachments();}));
        LinearLayout field=new LinearLayout(this);field.setGravity(Gravity.CENTER_VERTICAL);field.setBackground(phoneGlass(26));
        input=new ComposerEditText(this);((ComposerEditText)input).onSend(this::sendMessage);
        input.setTextColor(fg());input.setHintTextColor(0xff99999d);input.setTextSize(16);
        String placeholder=getResources().getConfiguration().screenWidthDp<350?"Message…":"Écrire à "+activeAgent+"…";
        input.setHint(pendingFile.isEmpty()?placeholder:pendingFile);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);input.setMinHeight(dp(52));input.setPadding(dp(17),dp(13),dp(4),dp(13));
        input.setBackgroundColor(Color.TRANSPARENT);input.setContentDescription("Message à "+activeAgent);
        if(Build.VERSION.SDK_INT>=33)input.setAutoHandwritingEnabled(false);
        input.setOnClickListener(v->{input.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);input.setText(draft);input.setSelection(input.length());
        field.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        field.addView(icon("mic","Dicter un message",40,false,v->startListening()));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.setMargins(dp(9),0,dp(9),0);composer.addView(field,fp);
        composerAction=icon("wave","Mode vocal Sarah",50,false,v->{
            if(!input.getText().toString().trim().isEmpty()||!pendingFile.isEmpty()){sendMessage();return;}
            if(listening){stopListening();return;}
            hideKeyboard();showVoice();
        });
        composerAction.setBackground(PhoneStyle.shape(this,accent(),25,true));composer.addView(composerAction);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence value,int a,int b,int c){draft=value.toString();boolean typed=!draft.trim().isEmpty()||!pendingFile.isEmpty();composerAction.update(typed?"send":"wave",typed?"Envoyer":"Mode vocal Sarah");}
            public void afterTextChanged(Editable e){}
        });
        boolean hasDraft=!draft.trim().isEmpty()||!pendingFile.isEmpty();
        composerAction.update(hasDraft?"send":"wave",hasDraft?"Envoyer":"Mode vocal Sarah");
        root.addView(composer);
        setContentView(page);
        if(!messages.isEmpty())scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View welcomeContent(){
        boolean coding=activeAgent.equals("Raphaël");
        boolean compact=getResources().getConfiguration().screenWidthDp<=330||getResources().getConfiguration().screenHeightDp<540;
        LinearLayout hero=column();hero.setGravity(Gravity.CENTER);hero.setPadding(dp(12),dp(compact?8:28),dp(12),dp(compact?8:28));
        FrameLayout mark=new FrameLayout(this);
        mark.setBackground(PhoneStyle.shape(this,Color.BLACK,24,false));mark.setClipToOutline(true);
        ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.lam_brand);brand.setScaleType(ImageView.ScaleType.FIT_CENTER);brand.setContentDescription("Local Agent LLM");
        mark.addView(brand,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        hero.addView(mark,new LinearLayout.LayoutParams(dp(compact?48:76),dp(compact?48:76)));
        TextView eyebrow=text(activeAgent.toUpperCase(Locale.ROOT),11,accent());eyebrow.setGravity(Gravity.CENTER);eyebrow.setLetterSpacing(.18f);eyebrow.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(0,dp(20),0,dp(10));if(!compact)hero.addView(eyebrow,ep);
        String heading=coding?"Une idée.\nOn la construit.":activeAgent.equals("Yohan")?"D’une langue\nà l’autre.":"Comment puis-je\nt’aider ?";
        TextView greeting=title(heading,compact?23:29);
        if(compact)greeting.setPadding(0,dp(14),0,0);
        greeting.setGravity(Gravity.CENTER);greeting.setLineSpacing(dp(2),1);hero.addView(greeting,new LinearLayout.LayoutParams(-1,-2));
        TextView intro=text(coding?"Prépare ton projet, écris ton code\nou retrouve ton espace de travail.":activeAgent.equals("Yohan")?"Écris en français ou en hébreu.\nRetrouve tes échanges au même endroit.":"Une question, une idée ou un projet.\nCommençons simplement.",14,muted());
        if(compact){intro.setText("Un espace pour tes idées.");intro.setTextSize(12);}
        intro.setGravity(Gravity.CENTER);intro.setLineSpacing(dp(3),1);intro.setPadding(0,dp(compact?8:12),0,dp(compact?16:24));hero.addView(intro,new LinearLayout.LayoutParams(-1,-2));
        if(coding){
            hero.addView(startAction("code","Ouvrir le Studio","Ton espace de code et d’aperçu",v->showStudio()),new LinearLayout.LayoutParams(-1,-2));
            hero.addView(startAction("new","Décrire mon projet","Commencer avec une idée",v->composePrompt("Je voudrais créer un projet : ")),actionMargin());
        }else if(activeAgent.equals("Yohan")){
            hero.addView(startAction("wave","Français ⇄ hébreu","Préparer une traduction",v->composePrompt("Traduis en hébreu : ")),new LinearLayout.LayoutParams(-1,-2));
            hero.addView(startAction("mic","Dicter un message","Utiliser la dictée Android",v->startListening()),actionMargin());
        }else{
            hero.addView(startAction("new","Commencer une discussion","Écrire ma première question",v->composePrompt("")),new LinearLayout.LayoutParams(-1,-2));
            hero.addView(startAction("wave","Passer en mode vocal","Ouvrir l’orbe de "+activeAgent,v->{hideKeyboard();showVoice();}),actionMargin());
        }
        return hero;
    }

    private LinearLayout.LayoutParams actionMargin(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);return p;
    }
    private View startAction(String glyph,String name,String detail,View.OnClickListener action){
        boolean compact=getResources().getConfiguration().screenWidthDp<=330||getResources().getConfiguration().screenHeightDp<540;
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(compact?8:12),dp(10),dp(compact?8:12));
        row.setBackground(PhoneStyle.shape(this,dark?0xff131316:Color.WHITE,18,true));
        PhoneStyle.Icon symbol=icon(glyph,name,compact?32:40,false,action);symbol.setMinimumHeight(0);symbol.color=accent();row.addView(symbol);
        LinearLayout copy=column();copy.setPadding(dp(9),0,dp(4),0);copy.addView(title(name,14));TextView sub=text(detail,11,muted());sub.setPadding(0,dp(3),0,0);copy.addView(sub);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        PhoneStyle.Icon arrow=icon("forward",name,28,false,action);arrow.color=muted();arrow.setMinimumHeight(0);row.addView(arrow);
        row.setContentDescription(name);row.setOnClickListener(action);
        return row;
    }
    private void composePrompt(String prompt){
        input.setText(prompt);input.setSelection(input.length());input.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
    }

    private void renderMessage(String sender,String content) {
        if(conversation==null)return;
        boolean user="Toi".equals(sender);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.BOTTOM);
        LinearLayout stack=column();
        if(!user){
            ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.sarah_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackground(PhoneStyle.shape(this,0xff26262b,14,true));avatar.setClipToOutline(true);
            LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(dp(28),dp(28));av.rightMargin=dp(8);av.bottomMargin=dp(30);row.addView(avatar,av);
        }
        TextView bubble=text(content,16,user?Color.WHITE:fg());bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(dp(3),1);bubble.setPadding(dp(16),dp(user?10:12),dp(16),dp(user?10:12));
        bubble.setBackground(PhoneStyle.shape(this,user?0xff1681f2:surface(),19,!user));
        bubble.setMaxWidth(Math.max(dp(150),Math.min(getResources().getDisplayMetrics().widthPixels-dp(user?72:108),dp(580))));
        stack.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
        for(int i=messages.size()-1;i>=0;i--){String[] m=messages.get(i);if(m[0].equals(sender)&&m[1].equals(content)){if(m.length>2)time=m[2];break;}}
        TextView timestamp=text(time,11,muted());timestamp.setPadding(dp(4),0,dp(8),0);meta.addView(timestamp);
        if(!user)meta.addView(icon("speaker","Lire à voix haute",30,false,v->speak(content)));
        stack.addView(meta,new LinearLayout.LayoutParams(-2,dp(30)));
        row.addView(stack,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.gravity=user?Gravity.END:Gravity.START;
        p.setMargins(user?dp(40):dp(4),dp(2),user?dp(4):dp(40),dp(8));conversation.addView(row,p);
        row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setItems(new String[]{"Copier",user?"Régénérer":"Lire à voix haute"},(d,i)->{
            if(i==0){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Message",content));}
            else if(user){input.setText(content);sendMessage();}else speak(content);
        }).show();return true;});
    }

    private void append(String who, String content) {
        if(welcome!=null&&welcome.getParent()==conversation){conversation.removeView(welcome);welcome=null;}
        messages.add(new String[]{who,content,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),java.util.UUID.randomUUID().toString(),BridgeProtocol.timestamp(System.currentTimeMillis()),"[]","complete"});
        saveHistory();
        if (panel == 0) {
            renderMessage(who, content);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void sendMessage() {
        if(input==null)return;
        String q=input.getText().toString().trim();
        if(q.isEmpty()&&pendingFile.isEmpty())return;
        if(listening)stopListening();
        String attachment=pendingFile;pendingFile="";draft="";input.setText("");
        append("Toi",attachment.isEmpty()?q:"Pièce jointe : "+attachment+(q.isEmpty()?"":"\n"+q));
        String reply=attachment.isEmpty()?engine.reply(q):"Le fichier est joint à cette discussion. Le moteur léger Android ne peut pas encore analyser son contenu.";
        append(activeAgent,reply);
        if(preferences.getBoolean("speak",false))speak(reply);
    }

    private void showVoice() {
        panel=1;voiceMini=false;
        FrameLayout page=new FrameLayout(this);page.addView(new PhoneStyle.Background(this,accent(),dark));
        LinearLayout root=column();root.setPadding(dp(18),dp(10),dp(18),dp(16));page.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon("menu","Ouvrir le menu",44,true,v->{voiceMini=true;showDrawer();}));
        LinearLayout caption=column();caption.setGravity(Gravity.CENTER);
        TextView name=title(activeAgent+"  ⌄",22);name.setGravity(Gravity.CENTER);caption.addView(name);
        TextView hint=text("Réduire le mode vocal",11,muted());hint.setGravity(Gravity.CENTER);caption.addView(hint);caption.setOnClickListener(v->{voiceMini=true;showChat();});
        top.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(icon("gear","Réglages vocaux",44,true,v->showSpeechSettings()));root.addView(top);
        ScrollView bodyScroll=new ScrollView(this);bodyScroll.setFillViewport(true);bodyScroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=column();body.setGravity(Gravity.CENTER);body.setPadding(0,dp(12),0,dp(12));bodyScroll.addView(body,new ScrollView.LayoutParams(-1,-1));
        voiceOrb=new PhoneStyle.Orb(this,accent());int size=Math.min(320,getResources().getConfiguration().screenWidthDp-36);
        body.addView(voiceOrb,new LinearLayout.LayoutParams(dp(size),dp(size)));
        voiceStatus=title(listening?"Je t’écoute.":"Mode vocal",25);voiceStatus.setGravity(Gravity.CENTER);body.addView(voiceStatus);
        voiceSubtitle=text(listening?"Parle normalement":"Touche le micro pour commencer",14,muted());voiceSubtitle.setGravity(Gravity.CENTER);voiceSubtitle.setPadding(dp(12),dp(8),dp(12),dp(12));body.addView(voiceSubtitle);
        TextView source=text("Reconnaissance vocale Android",11,muted());source.setGravity(Gravity.CENTER);body.addView(source);
        root.addView(bodyScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(icon("plus","Joindre une photo ou un fichier",50,true,v->showAttachments()));
        voiceMic=icon(listening?"stop":"mic",listening?"Arrêter la dictée":"Commencer la dictée",64,false,v->startListening());voiceMic.setBackground(PhoneStyle.shape(this,accent(),32,true));
        LinearLayout.LayoutParams micp=new LinearLayout.LayoutParams(dp(64),dp(64));micp.setMargins(dp(26),0,dp(26),0);controls.addView(voiceMic,micp);
        controls.addView(icon("down","Réduire le mode vocal",50,true,v->{voiceMini=true;showChat();}));root.addView(controls);
        setContentView(page);
    }

    private void stopListening(){
        if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}listening=false;updateVoice("Micro en pause","Touche le micro pour reprendre");
    }
    private void updateVoice(String status,String detail){
        if(panel==1&&voiceStatus!=null){voiceStatus.setText(status);voiceSubtitle.setText(detail);if(voiceMic!=null)voiceMic.update(listening?"stop":"mic",listening?"Arrêter la dictée":"Commencer la dictée");}
    }

    private void showPairing(){
        hideKeyboard();panel=2;LinearLayout root=screen("Connexion ordinateur","Associer le PC avec son QR code carré");
        ScrollView scrollPair=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(4),dp(10),dp(4),dp(16));
        LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(phoneGlass(22));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.lam_brand);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(logo,new LinearLayout.LayoutParams(dp(70),dp(70)));
        TextView label=title("Local Agent sur ton PC",21);label.setPadding(0,dp(14),0,dp(8));card.addView(label);
        bridgeStatus=text(bridge==null?"Connexion indisponible":bridge.status().optString("detail"),14,muted());bridgeStatus.setLineSpacing(dp(3),1);card.addView(bridgeStatus);
        body.addView(card);
        TextView explain=text("Sur le PC, ouvre Connexion téléphone puis affiche le QR carré. Les deux appareils doivent être sur le même réseau Wi-Fi.",14,muted());
        explain.setPadding(dp(2),dp(18),dp(2),dp(18));explain.setLineSpacing(dp(3),1);body.addView(explain);
        body.addView(button("Scanner le QR code carré",v->scanQr()),new LinearLayout.LayoutParams(-1,dp(52)));
        Button importQr=button("Importer une image du QR",v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,703);});
        body.addView(importQr,actionMargin());
        Button refresh=button("Vérifier la connexion",v->{if(bridge!=null)bridge.refresh(this::bridgeResult);});body.addView(refresh,actionMargin());
        TextView info=text("Le jumelage vérifie l’identité du PC et chiffre la connexion. Pour échanger les discussions, lance la synchronisation sur le PC puis accepte la demande sur Android.",12,muted());
        info.setPadding(dp(2),dp(20),dp(2),dp(16));info.setLineSpacing(dp(3),1);body.addView(info);
        if(bridge!=null&&bridge.status().optBoolean("paired")){
            TextView forget=text("Oublier cet ordinateur",14,0xffed6565);forget.setPadding(dp(2),dp(10),dp(2),dp(10));
            forget.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Oublier le jumelage Android ?").setMessage("Révoque aussi cet appareil depuis le PC si tu souhaites arrêter son autorisation.")
                .setNegativeButton("Annuler",null).setPositiveButton("Oublier",(d,w)->{try{bridge.forget();showPairing();}catch(Exception e){toast(e.getMessage());}}).show());body.addView(forget);
        }
        scrollPair.addView(body);root.addView(scrollPair,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void bridgeResult(JSONObject state,String error){
        if(isDestroyed())return;updateBridgeUi(state);if(error!=null)toast(error);
        else if(panel==2)showPairing();
    }
    private void updateBridgeUi(JSONObject state){
        if(isDestroyed())return;
        if(panel==2&&bridgeStatus!=null)bridgeStatus.setText(state.optString("detail"));
        JSONObject pending=state.optJSONObject("pending");
        if(!resumed||pending==null)return;
        String request=pending.optString("requestId");
        if(!handledSync.add(request))return;
        new AlertDialog.Builder(this).setTitle("Synchroniser avec "+state.optString("name")+" ?")
            .setMessage("Échanger les discussions avec ce PC ? Une sauvegarde locale sera créée et les conflits conservés.")
            .setPositiveButton("Synchroniser",(d,w)->{
                saveHistory();
                bridge.respond(request,true,(status,error)->{
                    if(isDestroyed())return;
                    if(error==null){if(input!=null)draft=input.getText().toString();history=new ChatHistory(preferences);loadHistory();if(panel==0)showChat();}
                    bridgeResult(status,error);
                });
            })
            .setNegativeButton("Refuser",(d,w)->bridge.respond(request,false,this::bridgeResult))
            .setOnCancelListener(d->bridge.respond(request,false,this::bridgeResult)).show();
    }
    private void handleSquareQr(String text){
        try{
            BridgeProtocol.Invitation invitation=new BridgeProtocol.Invitation(text);
            new AlertDialog.Builder(this).setTitle("Associer à "+invitation.name+" ?")
                .setMessage("Connecter ce téléphone à l’ordinateur du réseau local.\nEmpreinte : "+invitation.pin.substring(0,16)+"…")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Associer",(d,w)->{if(bridge==null){toast("Connexion indisponible.");return;}bridge.pair(invitation,this::bridgeResult);}).show();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("QR non reconnu").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void importSquareQr(android.net.Uri uri){
        new Thread(()->{
            String text=null,error=null;android.graphics.Bitmap bitmap=null;
            try{
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,options);}
                if(options.outWidth<=0||options.outHeight<=0)throw BridgeProtocol.error("Image illisible.");
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(in,null,options);}
                text=SquareQrReader.decode(bitmap);
            }catch(Exception e){error="Aucun QR carré lisible dans cette image.";}
            finally{if(bitmap!=null)bitmap.recycle();}
            String result=text,failure=error;handler.post(()->{if(isDestroyed())return;if(result!=null)handleSquareQr(result);else toast(failure);});
        },"qr-image-reader").start();
    }

    private void showMaps() {
        panel = 3;
        LinearLayout root = screen("Cartes & adresses", "Recherche d'adresses via les applications de cartes installées");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        EditText address = new EditText(this);
        address.setTextColor(fg());
        address.setHintTextColor(muted());
        address.setHint("Une adresse ou un lieu…");
        address.setBackground(rounded(surface(), 12));
        address.setPadding(dp(12), dp(10), dp(12), dp(10));
        body.addView(address, new LinearLayout.LayoutParams(-1, dp(58)));
        Button search = button("Ouvrir sur la carte", v -> {
            String value = address.getText().toString().trim();
            if (value.isEmpty()) { toast("Indique un lieu."); return; }
            Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(value)));
            try { startActivity(map); }
            catch (Exception e) { toast("Aucune application de cartes disponible."); }
        });
        LinearLayout.LayoutParams ps = new LinearLayout.LayoutParams(-1, dp(55));
        ps.topMargin = dp(15);
        body.addView(search, ps);
        TextView info = text("La carte intégrée 3D d'iOS (MapKit) doit être remplacée par une implémentation Android. Cette version utilise le moteur de cartes présent sur le téléphone.", 14, muted());
        info.setPadding(0, dp(20), 0, 0);
        body.addView(info);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation(root);
        setContentView(root);
    }



    private void showAgents(){ showAgents(agentAnchor); }

    private void showAgents(View anchor){
        if(agentPopup!=null&&agentPopup.isShowing()){agentPopup.dismiss();return;}
        hideKeyboard();
        LinearLayout menu=column();menu.setPadding(dp(6),dp(6),dp(6),dp(6));
        menu.setBackground(PhoneStyle.shape(this,dark?0xff232327:Color.WHITE,20,true));
        int width=Math.min(dp(264),getResources().getDisplayMetrics().widthPixels-dp(24));
        for(int i=0;i<PhoneStyle.AGENTS.length;i++){
            final String agent=PhoneStyle.AGENTS[i];
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14),0,dp(12),0);
            if(agent.equals(activeAgent))row.setBackground(PhoneStyle.shape(this,PhoneStyle.alpha(PhoneStyle.COLORS[i],24),12,false));
            TextView dot=text("●",12,PhoneStyle.COLORS[i]);dot.setGravity(Gravity.CENTER_VERTICAL);row.addView(dot,new LinearLayout.LayoutParams(dp(26),-1));
            TextView name=text(agent,16,fg());name.setGravity(Gravity.CENTER_VERTICAL);row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
            if(agent.equals(activeAgent)){TextView check=text("✓",18,fg());check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(26),-1));}
            row.setContentDescription("Agent "+agent);row.setOnClickListener(v->{
                agentPopup.dismiss();activeAgent=agent;history.current.agent=agent;saveHistory();showChat();
            });
            menu.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));
        }
        ScrollView container=new ScrollView(this);container.setVerticalScrollBarEnabled(false);container.addView(menu);
        int height=dp(276);
        boolean attached=anchor!=null&&anchor.isAttachedToWindow();
        if(attached){
            int[] point=new int[2];anchor.getLocationOnScreen(point);Rect available=new Rect();getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
            height=Math.min(height,Math.max(dp(120),available.bottom-point[1]-anchor.getHeight()-dp(14)));
        }
        agentPopup=new android.widget.PopupWindow(container,width,height,true);
        agentPopup.setBackgroundDrawable(PhoneStyle.shape(this,dark?0xff232327:Color.WHITE,20,true));
        agentPopup.setElevation(dp(24));agentPopup.setOutsideTouchable(true);
        agentPopup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
        if(attached)agentPopup.showAsDropDown(anchor,(anchor.getWidth()-width)/2,dp(8),Gravity.START);
        else agentPopup.showAtLocation(getWindow().getDecorView(),Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(82));
        android.view.animation.AnimationSet enter=new android.view.animation.AnimationSet(true);
        enter.addAnimation(new android.view.animation.AlphaAnimation(0f,1f));
        enter.addAnimation(new android.view.animation.TranslateAnimation(0,0,-dp(6),0));enter.setDuration(140);container.startAnimation(enter);
    }

    private View agentMenuAction(String glyph,String label,View.OnClickListener action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);
        PhoneStyle.Icon image=icon(glyph,label,28,false,action);image.setMinimumHeight(0);row.addView(image);
        TextView name=text(label,16,fg());name.setGravity(Gravity.CENTER_VERTICAL);name.setPadding(dp(10),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        row.setContentDescription(label);row.setOnClickListener(action);
        return row;
    }

    private void showDrawer(){
        hideKeyboard();
        drawer=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=column();root.setBackgroundColor(bg());root.setPadding(dp(20),dp(16),dp(20),dp(16));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=title("Sarah",30);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        EditText search=new EditText(this);search.setTextSize(16);search.setTextColor(fg());search.setHintTextColor(muted());search.setHint("Rechercher");search.setPadding(dp(14),dp(10),dp(14),dp(10));search.setBackground(PhoneStyle.shape(this,surface(),18,true));search.setSingleLine();search.setVisibility(View.GONE);
        header.addView(icon("search","Rechercher une discussion",50,true,v->{search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();}));
        root.addView(header);LinearLayout.LayoutParams se=new LinearLayout.LayoutParams(-1,dp(48));se.topMargin=dp(12);root.addView(search,se);
        ScrollView sc=new ScrollView(this);sc.setVerticalScrollBarEnabled(false);LinearLayout items=column();items.setPadding(0,dp(24),0,dp(16));sc.addView(items);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        Runnable populate=()->{
            items.removeAllViews();String lastGroup="";
            java.util.List<ChatHistory.Chat> chats=history.filter(search.getText().toString(),archivedView);
            if(chats.isEmpty()){items.addView(text(archivedView?"Archives":"Récents",13,muted()));TextView empty=text("Aucune discussion "+(search.length()>0?"trouvée.":"pour le moment."),15,muted());empty.setPadding(0,dp(12),0,dp(14));items.addView(empty);}
            for(ChatHistory.Chat chat:chats){
                String group=archivedView?"Archives":chat.pinned?"Épinglés":"Récents";
                if(!group.equals(lastGroup)){TextView g=text(group,13,muted());g.setPadding(0,dp(8),0,dp(12));items.addView(g);lastGroup=group;}
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                TextView name=text(chat.title,17,fg());name.setMaxLines(2);name.setPadding(0,dp(14),dp(6),dp(14));
                name.setOnClickListener(v->{saveHistory();history.current=chat;loadHistory();drawer.dismiss();showChat();});
                row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon("more","Options de discussion "+chat.title,44,false,v->{
                    new AlertDialog.Builder(this).setItems(new String[]{chat.pinned?"Désépingler":"Épingler",chat.archived?"Désarchiver":"Archiver"},(d,i)->{if(i==0)chat.pinned=!chat.pinned;else chat.archived=!chat.archived;history.save();drawer.dismiss();showDrawer();}).show();
                }));items.addView(row);
            }
            TextView archive=text(archivedView?"← Récents":"Archives  ›",16,muted());archive.setPadding(0,dp(20),0,dp(15));archive.setOnClickListener(v->{archivedView=!archivedView;drawer.dismiss();showDrawer();});items.addView(archive);
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){populate.run();}public void afterTextChanged(Editable e){}});
        populate.run();
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);
        Button fresh=button("＋  Chat",v->{saveHistory();history.newChat();messages.clear();draft="";drawer.dismiss();showChat();});fresh.setTextSize(17);
        footer.addView(fresh,new LinearLayout.LayoutParams(dp(130),dp(54)));footer.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        footer.addView(icon("gear","Réglages",54,true,v->{drawer.dismiss();showSettings();}));root.addView(footer);
        drawer.setContentView(root);drawer.show();
        android.view.Window w=drawer.getWindow();w.setGravity(Gravity.LEFT|Gravity.TOP);w.setDimAmount(.40f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setLayout(Math.min(dp(360),(int)(getResources().getDisplayMetrics().widthPixels*.84f)),-1);
        if(Build.VERSION.SDK_INT>=30){
            w.setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{
                Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                Insets ime=insets.getInsets(WindowInsets.Type.ime());
                root.setPadding(dp(20)+bars.left,dp(16)+bars.top,dp(20),dp(16)+Math.max(bars.bottom,ime.bottom));
                return insets;
            });root.requestApplyInsets();
        }
    }

    private void showAttachments(){
        new AlertDialog.Builder(this).setItems(new String[]{"Photos","Appareil photo","Fichier"},(d,i)->{
            if(i==1){Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);try{startActivityForResult(camera,702);}catch(Exception e){toast("Appareil photo indisponible.");}}
            else {Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType(i==0?"image/*":"*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,701);}
        }).show();
    }
    private void showTools(){
        new AlertDialog.Builder(this).setTitle("Outils").setItems(new String[]{"Mode vocal","Studio Raphaël","Cartes et adresses","Connexion ordinateur","Modèles"},(d,i)->{
            if(i==0)showVoice();else if(i==1)showStudio();else if(i==2)showMaps();else if(i==3)showPairing();else showModelCenter();
        }).show();
    }

    private void showModelCenter() {
        ModelDownloadCenter.show(this, dark);
    }

    private void showSettings(){
        hideKeyboard();panel=4;
        LinearLayout root=column();root.setBackgroundColor(bg());root.setPadding(dp(16),dp(8),dp(16),0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon("back","Retour au chat",44,true,v->showChat()));
        TextView heading=title("Réglages",20);heading.setGravity(Gravity.CENTER);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView done=text("Terminé",14,accent());done.setGravity(Gravity.CENTER);done.setOnClickListener(v->showChat());done.setContentDescription("Fermer les réglages");
        top.addView(done,new LinearLayout.LayoutParams(dp(60),dp(44)));root.addView(top);
        ScrollView sc=new ScrollView(this);sc.setVerticalScrollBarEnabled(false);
        LinearLayout list=column();list.setPadding(0,dp(22),0,dp(20));
        LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);identity.setPadding(dp(16),dp(16),dp(16),dp(16));identity.setBackground(phoneGlass(22));
        PhoneStyle.Icon emblem=icon("wave","Sarah Engine",50,false,v->showEngine());emblem.color=accent();identity.addView(emblem);
        LinearLayout engineText=column();engineText.setPadding(dp(10),0,0,0);engineText.addView(title("Sarah Engine",20));TextView engineState=text("Moteur local léger · "+engine.getDeviceMode().name(),12,muted());engineState.setPadding(0,dp(4),0,0);engineText.addView(engineState);
        identity.addView(engineText,new LinearLayout.LayoutParams(0,-2,1));list.addView(identity);
        TextView section=text("RÉGLAGES",12,muted());section.setLetterSpacing(.08f);section.setPadding(dp(6),dp(26),0,dp(12));list.addView(section);
        LinearLayout group=column();group.setBackground(PhoneStyle.shape(this,dark?0xff18181d:Color.WHITE,20,false));group.setClipToOutline(true);
        String[] names={"Agents","Voix et parole","Langue de dictée","Connexion ordinateur","Sarah Engine","Modèles","Apparence","À propos"};
        String[] details={"Les assistants et leurs rôles","Voix, vitesse et microphone","Français, hébreu et anglais","QR code et connexion locale","État des moteurs disponibles","Téléchargements facultatifs",dark?"Thème sombre":"Thème clair","Version et informations"};
        String[] glyphs={"new","wave","mic","code","wave","file","photo","more"};
        for(int i=0;i<names.length;i++){
            final int n=i;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinimumHeight(dp(68));
            PhoneStyle.Icon glyph=icon(glyphs[i],names[i],36,false,v->settingAction(n,row));glyph.color=PhoneStyle.COLORS[i%PhoneStyle.COLORS.length];glyph.setMinimumHeight(0);glyph.setBackground(PhoneStyle.shape(this,PhoneStyle.alpha(glyph.color,24),10,false));row.addView(glyph);
            LinearLayout copy=column();copy.setPadding(dp(12),0,dp(6),0);copy.addView(title(names[i],16));TextView detail=text(details[i],12,muted());detail.setPadding(0,dp(3),0,0);copy.addView(detail);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            PhoneStyle.Icon arrow=icon("forward",names[i],22,false,v->settingAction(n,row));arrow.color=muted();arrow.setMinimumHeight(0);row.addView(arrow);
            row.setContentDescription(names[i]);row.setOnClickListener(v->settingAction(n,row));group.addView(row);
            if(i<names.length-1){View divider=new View(this);divider.setBackgroundColor(dark?0x18ffffff:0x18000000);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(60),0,dp(12),0);group.addView(divider,lp);}
        }
        list.addView(group);
        TextView footer=text("Local Agent LLM · Android",11,muted());footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(22),0,dp(10));list.addView(footer);
        sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void settingAction(int index,View anchor){
        switch(index){
            case 0:showAgents(anchor);break;case 1:showSpeechSettings();break;case 2:chooseLanguage();break;
            case 3:showPairing();break;case 4:showEngine();break;case 5:showModelCenter();break;
            case 6:dark=!dark;preferences.edit().putBoolean("dark",dark).apply();showSettings();break;
            case 7:new AlertDialog.Builder(this).setTitle("Local Agent LLM").setMessage("Interface adaptée du code iPhone SarahAI. Le moteur léger répond localement. Whisper, la traduction continue et les gros modèles iOS nécessitent encore un moteur Android opérationnel.").setPositiveButton("OK",null).show();
        }
    }

    private void showEngine() {
        new AlertDialog.Builder(this).setTitle("Sarah Engine")
            .setMessage(engine.getDeviceSummary() + "\n\nAgents : Sarah, Raphaël, Tom, Yohan. Routage local simplifié. Aucun gros modèle neuronal n'est installé.")
            .setPositiveButton("OK", null).show();
    }

    private void showSpeechSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        CheckBox speak = new CheckBox(this);
        speak.setText("Lire les réponses à voix haute");
        speak.setChecked(preferences.getBoolean("speak", false));
        layout.addView(speak);
        CheckBox offline = new CheckBox(this);
        offline.setText("Privilégier la dictée hors ligne si disponible");
        offline.setChecked(preferences.getBoolean("offline", true));
        layout.addView(offline);
        new AlertDialog.Builder(this).setTitle("Voix et microphone").setView(layout)
            .setPositiveButton("Enregistrer", (d,w) -> preferences.edit()
                .putBoolean("speak", speak.isChecked()).putBoolean("offline", offline.isChecked()).apply())
            .setNegativeButton("Annuler", null).show();
    }

    private void chooseLanguage() {
        final String[] values = {"Français", "Hébreu", "English"};
        final String[] codes = {"fr-FR", "he-IL", "en-US"};
        new AlertDialog.Builder(this).setTitle("Langue vocale").setItems(values, (d,i) -> {
            preferences.edit().putString("language", codes[i]).apply();
            toast("Langue choisie : " + values[i]);
        }).show();
    }

    private void refreshPanel() {
        if (panel == 4) showSettings(); else if (panel == 1) showVoice(); else if (panel == 2) showPairing();
        else if (panel == 3) showMaps(); else showChat();
    }

    private void speak(String text) {
        if (!speakerReady || speaker == null) return;
        String lang=text.startsWith("Hello")||text.startsWith("I am")||text.startsWith("You can")?"en-US":preferences.getString("language","fr-FR");
        speaker.setLanguage(Locale.forLanguageTag(lang));
        speaker.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sarah-reply");
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, RECORD_AUDIO_REQUEST);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateVoice("Micro indisponible","Aucun moteur de dictée Android installé."); toast("Aucun moteur de dictée n'est installé sur ce téléphone.");
            return;
        }
        if (listening) { if (recognizer != null) recognizer.stopListening(); listening=false; updateVoice("Transcription…","Un instant…"); return; }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { updateVoice("Je t’écoute.","Parle normalement"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float value) {if(voiceOrb!=null)voiceOrb.level(value/12f);if(panel==0&&composerAction!=null&&listening)composerAction.setAlpha(.55f+Math.min(1f,Math.max(0f,value/12f))*.45f);}
            @Override public void onBufferReceived(byte[] bytes) { }
            @Override public void onEndOfSpeech() { listening = false; }
            @Override public void onError(int error) { listening = false; updateVoice("Micro indisponible","La dictée n’a pas abouti (code "+error+")."); toast("La dictée n'a pas abouti (code " + error + ")."); }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> items = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (items != null && !items.isEmpty()) {
                    draft=items.get(0);voiceMini=false;showChat();
                    input.setText(items.get(0));
                    input.setSelection(input.length());
                }
            }
            @Override public void onPartialResults(Bundle b) {ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words!=null&&!words.isEmpty())updateVoice("Je t’écoute.",words.get(0));}
            @Override public void onEvent(int id, Bundle b) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferences.getString("language", "fr-FR"));
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferences.getBoolean("offline", true));
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { listening = true; updateVoice("Activation du micro…","Reconnaissance vocale Android"); recognizer.startListening(intent); }
        catch (Exception e) { listening = false; toast("Impossible de démarrer le micro."); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == RECORD_AUDIO_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else if(requestCode==RECORD_AUDIO_REQUEST)updateVoice("Micro non autorisé","Tu peux activer le microphone dans les autorisations Android.");
    }

    private void scanQr() {
        try {
            new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Place le QR code carré du PC dans le cadre")
                .setBeepEnabled(false).setOrientationLocked(false).initiateScan();
        } catch (Exception e) { toast("Scanner indisponible sur cet appareil."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==703){if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)importSquareQr(data.getData());return;}
        if(requestCode==701||requestCode==702){
            if(resultCode==RESULT_OK){
                pendingFile=requestCode==702?"Photo de l’appareil photo":"Fichier";
                if(data!=null&&data.getData()!=null){
                    try(android.database.Cursor c=getContentResolver().query(data.getData(),null,null,null,null)){
                        if(c!=null&&c.moveToFirst()){int index=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(index>=0)pendingFile=c.getString(index);}
                    }catch(Exception ignored){}
                }
                showChat();
            }
            return;
        }
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null){if(result.getContents()!=null)handleSquareQr(result.getContents());return;}
        super.onActivityResult(requestCode,resultCode,data);
    }

    private void loadHistory(){
        messages.clear();messages.addAll(history.current.messages);activeAgent=history.current.agent;
        if(messages.isEmpty())activeAgent=preferences.getString("active_agent",activeAgent);
    }
    private void saveHistory(){
        history.current.messages.clear();history.current.messages.addAll(messages);
        history.current.agent=activeAgent;history.current.updated=System.currentTimeMillis();history.save();
        preferences.edit().putString("active_agent",activeAgent).apply();
    }

    private void showStudio(){
        hideKeyboard();panel=5;LinearLayout root=screen("Studio Raphaël","index.html");
        EditText code=new EditText(this);code.setGravity(Gravity.TOP|Gravity.START);code.setTypeface(Typeface.MONOSPACE);code.setTextColor(fg());code.setTextSize(14);
        code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setPadding(dp(12),dp(12),dp(12),dp(12));code.setBackground(PhoneStyle.shape(this,surface(),16,false));
        code.setText(preferences.getString("studio_html","<!doctype html>\n<html lang=\"fr\">\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<h1>Bonjour, Sarah.</h1>\n</html>"));
        root.addView(code,new LinearLayout.LayoutParams(-1,0,1));
        Button preview=button("Aperçu",v->{
            String html=code.getText().toString();preferences.edit().putString("studio_html",html).apply();
            Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
            LinearLayout page=column();page.setBackgroundColor(bg());Button back=button("Retour au code",x->dialog.dismiss());page.addView(back);
            android.webkit.WebView web=new android.webkit.WebView(this);
            web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setJavaScriptEnabled(false);
            web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
            dialog.setContentView(page);dialog.setOnDismissListener(x->web.destroy());dialog.show();dialog.getWindow().setLayout(-1,-1);
        });
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(52)));setContentView(root);
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    @Override protected void onSaveInstanceState(Bundle out){
        if(input!=null)draft=input.getText().toString();
        out.putString("draft",draft);super.onSaveInstanceState(out);
    }
    // API 33+ uses the registered platform predictive-back callback above.
    // This method only supplies compatibility navigation to API 24–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){
        if(panel!=0){hideKeyboard();showChat();}else super.onBackPressed();
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.removeCallbacks(bridgePoll);handler.post(bridgePoll);}
    @Override protected void onPause(){
        resumed=false;handler.removeCallbacks(bridgePoll);
        if(listening)stopListening();
        super.onPause();
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.destroy();
        if (speaker != null) speaker.shutdown();
        handler.removeCallbacks(bridgePoll);if(bridge!=null)bridge.close();
        if(agentPopup!=null)agentPopup.dismiss();
        if(Build.VERSION.SDK_INT>=33&&predictiveBack!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(predictiveBack);
    }
}
