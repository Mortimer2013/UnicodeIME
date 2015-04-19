package com.android.unicodeime;

import android.content.Context;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.util.AttributeSet;

public class MyKeyboardView extends KeyboardView {

	public MyKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public MyKeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	final Handler handler = new Handler();
	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			handler.postDelayed(this, 100);
		}
	};
	
	@Override
	protected boolean onLongPress(Key key) {
//		if (key.codes[0] == Keyboard.KEYCODE_DELETE) {
//			getOnKeyboardActionListener().onKey(Keyboard.KEYCODE_DELETE, null);
//			return true;
//		} else {
			return super.onLongPress(key);
//		}
	}
}
