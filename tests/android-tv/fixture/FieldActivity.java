package local.tvtext.testfield;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FieldActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView clipboardProbe;
    private EditText field;
    private final Runnable probe=new Runnable(){public void run(){
        if(!hasWindowFocus())return;
        ClipData clip=getSystemService(ClipboardManager.class).getPrimaryClip();
        clipboardProbe.setText(clip==null?"<empty>":clip.getItemAt(0).coerceToText(FieldActivity.this));
        handler.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(48,24,48,24);root.setBackgroundColor(0xff121824);
        TextView label=new TextView(this);label.setText("TVText — проверка ввода");label.setTextSize(24);label.setTextColor(0xff70dcb4);root.addView(label);
        field=new EditText(this);field.setTextColor(0xffffffff);field.setTextSize(28);field.setSingleLine(false);field.setMinLines(2);field.setContentDescription("TVText test field");root.addView(field,new LinearLayout.LayoutParams(-1,-2));
        configure();
        clipboardProbe=new TextView(this);clipboardProbe.setTextColor(0xffffffff);clipboardProbe.setContentDescription("TVText fixture clipboard");root.addView(clipboardProbe);
        setContentView(root);field.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        field.postDelayed(new Runnable(){public void run(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);}},600);
    }
    private void configure(){
        String scenario=getIntent().getStringExtra("scenario");
        if(scenario!=null){
            String initial=scenario.equals("selection")?"left old right":scenario.equals("empty")?"safe":"left right";
            field.setText(initial);field.setSelection(scenario.equals("empty")?4:5,scenario.equals("selection")?8:scenario.equals("empty")?4:5);
        }
        ClipboardManager clipboard=getSystemService(ClipboardManager.class);
        if("empty".equals(scenario))clipboard.clearPrimaryClip();
        else if(scenario!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Fixture","original clipboard"));
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);configure();}
    @Override public void onResume(){super.onResume();handler.removeCallbacks(probe);if(hasWindowFocus())handler.post(probe);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);handler.removeCallbacks(probe);if(focused)handler.post(probe);}
    @Override public void onPause(){handler.removeCallbacks(probe);super.onPause();}
}
