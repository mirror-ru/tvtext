package local.tvtext.uitests;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import com.tvtext.app.BridgeRuntime;
import com.tvtext.app.Protocol;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** A different package owns the focused field and clipboard throughout insertion. */
final class ExternalFieldChecks {
    private static final String PACKAGE="local.tvtext.testfield";
    private final Instrumentation test;
    private final UiAutomation automation;
    private final List<String> clipboardFailures=new ArrayList<>();
    ExternalFieldChecks(Instrumentation test){this.test=test;automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private String shell(String command){
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int count;while((count=input.read(bytes))>=0)out.write(bytes,0,count);return out.toString("UTF-8").trim();
        }catch(Exception error){throw new RuntimeException(error);}
    }
    @SuppressWarnings("deprecation") private String value(AccessibilityNodeInfo node,String description){
        if(description.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node.getText()==null?"":node.getText().toString();
        for(int i=0;i<node.getChildCount();i++){
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{String result=value(child,description);if(result!=null)return result;}finally{child.recycle();}
        }
        return null;
    }
    @SuppressWarnings("deprecation") private String value(String description){
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();if(root==null)return null;
        try{return PACKAGE.contentEquals(root.getPackageName()==null?"":root.getPackageName())?value(root,description):null;}finally{root.recycle();}
    }
    private void await(String description,String expected){
        long until=SystemClock.uptimeMillis()+15000;String actual;
        while(!expected.equals(actual=value(description))){
            check(SystemClock.uptimeMillis()<until,"external "+description+": expected "+expected+", got "+actual);
            SystemClock.sleep(50);test.waitForIdleSync();
        }
    }
    private void insert(BridgeRuntime runtime,String token,String text,String expected,String originalClipboard){
        long until=SystemClock.uptimeMillis()+15000;
        while(true){
            Protocol.Response status=runtime.getEngine().handle(new Protocol.Request(Protocol.STATUS,token,"",""),()->Boolean.TRUE);
            check(status.getCode()==Protocol.OK,"external fixture status rejected: "+status.getCode()+" "+status.getMessage());
            if(PACKAGE.equals(status.getTarget()))break;
            check(SystemClock.uptimeMillis()<until,"receiver did not observe the external field");SystemClock.sleep(50);test.waitForIdleSync();
        }
        Protocol.Response response=runtime.getEngine().handle(new Protocol.Request(Protocol.INSERT,token,UUID.randomUUID().toString(),text),()->Boolean.TRUE);
        check(response.getCode()==Protocol.OK&&PACKAGE.equals(response.getTarget()),"external insert: "+response.getCode()+" "+response.getMessage()+" target="+response.getTarget());
        await("TVText test field",expected);
        // Observe after the receiver's 750 ms restoration delay and the probe's
        // next frame; the pre-insertion label is not evidence of restoration.
        SystemClock.sleep(1000);
        long restoreUntil=SystemClock.uptimeMillis()+2500;
        while(!originalClipboard.equals(value("TVText fixture clipboard"))&&SystemClock.uptimeMillis()<restoreUntil)SystemClock.sleep(50);
        if(!originalClipboard.equals(value("TVText fixture clipboard")))clipboardFailures.add("expected "+originalClipboard+", got "+value("TVText fixture clipboard"));
    }
    private void start(String scenario){String output=shell("am start -W -n "+PACKAGE+"/.FieldActivity -f 0x30000000 --es scenario "+scenario);check(output.contains("Status: ok"),"fixture activity start failed: "+output);}
    void run(BridgeRuntime runtime,String token){
        String ime=shell("settings get secure default_input_method");
        try{
            start("cursor");
            await("TVText test field","left right");
            await("TVText fixture clipboard","original clipboard");
            insert(runtime,token,"Привет 🌍\n","left Привет 🌍\nright","original clipboard");
            start("selection");
            await("TVText test field","left old right");
            await("TVText fixture clipboard","original clipboard");
            insert(runtime,token,"new","left new right","original clipboard");
            start("empty");
            await("TVText test field","safe");
            await("TVText fixture clipboard","<empty>");
            insert(runtime,token,"+","safe+","<empty>");
            check(ime.equals(shell("settings get secure default_input_method")),"external insertion preserves selected keyboard");
            check(clipboardFailures.isEmpty(),"external insertion and IME checks passed; clipboard restoration failed in "+clipboardFailures.size()+" scenarios: "+clipboardFailures);
        }finally{shell("am force-stop "+PACKAGE);shell("am start -W -n com.tvtext.app/.MainActivity -f 0x10020000");test.waitForIdleSync();}
    }
}
