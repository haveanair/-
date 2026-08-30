package com.powerduck.jinjugame;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.Manifest;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
 WebView web;
 String pending=null;
 static final int REQ_CAMERA=201;
 static final int REQ_LOCATION=202;
 static final int REQ_FILE=203;
 PermissionRequest pendingWebRequest=null;
 GeolocationPermissions.Callback pendingGeoCallback=null;
 String pendingGeoOrigin=null;
 boolean pendingQrLaunch=false;
 ValueCallback<Uri[]> pendingFileCallback=null;

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  web=new WebView(this);setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);
  s.setGeolocationEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);
  Haptics haptics=new Haptics();
  web.addJavascriptInterface(haptics,"NativeHaptics");
  web.addJavascriptInterface(haptics,"AndroidHaptics");
  web.addJavascriptInterface(new QrBridge(),"NativeQr");

  web.setWebChromeClient(new WebChromeClient(){
   @Override public void onPermissionRequest(final PermissionRequest request){
    runOnUiThread(()->{
     boolean wantsCamera=false;
     for(String r:request.getResources())if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r))wantsCamera=true;
     if(wantsCamera && android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
      pendingWebRequest=request;requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
     }else request.grant(request.getResources());
    });
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){
    if(android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
     callback.invoke(origin,true,false);return;
    }
    pendingGeoOrigin=origin;pendingGeoCallback=callback;
    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
   }
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> filePathCallback,FileChooserParams params){
    if(pendingFileCallback!=null)pendingFileCallback.onReceiveValue(null);
    pendingFileCallback=filePathCallback;
    try{
     Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
     startActivityForResult(i,REQ_FILE);return true;
    }catch(Exception e){pendingFileCallback=null;return false;}
   }
  });

  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String u){if(pending!=null){sendUnlockCode(pending);pending=null;}}
  });
  read(getIntent());web.loadUrl("file:///android_asset/index.html");
 }

 void launchNativeQr(){
  IntentIntegrator q=new IntentIntegrator(MainActivity.this);
  q.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
  q.setPrompt("중앙시장 게임 QR을 화면 안에 맞추소");q.setBeepEnabled(true);q.setOrientationLocked(true);q.setBarcodeImageEnabled(false);q.setCameraId(0);q.initiateScan();
 }
 void requestNativeQr(){
  if(android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){launchNativeQr();return;}
  pendingQrLaunch=true;requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
 }
 void jsError(String msg){final String m=msg;web.post(()->web.evaluateJavascript("window.onNativeQrError&&window.onNativeQrError("+JSONObject.quote(m)+")",null));}

 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults);
  if(requestCode==REQ_CAMERA){
   boolean granted=android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
   if(pendingWebRequest!=null){if(granted)pendingWebRequest.grant(pendingWebRequest.getResources());else pendingWebRequest.deny();pendingWebRequest=null;}
   if(pendingQrLaunch){pendingQrLaunch=false;if(granted)launchNativeQr();else jsError("카메라 권한이 거부되었습니다. 설정에서 카메라 권한을 허용하소.");}
  }else if(requestCode==REQ_LOCATION){
   boolean granted=android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
   if(pendingGeoCallback!=null){pendingGeoCallback.invoke(pendingGeoOrigin,granted,false);pendingGeoCallback=null;pendingGeoOrigin=null;}
  }
 }

 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);read(i);if(pending!=null){sendUnlockCode(pending);pending=null;}}
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  if(requestCode==REQ_FILE){
   if(pendingFileCallback!=null){Uri[] r=null;if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)r=new Uri[]{data.getData()};pendingFileCallback.onReceiveValue(r);pendingFileCallback=null;}return;
  }
  IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
  if(result!=null){
   if(result.getContents()!=null){final String raw=result.getContents();web.post(()->web.evaluateJavascript("window.onNativeQrResult&&window.onNativeQrResult("+JSONObject.quote(raw)+")",null));}
   else web.post(()->web.evaluateJavascript("window.onNativeQrCancelled&&window.onNativeQrCancelled()",null));return;
  }
  super.onActivityResult(requestCode,resultCode,data);
 }
 void read(Intent i){Uri u=i.getData();if(u!=null&&"jinjumarket".equals(u.getScheme())&&"unlock".equals(u.getHost()))pending=u.getQueryParameter("game");}
 void sendUnlockCode(String code){final String raw=code==null?"":code;web.evaluateJavascript("window.unlockFromNative&&window.unlockFromNative("+JSONObject.quote(raw)+")",null);}
 public class QrBridge {@JavascriptInterface public void openScanner(){runOnUiThread(()->requestNativeQr());}}
 public class Haptics {
  Vibrator getVibratorCompat(){
   try{
    if(android.os.Build.VERSION.SDK_INT>=31){
     VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
     return vm==null?null:vm.getDefaultVibrator();
    }
    return (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
   }catch(Exception e){return null;}
  }
  int oneShot(long ms){
   try{
    Vibrator v=getVibratorCompat();if(v==null||!v.hasVibrator())return 0;
    ms=Math.max(32,Math.min(ms,1200));
    if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,255));
    else v.vibrate(ms);
    return 1;
   }catch(Exception ignored){return 0;}
  }
  int vibrateWave(long[] timings,int[] amps){
   try{
    Vibrator v=getVibratorCompat();if(v==null||!v.hasVibrator())return 0;
    if(android.os.Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(timings,amps,-1));
    else v.vibrate(timings,-1);
    return 1;
   }catch(Exception ignored){return 0;}
  }
  @JavascriptInterface public int available(){try{Vibrator v=getVibratorCompat();return v!=null&&v.hasVibrator()?1:0;}catch(Exception e){return 0;}}
  @JavascriptInterface public int vibrate(int ms){return oneShot(ms);}
  @JavascriptInterface public int pattern(String json){
   try{
    org.json.JSONArray a=new org.json.JSONArray(json);int n=Math.min(a.length(),24);if(n<=0)return 0;
    long[] timings=new long[n+1];int[] amps=new int[n+1];timings[0]=0;amps[0]=0;
    for(int i=0;i<n;i++){
     long raw=Math.max(0,Math.min(a.optLong(i,0),1200));
     boolean on=(i%2==0);timings[i+1]=on?Math.max(32,raw):Math.max(12,raw);amps[i+1]=on?255:0;
    }
    return vibrateWave(timings,amps);
   }catch(Exception ignored){return 0;}
  }
  @JavascriptInterface public int bomb(){
   return vibrateWave(new long[]{0,180,45,260,50,360},new int[]{0,255,0,255,0,255});
  }
 }
}
