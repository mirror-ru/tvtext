package local.tvtext.uitests;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Minimal IME fixture: the ATD emulator has no preinstalled keyboard. */
public final class TestKeyboard extends InputMethodService {
    @Override public View onCreateInputView() {
        TextView view=new TextView(this);
        view.setText("TVText test keyboard");view.setTextSize(18);view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);view.setBackgroundColor(0xff30394c);
        view.setMinHeight((int)(100*getResources().getDisplayMetrics().density));
        return view;
    }
}
