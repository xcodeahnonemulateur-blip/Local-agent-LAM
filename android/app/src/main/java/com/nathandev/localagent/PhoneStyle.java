package com.nathandev.localagent;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;

/** Android rendering of AgentType.swift and CommonStyles.swift at source 8de7037. */
final class PhoneStyle {
    static final String[] AGENTS = {"Sarah", "Raphaël", "Yohan", "Ethel", "Nathan", "Tom"};
    static final String[] ROLES = {"Assistante principale", "Développement et code",
        "Français ⇄ hébreu", "Création et design", "Contenus et réseaux sociaux", "Recherche et connaissances"};
    static final int[] COLORS = {0xffff2ea6, 0xff26b8ff, 0xff0073e6, 0xfff22659, 0xffd98cff, 0xff0dd973};
    static int color(String agent) {
        for (int i=0;i<AGENTS.length;i++) if (AGENTS[i].equals(agent)) return COLORS[i];
        return COLORS[0];
    }
    static int dp(Context c, float n) { return (int)(n*c.getResources().getDisplayMetrics().density+.5f); }
    static int alpha(int color, int a) { return (color & 0x00ffffff) | (a<<24); }
    static GradientDrawable shape(Context c, int color, int radius, boolean border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(c,radius));
        if (border) d.setStroke(dp(c,1),0x22ffffff);
        return d;
    }
    static android.graphics.drawable.Drawable glass(Context c, int tint, int radius) {
        return glass(c,tint,radius,true);
    }
    static android.graphics.drawable.Drawable glass(Context c, int tint, int radius, boolean dark) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            dark?new int[]{blend(0xff242428,tint,.12f),0xff141418,0xff101014}
                :new int[]{blend(Color.WHITE,tint,.045f),Color.WHITE,0xfff7f8fb});
        d.setCornerRadius(dp(c,radius)); d.setStroke(dp(c,1),dark?0x2bffffff:0x22182534);
        return new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x15000000), d, null);
    }
    static int blend(int a,int b,float f) {
        return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),
            (int)(Color.green(a)*(1-f)+Color.green(b)*f),
            (int)(Color.blue(a)*(1-f)+Color.blue(b)*f));
    }
    static final class Background extends View {
        private final Paint p=new Paint(3);
        private final int color;
        private final boolean dark;
        private Shader glow;
        Background(Context c,int tint) {this(c,tint,true);}
        Background(Context c,int tint,boolean isDark) {super(c);color=tint;dark=isDark;p.setDither(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            glow=new RadialGradient(w*.5f,-dp(getContext(),90),dp(getContext(),390),
                dark?new int[]{blend(Color.BLACK,color,.105f),blend(Color.BLACK,color,.055f),Color.BLACK}
                    :new int[]{blend(0xfff8f9fc,color,.05f),blend(0xfff8f9fc,color,.025f),0xfff8f9fc},
                new float[]{0,.40f,1},Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c) {
            c.drawColor(dark?Color.BLACK:0xfff8f9fc);
            p.setShader(glow);
            c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setShader(null);
        }
    }
    static final class Icon extends View {
        private final Paint p=new Paint(3);
        String name; int color;
        Icon(Context c,String icon,int tint,String description) {
            super(c);name=icon;color=tint;setContentDescription(description);
            setFocusable(true);setClickable(true);setMinimumHeight(dp(c,44));
        }
        void update(String icon,String description) {name=icon;setContentDescription(description);invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save(); float side=dp(getContext(),22);
            canvas.translate((getWidth()-side)/2,(getHeight()-side)/2);canvas.scale(side/24,side/24);
            p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setShader(null);
            switch(name) {
                case "menu": line(canvas,4,6,20,6);line(canvas,4,12,20,12);line(canvas,4,18,20,18);break;
                case "plus":line(canvas,12,4,12,20);line(canvas,4,12,20,12);break;
                case "send":line(canvas,12,20,12,4);line(canvas,5,11,12,4);line(canvas,19,11,12,4);break;
                case "down":line(canvas,7,10,12,15);line(canvas,12,15,17,10);break;
                case "back":line(canvas,15,5,8,12);line(canvas,8,12,15,19);break;
                case "forward":line(canvas,9,5,16,12);line(canvas,16,12,9,19);break;
                case "close":line(canvas,6,6,18,18);line(canvas,18,6,6,18);break;
                case "mic":
                    canvas.drawRoundRect(9,3,15,15,3,3,p);
                    canvas.drawArc(6,7,18,19,0,180,false,p);
                    line(canvas,12,19,12,22);line(canvas,9,22,15,22);break;
                case "wave":
                    for(int i=0;i<5;i++){float h=new float[]{7,14,20,12,6}[i];line(canvas,4+i*4,12-h/2,4+i*4,12+h/2);}break;
                case "stop":p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(7,7,17,17,2,2,p);break;
                case "search":canvas.drawCircle(10,10,6,p);line(canvas,15,15,21,21);break;
                case "new":canvas.drawRoundRect(3,5,18,21,3,3,p);line(canvas,10,14,21,3);line(canvas,10,14,9,18);line(canvas,9,18,13,17);break;
                case "speaker":
                    path(canvas,new float[]{3,9,7,9,12,5,12,19,7,15,3,15,3,9});
                    canvas.drawArc(9,7,21,17,-65,130,false,p);canvas.drawArc(10,3,26,21,-55,110,false,p);break;
                case "code":line(canvas,8,6,2,12);line(canvas,2,12,8,18);line(canvas,16,6,22,12);line(canvas,22,12,16,18);line(canvas,14,4,10,20);break;
                case "photo":canvas.drawRoundRect(3,4,21,20,3,3,p);canvas.drawCircle(8,9,1.5f,p);path(canvas,new float[]{4,17,10,12,14,16,18,11,21,14});break;
                case "camera":canvas.drawRoundRect(2,6,22,20,3,3,p);canvas.drawCircle(12,13,4,p);path(canvas,new float[]{7,6,9,3,15,3,17,6});break;
                case "file":path(canvas,new float[]{5,2,15,2,20,7,20,22,5,22,5,2});line(canvas,15,2,15,7);line(canvas,15,7,20,7);break;
                case "copy":canvas.drawRoundRect(8,7,21,21,2,2,p);path(canvas,new float[]{16,3,3,3,3,16});break;
                case "more":p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(5+i*7,12,1.4f,p);break;
                case "gear":
                    canvas.drawCircle(12,12,3,p);canvas.drawCircle(12,12,7,p);
                    for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);line(canvas,12,2,12,5);canvas.restore();}break;
                default:canvas.drawRoundRect(4,5,20,19,4,4,p);line(canvas,8,10,16,10);line(canvas,8,14,14,14);
            }
            canvas.restore();
        }
        private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
        private void path(Canvas c,float[] points){Path q=new Path();q.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)q.lineTo(points[i],points[i+1]);c.drawPath(q,p);}
    }
    static final class Orb extends View {
        private final Paint p=new Paint(3);
        private final int color;
        private float level;
        Orb(Context c,int tint){super(c);color=tint;setContentDescription("Orbe vocal Sarah");}
        void level(float value){level=Math.max(0,Math.min(1,value));invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)*.72f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx,cy,r*1.42f,new int[]{alpha(color,90),alpha(color,20),0x00000000},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r*1.42f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),1));p.setColor(alpha(color,85));
            canvas.drawCircle(cx,cy,r*(1.14f+level*.025f),p);p.setColor(alpha(color,38));canvas.drawCircle(cx,cy,r*1.31f,p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setShader(new LinearGradient(cx-r*.7f,cy-r,cx+r*.6f,cy+r,new int[]{0xfffff0fa,blend(Color.WHITE,color,.55f),color,blend(color,Color.BLACK,.25f)},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r*(1+level*.03f),p);
            p.setShader(new RadialGradient(cx-r*.32f,cy-r*.4f,r*.86f,new int[]{0xb3ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r,p);p.setShader(null);
        }
    }
}
