package local.tvtext.testfield;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FieldActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(48,24,48,24);root.setBackgroundColor(0xff121824);
        TextView label=new TextView(this);label.setText("TVText — проверка ввода");label.setTextSize(24);label.setTextColor(0xff70dcb4);root.addView(label);
        final EditText field=new EditText(this);field.setTextColor(0xffffffff);field.setTextSize(28);field.setSingleLine(false);field.setMinLines(2);field.setContentDescription("TVText test field");root.addView(field,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);field.requestFocus();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        field.postDelayed(new Runnable(){public void run(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);}},600);
    }
}
