package com.powerduck.jinjugame;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.*;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public class MainActivity extends Activity {
 WebView web;
 String pending=null;
 static final int REQ_CAMERA=201;
 static final int REQ_LOCATION=202;
 static final int REQ_FILE=203;
 PermissionRequest pendingWebRequest=null;
 GeolocationPermissions.Callback pendingGeoCallback=null;
 String pendingGeoOrigin=null;
 ValueCallback<Uri[]> pendingFileCallback=null;

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  web=new WebView(this);
  setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true);
  s.setAllowContentAccess(true);
  s.setGeolocationEnabled(true);
  s.setMediaPlaybackRequiresUserGesture(false);
  Haptics h=new Haptics();
  web.addJavascriptInterface(h,"NativeHaptics");
  web.addJavascriptInterface(h,"AndroidHaptics");
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String u){
    if(pending!=null){sendUnlockCode(pending);pending=null;}
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public void onPermissionRequest(final PermissionRequest request){
    runOnUiThread(()->{
     boolean camera=false;
     for(String r:request.getResources()) if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) camera=true;
     if(camera && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
      pendingWebRequest=request;
      requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
     }else request.grant(request.getResources());
    });
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){
    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
     cb.invoke(origin,true,false);
    }else{
     pendingGeoOrigin=origin;pendingGeoCallback=cb;
     requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
    }
   }
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
    if(pendingFileCallback!=null) pendingFileCallback.onReceiveValue(null);
    pendingFileCallback=cb;
    try{
     Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
     startActivityForResult(i,REQ_FILE);return true;
    }catch(Exception e){pendingFileCallback=null;return false;}
   }
  });
  read(getIntent());
  web.loadUrl("file:///android_asset/index.html");
 }

 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults);
  if(requestCode==REQ_CAMERA){
   boolean ok=checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
   if(pendingWebRequest!=null){if(ok)pendingWebRequest.grant(pendingWebRequest.getResources());else pendingWebRequest.deny();pendingWebRequest=null;}
  }else if(requestCode==REQ_LOCATION){
   boolean ok=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
   if(pendingGeoCallback!=null){pendingGeoCallback.invoke(pendingGeoOrigin,ok,false);pendingGeoCallback=null;pendingGeoOrigin=null;}
  }
 }

 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);read(i);if(pending!=null){sendUnlockCode(pending);pending=null;}}
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  if(requestCode==REQ_FILE){
   if(pendingFileCallback!=null){Uri[] r=null;if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)r=new Uri[]{data.getData()};pendingFileCallback.onReceiveValue(r);pendingFileCallback=null;}return;
  }
  super.onActivityResult(requestCode,resultCode,data);
 }
 void read(Intent i){Uri u=i==null?null:i.getData();if(u!=null&&"jinjumarket".equals(u.getScheme())&&"unlock".equals(u.getHost()))pending=u.getQueryParameter("game");}
 void sendUnlockCode(String code){final String raw=code==null?"":code;web.evaluateJavascript("window.unlockFromNative&&window.unlockFromNative("+JSONObject.quote(raw)+")",null);}

 public class Haptics {
  Vibrator vib(){
   try{
    if(android.os.Build.VERSION.SDK_INT>=31){VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);return vm==null?null:vm.getDefaultVibrator();}
    return (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
   }catch(Exception e){return null;}
  }
  int one(long ms){
   try{Vibrator v=vib();if(v==null||!v.hasVibrator())return 0;ms=Math.max(32,Math.min(ms,1200));if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,255));else v.vibrate(ms);return 1;}catch(Exception e){return 0;}
  }
  @JavascriptInterface public int available(){try{Vibrator v=vib();return v!=null&&v.hasVibrator()?1:0;}catch(Exception e){return 0;}}
  @JavascriptInterface public int vibrate(int ms){return one(ms);}
  @JavascriptInterface public int pattern(String json){
   try{org.json.JSONArray a=new org.json.JSONArray(json);int n=Math.min(a.length(),24);if(n<1)return 0;long[] t=new long[n+1];int[] amp=new int[n+1];for(int i=0;i<n;i++){long x=Math.max(0,Math.min(a.optLong(i,0),1200));boolean on=i%2==0;t[i+1]=on?Math.max(32,x):Math.max(12,x);amp[i+1]=on?255:0;}Vibrator v=vib();if(v==null||!v.hasVibrator())return 0;if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(t,amp,-1));else v.vibrate(t,-1);return 1;}catch(Exception e){return 0;}
  }
  @JavascriptInterface public int bomb(){return pattern("[180,45,260,50,360]");}
 }
}
