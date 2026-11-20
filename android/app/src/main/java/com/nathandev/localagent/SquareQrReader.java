package com.nathandev.localagent;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.*;

final class SquareQrReader {
    static String decode(Bitmap bitmap)throws Exception{
        if(bitmap==null||bitmap.getWidth()>4096||bitmap.getHeight()>4096)throw BridgeProtocol.error("Image de QR trop grande ou illisible.");
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        LuminanceSource source=new RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels);
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.TRY_HARDER,true);hints.put(DecodeHintType.CHARACTER_SET,"UTF-8");
        try{return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)),hints).getText();}
        catch(NotFoundException e){return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source.invert())),hints).getText();}
    }
}
