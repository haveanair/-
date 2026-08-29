package com.powerduck.jinjugame;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import java.io.InputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
 WebView web;
 String pending=null;
 static final int REQ_CAMERA=201,REQ_LOCATION=202,REQ_FILE=203;
 PermissionRequest pendingWebRequest=null;
 GeolocationPermissions.Callback pendingGeoCallback=null;
 String pendingGeoOrigin=null;
 ValueCallback<Uri[]> pendingFileCallback=null;
 Uri pendingCameraUri=null;

 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  web=new WebView(this);setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);
  s.setGeolocationEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);
  if(Build.VERSION.SDK_INT>=21)s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
  Haptics h=new Haptics();
  web.addJavascriptInterface(h,"NativeHaptics");
  web.addJavascriptInterface(h,"AndroidHaptics");

  web.setWebChromeClient(new WebChromeClient(){
   @Override public void onPermissionRequest(final PermissionRequest request){
    runOnUiThread(()->{
     boolean cam=false;
     for(String r:request.getResources())if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r))cam=true;
     if(cam && Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
      pendingWebRequest=request;requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
     }else request.grant(request.getResources());
    });
   }
   @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){
    if(Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){cb.invoke(origin,true,false);return;}
    pendingGeoOrigin=origin;pendingGeoCallback=cb;
    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
   }
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams params){
    if(pendingFileCallback!=null)pendingFileCallback.onReceiveValue(null);
    pendingFileCallback=cb;pendingCameraUri=null;
    try{
     Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");
     Intent cam=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
     if(cam.resolveActivity(getPackageManager())!=null){
      ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"jinju_qr_"+System.currentTimeMillis()+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
      if(Build.VERSION.SDK_INT>=29)v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/JinjuMarketGame");
      pendingCameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
      if(pendingCameraUri!=null){cam.putExtra(MediaStore.EXTRA_OUTPUT,pendingCameraUri);cam.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
     }
     if(params!=null && params.isCaptureEnabled() && pendingCameraUri!=null){startActivityForResult(cam,REQ_FILE);return true;}
     Intent chooser=Intent.createChooser(pick,"QR 사진 선택");
     if(pendingCameraUri!=null)chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,new Intent[]{cam});
     startActivityForResult(chooser,REQ_FILE);return true;
    }catch(Throwable e){if(pendingFileCallback!=null)pendingFileCallback.onReceiveValue(null);pendingFileCallback=null;pendingCameraUri=null;return false;}
   }
  });

  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return local(r.getUrl());}
   @SuppressWarnings("deprecation") @Override public WebResourceResponse shouldInterceptRequest(WebView v,String u){try{return local(Uri.parse(u));}catch(Throwable e){return null;}}
   @Override public void onPageFinished(WebView v,String u){if(pending!=null){sendUnlockCode(pending);pending=null;}}
  });
  read(getIntent());web.loadUrl("https://jinju.local/index.html");
 }

 WebResourceResponse local(Uri u){
  try{if(u!=null && "jinju.local".equals(u.getHost()) && ("/".equals(u.getPath())||"/index.html".equals(u.getPath()))){InputStream in=getAssets().open("index.html");return new WebResourceResponse("text/html","UTF-8",in);}}catch(Throwable ignored){}
  return null;
 }

 @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){
  super.onRequestPermissionsResult(req,p,g);
  if(req==REQ_CAMERA){boolean ok=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;if(pendingWebRequest!=null){if(ok)pendingWebRequest.grant(pendingWebRequest.getResources());else pendingWebRequest.deny();pendingWebRequest=null;}}
  else if(req==REQ_LOCATION){boolean ok=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(pendingGeoCallback!=null){pendingGeoCallback.invoke(pendingGeoOrigin,ok,false);pendingGeoCallback=null;pendingGeoOrigin=null;}}
 }

 @Override protected void onActivityResult(int req,int result,Intent data){
  if(req==REQ_FILE){
   Uri[] out=null;
   if(result==RESULT_OK){Uri u=data!=null?data.getData():null;if(u==null)u=pendingCameraUri;if(u!=null)out=new Uri[]{u};}
   if(pendingFileCallback!=null){pendingFileCallback.onReceiveValue(out);pendingFileCallback=null;}pendingCameraUri=null;return;
  }
  super.onActivityResult(req,result,data);
 }
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);read(i);if(pending!=null){sendUnlockCode(pending);pending=null;}}
 void read(Intent i){if(i==null)return;Uri u=i.getData();if(u!=null&&"jinjumarket".equals(u.getScheme())&&"unlock".equals(u.getHost()))pending=u.getQueryParameter("game");}
 void sendUnlockCode(String code){final String raw=code==null?"":code;web.evaluateJavascript("window.unlockFromNative&&window.unlockFromNative("+JSONObject.quote(raw)+")",null);}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}

 class Haptics {
  Vibrator getVibratorCompat(){
   try{if(Build.VERSION.SDK_INT>=31){VibratorManager vm=(VibratorManager)getSystemService(Context.VIBRATOR_MANAGER_SERVICE);return vm==null?null:vm.getDefaultVibrator();}return (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);}catch(Throwable e){return null;}
  }
  int oneShot(long ms){try{Vibrator v=getVibratorCompat();if(v==null||!v.hasVibrator())return 0;ms=Math.max(24,Math.min(ms,1200));if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(ms,255));else v.vibrate(ms);return 1;}catch(Throwable e){return 0;}}
  int wave(long[] t,int[] a){try{Vibrator v=getVibratorCompat();if(v==null||!v.hasVibrator())return 0;if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(t,a,-1));else v.vibrate(t,-1);return 1;}catch(Throwable e){return 0;}}
  @JavascriptInterface public int available(){try{Vibrator v=getVibratorCompat();return v!=null&&v.hasVibrator()?1:0;}catch(Throwable e){return 0;}}
  @JavascriptInterface public int vibrate(int ms){return oneShot(ms);}
  @JavascriptInterface public int pattern(String json){try{org.json.JSONArray x=new org.json.JSONArray(json);int n=Math.min(x.length(),24);if(n<=0)return 0;long[] t=new long[n+1];int[] a=new int[n+1];t[0]=0;a[0]=0;for(int i=0;i<n;i++){long raw=Math.max(0,Math.min(x.optLong(i,0),1200));boolean on=i%2==0;t[i+1]=on?Math.max(24,raw):Math.max(8,raw);a[i+1]=on?255:0;}return wave(t,a);}catch(Throwable e){return 0;}}
  @JavascriptInterface public int bomb(){return wave(new long[]{0,180,45,260,50,360},new int[]{0,255,0,255,0,255});}
 }
}
