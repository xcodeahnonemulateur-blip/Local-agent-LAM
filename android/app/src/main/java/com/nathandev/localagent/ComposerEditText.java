package com.nathandev.localagent;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

/** Multiline composer with an explicit send action on both Android IMEs and hardware keyboards. */
final class ComposerEditText extends EditText {
    private Runnable sendAction;

    ComposerEditText(Context context) {
        super(context);
        setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_FULLSCREEN);
        setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                if (sendAction != null) sendAction.run();
                return true;
            }
            return false;
        });
    }

    void onSend(Runnable action) { sendAction = action; }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        // TextView adds NO_ENTER_ACTION to multiline fields. Remove it so Gboard shows Send.
        outAttrs.imeOptions = (outAttrs.imeOptions &
                ~(EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION)) |
                EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return connection;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed()) {
            if (event.getRepeatCount() == 0 && sendAction != null) sendAction.run();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed()) return true;
        return super.onKeyUp(keyCode, event);
    }
}
