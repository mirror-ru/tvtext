package local.tvtext.uitests;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.tvtext.app.BridgeRuntime;
import com.tvtext.app.MainActivity;
import com.tvtext.app.ReceiverDialogs;
import com.tvtext.app.LegacyInputBridge;
import com.tvtext.app.Protocol;
import com.tvtext.app.TvTheme;

// Avoid a second Kotlin runtime: the receiver's runtime is already R8-optimized.
public final class ThemeDeviceTests extends Instrumentation {
    private MainActivity activity;
    private final TvTheme theme=TvTheme.INSTANCE;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private Object field(String name){if(name.equals("dialog"))return ((ReceiverDialogs)field("dialogs")).getCurrent();try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void update(){ui(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("update");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});}
    private UiAutomation automation(){return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
    private String shell(String command){
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(automation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int count;while((count=input.read(buffer))>=0)out.write(buffer,0,count);return out.toString("UTF-8").trim();
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void restoreSetting(String name,String value){
        if(value.isEmpty()||value.equals("null"))shell("settings delete secure "+name);
        else{check(value.matches("[A-Za-z0-9_./:$-]+"),"invalid saved test setting");shell("settings put secure "+name+" "+value);}
    }
    private interface Condition{boolean matches();}
    private void waitUntil(Condition condition){long until=System.currentTimeMillis()+20000;while(!condition.matches()){check(System.currentTimeMillis()<until,"TV UI timeout");pause(50);waitForIdleSync();}}
    private void pause(long millis){try{Thread.sleep(millis);}catch(InterruptedException e){throw new RuntimeException(e);}}
    private List<View> descendants(View root){List<View> list=new ArrayList<>();list.add(root);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)list.addAll(descendants(group.getChildAt(i)));}return list;}
    private void capture(String name){waitForIdleSync();pause(250);Bitmap image=automation().takeScreenshot();check(image!=null,"no screenshot");try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,output);}catch(Exception e){throw new RuntimeException(e);}finally{image.recycle();}}
    private void checkTheme(){
        View page=activity.getWindow().getDecorView().findViewWithTag("tv-page"),card=activity.getWindow().getDecorView().findViewWithTag("tv-card");
        check(((ColorDrawable)page.getBackground()).getColor()==theme.getCanvas(),"canvas follows theme");
        check(((GradientDrawable)card.getBackground()).getColor().getDefaultColor()==theme.getSurface(),"card follows theme");
        int statusColor=((TextView)field("status")).getCurrentTextColor();
        check(statusColor==theme.getInk()||statusColor==theme.getMuted(),"status text follows theme");
        check(((View)field("themeButton")).getContentDescription().length()>0,"theme switch is accessible");
        check(contrast(Color.WHITE,theme.getPrimary())>=4.5&&contrast(Color.WHITE,theme.getPrimaryFocus())>=4.5,"primary button contrast");
        check(contrast(theme.getAccent(),theme.getButtonFocus())>=4.5&&contrast(theme.getAccent(),theme.getButtonPressed())>=4.5,"focused button contrast");
        check(contrast(theme.getInk(),theme.getSurface())>=7,"body contrast");
        View info=(View)field("statusRow");TextView detail=(TextView)field("detail");
        check(info.getVisibility()==View.VISIBLE&&descendants(info).contains(detail),"all states use one information card");
        check(!detail.getText().toString().contains("\n"),"instructions wrap naturally");
        check(((View)field("keyboardButton")).getLeft()<((View)field("languageButton")).getLeft(),"keyboard test is left of language");
    }
    private void toggleTheme(boolean dark){
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);ui(()->((View)field("themeButton")).performClick());
        activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(activity!=null,"theme recreation");waitForIdleSync();
        check(theme.getDark()==dark,"selected theme");checkTheme();check(((View)field("themeButton")).hasFocus(),"theme button retains D-pad focus");
        check(getTargetContext().getSharedPreferences("ui",0).getBoolean("dark-mode",true)==dark,"saved preference");
    }
    private AlertDialog openConnections(){ui(()->((Button)field("computersButton")).performClick());pause(150);waitForIdleSync();return (AlertDialog)field("dialog");}
    private void closeConnections(){AlertDialog current=(AlertDialog)field("dialog");ui(()->current.getWindow().getDecorView().findViewWithTag("connections-close").performClick());}
    private String clipboardText(ClipboardManager clipboard){ClipData clip=clipboard.getPrimaryClip();return clip==null?null:clip.getItemAt(0).coerceToText(getTargetContext()).toString();}
    private void legacyInsertionChecks(BridgeRuntime runtime,EditText editor){
        final int[] mode={0},setTextActions={0};
        ui(()->editor.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                if(mode[0]==1)info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE);
            }
            @Override public boolean performAccessibilityAction(View host,int action,Bundle arguments){
                if(action==AccessibilityNodeInfo.ACTION_SET_TEXT)setTextActions[0]++;
                if(mode[0]==2&&action==AccessibilityNodeInfo.ACTION_PASTE)return false;
                return super.performAccessibilityAction(host,action,arguments);
            }
        }));
        try{
            ui(()->{editor.setText("left right");editor.setSelection(5);});pause(200);
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"middle ",()->Boolean.TRUE).getCode()==Protocol.OK,"legacy insertion at cursor");
            waitUntil(()->editor.getText().toString().equals("left middle right"));
            ui(()->editor.setSelection(5,12));pause(200);
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"new ",()->Boolean.TRUE).getCode()==Protocol.OK,"legacy insertion replaces selection only");
            waitUntil(()->editor.getText().toString().equals("left new right"));
            for(int testMode=1;testMode<=2;testMode++){
                final int next=testMode;
                ui(()->{mode[0]=next;editor.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);});pause(300);
                if(testMode==1)check(!LegacyInputBridge.INSTANCE.status(runtime.getService()).getReady(),"set-text-only field is not ready for insertion");
                check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"must not overwrite",()->Boolean.TRUE).getCode()==Protocol.NO_FIELD,"unsupported or rejected paste reports failure");
                pause(100);check(editor.getText().toString().equals("left new right")&&setTextActions[0]==0,"failure never replaces existing field contents");
            }
        }finally{ui(()->{editor.setAccessibilityDelegate(null);editor.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);});pause(300);}
    }
    private void legacyClipboardChecks(BridgeRuntime runtime){
        pause(850);waitForIdleSync();
        ClipboardManager clipboard=getTargetContext().getSystemService(ClipboardManager.class);
        ClipData previous=clipboard.getPrimaryClip();
        try{
            ui(()->clipboard.setPrimaryClip(ClipData.newPlainText("Fixture","original clipboard")));
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"first insert",()->Boolean.TRUE).getCode()==Protocol.OK,"first rapid insert");
            pause(100);
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"second insert",()->Boolean.TRUE).getCode()==Protocol.OK,"second rapid insert");
            waitUntil(()->"original clipboard".equals(clipboardText(clipboard)));
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"third insert",()->Boolean.TRUE).getCode()==Protocol.OK,"insert before user copy");
            ui(()->clipboard.setPrimaryClip(ClipData.newPlainText("User","new user clipboard")));
            pause(900);waitForIdleSync();check("new user clipboard".equals(clipboardText(clipboard)),"restore preserves an intervening user copy");
            ui(clipboard::clearPrimaryClip);
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"empty clipboard insert",()->Boolean.TRUE).getCode()==Protocol.OK,"insert with empty original clipboard");
            waitUntil(()->!clipboard.hasPrimaryClip());
        }finally{ui(()->{if(previous==null)clipboard.clearPrimaryClip();else clipboard.setPrimaryClip(previous);});}
    }
    @Override public void onStart(){
        ClipboardManager clipboard=getTargetContext().getSystemService(ClipboardManager.class);
        final ClipData[] originalClipboard={null};final boolean[] clipboardCaptured={false};
        String oldServices=shell("settings get secure enabled_accessibility_services"),oldEnabled=shell("settings get secure accessibility_enabled");
        String oldIme=shell("settings get secure default_input_method");
        String oldHardwareKeyboard=shell("settings get secure show_ime_with_hard_keyboard");
        SharedPreferences prefs=getTargetContext().getSharedPreferences("ui",0);String oldLanguage=prefs.getString("language",null);Boolean oldDark=prefs.contains("dark-mode")?prefs.getBoolean("dark-mode",true):null;
        String token="";List<String> fixtureTokens=new ArrayList<>();Bundle result=new Bundle();int resultCode=Activity.RESULT_CANCELED;
        try{
            shell("ime enable local.tvtext.uitests/.TestKeyboard");shell("ime set local.tvtext.uitests/.TestKeyboard");
            shell("settings put secure show_ime_with_hard_keyboard 1");
            shell("settings delete secure enabled_accessibility_services");shell("settings put secure accessibility_enabled 0");
            prefs.edit().putString("language","ru").remove("dark-mode").remove("theme-focus").remove("language-focus").commit();
            setInTouchMode(false);
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            check(theme.getDark(),"dark mode is the default");checkTheme();
            pause(250);waitForIdleSync();
            Button access=(Button)field("computersButton");check(access.hasFocus()&&access.isEnabled(),"accessibility button initial focus: focus="+activity.getWindow().getDecorView().findFocus()+", enabled="+access.isEnabled()+", text="+access.getText());
            capture("TVText-TV-dark-accessibility-off.png");toggleTheme(false);capture("TVText-TV-light-accessibility-off.png");toggleTheme(true);
            Button language=(Button)field("languageButton");ui(()->((Button)field("computersButton")).requestFocus());
            int before=((GradientDrawable)language.getBackground().getCurrent()).getColor().getDefaultColor();ui(language::requestFocus);
            int focused=((GradientDrawable)language.getBackground().getCurrent()).getColor().getDefaultColor();check(before!=focused,"visible focus highlight");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();check(activity.getWindow().getDecorView().findViewWithTag("theme").hasFocus(),"D-pad reaches theme switch from RU");
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);ui(language::performClick);
            activity=(MainActivity)waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(activity!=null,"language recreation");waitForIdleSync();checkTheme();
            check(((Button)field("languageButton")).getText().toString().equals("EN")&&((Button)field("languageButton")).hasFocus(),"language and focus restored");
            BridgeRuntime runtime=BridgeRuntime.Companion.get(getTargetContext());
            shell("settings put secure enabled_accessibility_services com.tvtext.app/.TextService");shell("settings put secure accessibility_enabled 1");
            waitUntil(()->runtime.getService()!=null&&runtime.listening());update();checkTheme();
            ui(()->((Button)field("pairButton")).performClick());
            String pinBeforeTheme=runtime.getEngine().pairingPin(),fingerprint=runtime.getFingerprint();check(pinBeforeTheme.length()==8,"pairing code opens");
            check(((TextView)field("pairCode")).getCurrentTextColor()==theme.getInk(),"pair code color");
            toggleTheme(false);check(runtime.getEngine().pairingPin().equals(pinBeforeTheme)&&runtime.getFingerprint().equals(fingerprint)&&runtime.listening(),"switch preserves code, identity and receiver");capture("TVText-TV-light-pairing.png");
            toggleTheme(true);check(runtime.getEngine().pairingPin().equals(pinBeforeTheme),"code survives second switch");capture("TVText-TV-dark-pairing.png");
            ui(()->((Button)field("pairButton")).performClick());
            capture("TVText-TV-en-ready.png");
            ActivityMonitor russianMonitor=addMonitor(MainActivity.class.getName(),null,false);ui(()->((Button)field("languageButton")).performClick());
            activity=(MainActivity)waitForMonitorWithTimeout(russianMonitor,10000);removeMonitor(russianMonitor);check(activity!=null,"Russian recreation");waitForIdleSync();checkTheme();
            check(((Button)field("languageButton")).getText().toString().equals("RU"),"Russian connection labels");
            ui(()->((Button)field("pairButton")).performClick());
            check(((View)field("keyboardButton")).getVisibility()==View.GONE,"keyboard test hidden while pairing");
            ui(()->((Button)field("pairButton")).performClick());
            check(((View)field("keyboardButton")).getVisibility()==View.VISIBLE,"keyboard test visible on ready screen");
            if(runtime.getEngine().pairedTokens().isEmpty()){
                AlertDialog empty=openConnections();check(empty.getWindow().getDecorView().findViewWithTag("connections-empty")!=null,"empty list has a centered state");capture("TVText-TV-dark-connections-empty.png");closeConnections();
            }
            String pin=runtime.getEngine().openPairing();token=runtime.getEngine().handle(new Protocol.Request(Protocol.PAIR,pin,"",""),()->Boolean.TRUE).getToken();fixtureTokens.add(token);check(!token.isEmpty(),"fixture pairing");update();
            AlertDialog list=openConnections();
            check(((GradientDrawable)list.getWindow().getDecorView().getBackground()).getColor().getDefaultColor()==theme.getSurface(),"connections dialog theme");capture("TVText-TV-dark-connections.png");
            check(!((ScrollView)list.getWindow().getDecorView().findViewWithTag("connections-list")).canScrollVertically(1),"single connection does not need scrolling");
            ImageButton remove=null;for(View view:descendants(list.getWindow().getDecorView()))if(view instanceof ImageButton){remove=(ImageButton)view;break;}check(remove!=null,"delete icon");ImageButton selectedRemove=remove;ui(selectedRemove::performClick);
            AlertDialog confirm=(AlertDialog)field("dialog");View cancel=confirm.getWindow().getDecorView().findViewWithTag("revoke-cancel");
            check(confirm.getWindow().getDecorView().findViewWithTag("revoke-panel")!=null&&confirm.getWindow().getDecorView().findViewWithTag("revoke-confirm").hasFocus(),"styled confirmation focuses Delete");
            capture("TVText-TV-dark-revoke.png");ui(cancel::performClick);
            check(runtime.getEngine().pairedTokens().contains(token),"Cancel keeps fixture pairing");
            closeConnections();
            ui(()->((View)field("keyboardButton")).performClick());
            AlertDialog keyboard=(AlertDialog)field("dialog");EditText editor=keyboard.getWindow().getDecorView().findViewWithTag("keyboard-test-input");
            check(editor!=null&&editor.hasFocus(),"keyboard test opens with editor focus");
            // EditText advertises Paste only while the clipboard contains text.
            // Readiness checks inspect that action; insertion also separately
            // tests an initially empty clipboard and must prepare it itself.
            ui(()->{originalClipboard[0]=clipboard.getPrimaryClip();clipboardCaptured[0]=true;clipboard.setPrimaryClip(ClipData.newPlainText("Fixture","paste readiness"));});
            final String activeToken=token;
            waitUntil(()->runtime.getEngine().handle(new Protocol.Request(Protocol.STATUS,activeToken,"",""),()->Boolean.TRUE).getReady());
            String testText="Проверка TVText — test 123";
            Protocol.Response inserted=runtime.getEngine().handle(new Protocol.Request(Protocol.INSERT,token,java.util.UUID.randomUUID().toString(),testText),()->Boolean.TRUE);
            check(inserted.getCode()==Protocol.OK,"remote insert into keyboard test: "+inserted.getCode()+" "+inserted.getMessage());
            waitUntil(()->editor.getText().toString().equals(testText));
            capture("TVText-TV-dark-keyboard-test.png");
            check(keyboard.getWindow().getDecorView().findViewWithTag("keyboard-test-clear")==null,"keyboard test has no Clear action");
            waitUntil(()->{final boolean[] ready={false};ui(()->ready[0]=LegacyInputBridge.INSTANCE.status(runtime.getService()).getReady());return ready[0];});
            ui(editor::selectAll);pause(200);
            check(LegacyInputBridge.INSTANCE.insert(runtime.getService(),"Legacy test",()->Boolean.TRUE).getCode()==Protocol.OK,"legacy bridge accepts dedicated test field");
            waitUntil(()->editor.getText().toString().equals("Legacy test"));
            legacyInsertionChecks(runtime,editor);
            legacyClipboardChecks(runtime);
            ui(()->keyboard.getWindow().getDecorView().findViewWithTag("keyboard-test-close").performClick());
            check(((View)field("keyboardButton")).hasFocus(),"closing test restores footer focus");
            check(!runtime.getEngine().handle(new Protocol.Request(Protocol.STATUS,token,"",""),()->Boolean.TRUE).getReady(),"closed test field is unavailable");
            ui(()->((View)field("keyboardButton")).performClick());
            AlertDialog reopened=(AlertDialog)field("dialog");
            check(((EditText)reopened.getWindow().getDecorView().findViewWithTag("keyboard-test-input")).getText().length()==0,"test text is not retained");
            ActivityMonitor recreation=addMonitor(MainActivity.class.getName(),null,false);ui(activity::recreate);
            activity=(MainActivity)waitForMonitorWithTimeout(recreation,10000);removeMonitor(recreation);check(activity!=null,"recreation while keyboard dialog is open");waitForIdleSync();
            check(!reopened.isShowing()&&field("dialog")==null&&!MainActivity.Companion.getKeyboardTestOpen(),"destroy dismisses the dialog and closes the permitted test target");
            ui(()->((View)field("keyboardButton")).performClick());
            AlertDialog afterRecreation=(AlertDialog)field("dialog");
            check(((EditText)afterRecreation.getWindow().getDecorView().findViewWithTag("keyboard-test-input")).getText().length()==0,"recreated dialog never retains test input");
            ui(()->afterRecreation.getWindow().getDecorView().findViewWithTag("keyboard-test-close").performClick());
            toggleTheme(false);openConnections();capture("TVText-TV-light-connections.png");closeConnections();toggleTheme(true);
            ui(()->((Button)field("pairButton")).performClick());
            String connectionHint=((TextView)field("detail")).getText().toString();
            ui(()->((Button)field("pairButton")).performClick());
            for(int i=0;i<8;i++){String extraPin=runtime.getEngine().openPairing();fixtureTokens.add(runtime.getEngine().handle(new Protocol.Request(Protocol.PAIR,extraPin,"",""),()->Boolean.TRUE).getToken());}update();
            ui(()->((Button)field("pairButton")).performClick());
            check(((TextView)field("detail")).getText().toString().equals(connectionHint),"pairing instructions do not depend on connection count");
            ui(()->((Button)field("pairButton")).performClick());
            AlertDialog many=openConnections();ScrollView scroll=many.getWindow().getDecorView().findViewWithTag("connections-list");
            check(scroll.canScrollVertically(1),"long connection list scrolls instead of growing the dialog");
            List<ImageButton> deletes=new ArrayList<>();for(View view:descendants(many.getWindow().getDecorView()))if(view instanceof ImageButton)deletes.add((ImageButton)view);
            check(deletes.size()==runtime.getEngine().pairedTokens().size(),"every connection has one delete action");
            ImageButton last=deletes.get(deletes.size()-1);ui(last::requestFocus);check(scroll.canScrollVertically(-1),"D-pad focus reveals the last connection");
            capture("TVText-TV-dark-connections-many.png");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();check(many.getWindow().getDecorView().findViewWithTag("connections-close").hasFocus(),"Close is reachable below the final delete action");closeConnections();
            int connectionCount=runtime.getEngine().pairedTokens().size();
            AlertDialog beforeDelete=openConnections();
            View deleteAction=beforeDelete.getWindow().getDecorView().findViewWithTag("connection-delete");ui(deleteAction::performClick);
            AlertDialog deleteConfirm=(AlertDialog)field("dialog");
            ui(()->deleteConfirm.getWindow().getDecorView().findViewWithTag("revoke-confirm").performClick());
            check(runtime.getEngine().pairedTokens().size()==connectionCount-1,"confirm deletes exactly one connection");
            check(((AlertDialog)field("dialog")).getWindow().getDecorView().findViewWithTag("connections-panel")!=null,"deletion returns to updated device list");closeConnections();
            checkTheme();capture("TVText-TV-dark-ready.png");
            result.putString("stream","PASS: Android TV themes: API "+android.os.Build.VERSION.SDK_INT+", unified cards, dark/light RU/EN, D-pad focus, lists and revoke, keyboard insert, legacy cursor/selection/paste rejection/clipboard restoration, close, no retained text, contrast\n");resultCode=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error)+"\n");}
        finally{
            if(clipboardCaptured[0])ui(()->{if(originalClipboard[0]==null)clipboard.clearPrimaryClip();else clipboard.setPrimaryClip(originalClipboard[0]);});
            for(String fixture:fixtureTokens)if(!fixture.isEmpty())try{BridgeRuntime.Companion.get(getTargetContext()).getEngine().revokeToken(fixture);}catch(Exception ignored){}
            restoreSetting("enabled_accessibility_services",oldServices);restoreSetting("accessibility_enabled",oldEnabled);
            if(!oldIme.isEmpty()&&!oldIme.equals("null"))shell("ime set "+oldIme);else shell("ime reset");
            shell("ime disable local.tvtext.uitests/.TestKeyboard");
            restoreSetting("show_ime_with_hard_keyboard",oldHardwareKeyboard);
            SharedPreferences.Editor edit=prefs.edit();if(oldLanguage==null)edit.remove("language");else edit.putString("language",oldLanguage);if(oldDark==null)edit.remove("dark-mode");else edit.putBoolean("dark-mode",oldDark);edit.remove("language-focus").remove("theme-focus").commit();
        }
        finish(resultCode,result);
    }
    private static double linear(int value){double c=value/255.0;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
    private static double luminance(int color){return .2126*linear(Color.red(color))+.7152*linear(Color.green(color))+.0722*linear(Color.blue(color));}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
