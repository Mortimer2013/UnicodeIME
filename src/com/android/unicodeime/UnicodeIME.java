package com.android.unicodeime;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class UnicodeIME extends InputMethodService 
		implements KeyboardView.OnKeyboardActionListener { 
	static final boolean DEBUG = false;
	
    private String IME_MESSAGE = "ADB_INPUT_TEXT";
    private String IME_KEYCODE = "ADB_INPUT_CODE";
    private String IME_EDITORCODE = "ADB_EDITOR_CODE";
    private static final int KEYCODE_CLEAR = 4896;
    private static final int KEYCODE_OK = 4897;
    private static final int KEYCODE_UNI = 4895;
    private static final int SLEEP_TIME_MS = 100;
    // the time (ms) from onPress to onRelease defines long press
    private static final int LONG_PRESS_TIME_MS = 300;
    private long mPressTime = 0;
    private boolean mLongPressDel = false;
    private static final String UNI_PREFIX = "\\u";
    
    private BroadcastReceiver mReceiver = null;
    
    private MyKeyboardView mKeyboardView;
    private Keyboard mKeyboard;
    
    private CandidateView mCandidateView;
    private StringBuilder mComposing = new StringBuilder();
    private CompletionInfo [] mCompletions;
    
    private Handler mHandler;
    private Timer mTimer = null;
    private TimerTask mTask = null;
    private int mDelCount = 0;
    
    private void handleBackspace() {
    	final int length = mComposing.length();
    	if (length > 1) {
    		mComposing.delete(length - 1, length);
    		updateCandidates();
    	} else if (length > 0) {
    		mComposing.setLength(0);
    		updateCandidates();
    		setCandidatesViewShown(false);
    	} else {
    		keyDownUp(KeyEvent.KEYCODE_DEL);
    		if (mCandidateView != null) {
    			mCandidateView.clear();
    			setCandidatesViewShown(false);
    		}
    	}
    }
    
    /**
     * Update the list of available candidates from the current composing text.
     */
    private void updateCandidates() {
    	if (mComposing.length() > 0) {
    		ArrayList<String> list = new ArrayList<String>();
    		list.add(mComposing.toString());
    		String convertedChar = convertUnicodeArrayToChar(mComposing.toString());
    		if (convertedChar != null && !"".equals(convertedChar.trim()) && !convertedChar.equals(mComposing.toString())) {
    			list.add(convertedChar);
    		}
    		setSuggestions(list, true, true);
    	} else {
    		setSuggestions(null, false, false);
    	}
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions, 
    		boolean typedWordValid) {
    	if (suggestions != null && suggestions.size() >0) {
    		setCandidatesViewShown(true);
    	} else if (isExtractViewShown()) {
    		setCandidatesViewShown(true);
    	}
    	if (mCandidateView != null) {
    		mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
    	}
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
    	InputConnection ic = getCurrentInputConnection();
    	if (ic != null) {
    		ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
    		ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    	}
    }
    
    private void handleCharacter(int primaryCode, int [] keyCodes) {
    	if (primaryCode != KEYCODE_OK) {
    		mComposing.append((char)primaryCode);
    		updateCandidates();
    	} else {
    		getCurrentInputConnection().commitText(
    			String.valueOf((char)primaryCode), 1);
    		mCandidateView.clear();
    		setCandidatesViewShown(false);
    	}
    }
    
    public void pickDefaultCandidate() {
    	commitTyped(getCurrentInputConnection());
    }
    
    public void pickSuggestionManually(String suggestion) {
    	getCurrentInputConnection().commitText(suggestion, suggestion.length());
    	if (mCandidateView != null) {
    		mCandidateView.clear();
    		setCandidatesViewShown(false);
    	}
    }
    
    public void pickSuggestionManually(int index) {
    	if (mCompletions != null && index >= 0 && index < mCompletions.length) {
    		CompletionInfo ci = mCompletions[index];
    		getCurrentInputConnection().commitCompletion(ci);
    		if (mCandidateView != null) {
    			mCandidateView.clear();
    			setCandidatesViewShown(false);
    		}
    	} else if (mComposing.length() > 0) {
    		// If we were generating candidate suggestions for the current text, we would commit one of
    		// them here.
    		commitTyped(getCurrentInputConnection());
    	}
    }
    
    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
    	if (mComposing.length() > 0) {
    		inputConnection.commitText(mComposing, mComposing.length());
    		mComposing.setLength(0);
    		updateCandidates();
    	}
    }
    
    public static String convertUnicodeArrayToChar(String uni) {
    	if (uni != null && uni.length() > 4 && uni.startsWith(UNI_PREFIX)) {
    		String [] codes = uni.split("u");
    		if (codes.length < 2 || codes[1].length() < 3) {
    			return uni;
    		}
    		char [] chs = codes[1].toCharArray();
    		int len = chs.length;
    		char [] dest = new char[8];
    		if (len <= 8) {
    			int added = 8 - len;
    			for (int i = 0; i < added; i++) {
    				dest[i] = '0';
    			}
    			for (int i = added; i < 8; i++) {
    				dest[i] = chs[i - added];
    			}
    		} else {
    			String destStr = codes[1].substring(0, 8);
    			dest = destStr.toCharArray();
    		}
    		
    		byte [] bs = new byte[4];
    		String a = "0x";
    		String b1 = a + String.valueOf(new char [] {dest[0], dest[1]});
    		String b2 = a + String.valueOf(new char [] {dest[2], dest[3]});
    		String b3 = a + String.valueOf(new char [] {dest[4], dest[5]});
    		String b4 = a + String.valueOf(new char [] {dest[6], dest[7]});
    		bs[0] = (byte)(Integer.decode(b1).intValue() & 0xFF);
    		bs[1] = (byte)(Integer.decode(b2).intValue() & 0xFF);
    		bs[2] = (byte)(Integer.decode(b3).intValue() & 0xFF);
    		bs[3] = (byte)(Integer.decode(b4).intValue() & 0xFF);
    		
    		String ret = uni;
    		try {
    			ret = new String(new String(bs, "UTF-32"));
    		} catch (UnsupportedEncodingException e) {
    			e.printStackTrace();
    		}
    		return ret;
    	}
    	return uni;
    }
    
    /**
     * Main initialization of the input method component. Be sure to call super class.
     */
    @Override
    public void onCreate() {
    	super.onCreate();
    	Looper looper = Looper.myLooper();
    	mHandler = new MessageHandler(looper);
    }
    
    @SuppressLint({ "HandlerLeak", "HandlerLeak" })
	final class MessageHandler extends Handler {
    	public MessageHandler(Looper looper) {
    		super(looper);
    	}
    	
    	@Override
    	public void handleMessage(Message msg) {
    		if (msg.what == 1 && mLongPressDel && mDelCount-- > 0 
    				&& (System.currentTimeMillis() - mPressTime > LONG_PRESS_TIME_MS)) {
    			handleBackspace();
    		}
    	}
    }
    
    private void startTimer() {
    	if (mTimer == null) {
    		mTimer = new Timer();
    	}
    	if (mTask == null) {
    		mTask = new TimerTask() {
    			public void run() {
    				if (mLongPressDel) {
    					Message message = new Message();
    					message.what = 1;
    					mHandler.sendMessage(message);
    					mDelCount++;
    				}
    			}
    		};
    	}
    	if (mTimer != null && mTask != null) {
    		mTimer.schedule(mTask, 0, SLEEP_TIME_MS);
    	}
    }
    
    private void stopTimer() {
    	if (mTimer != null) {
    		mTimer.cancel();
    		mTimer = null;
    	}
    	if (mTask != null) {
    		mTask.cancel();
    		mTask = null;
    	}
    	mDelCount = 0;
    }
    
    /**
     * This is the point where you can do all of your UI initialization.
     * It is called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {
    	mKeyboard = new Keyboard(this, R.xml.symbols);
    }
    
    /**
     * Called by the framework when your view for creating input needs to be generated.
     * This will be called the first time your input method is displayed, and every time it
     * needs to be re-created such as due to a configuration change.
     */
    @Override 
    public View onCreateInputView() {
//    	mKeyboardView = (KeyboardView)findViewById(R.id.keyboard_view);
    	mKeyboardView = (MyKeyboardView)getLayoutInflater().inflate(
    			R.layout.layout, null);
    	mKeyboardView.setKeyboard(mKeyboard);
    	mKeyboardView.setEnabled(true);
    	mKeyboardView.setPreviewEnabled(true);
    	mKeyboardView.setVisibility(View.VISIBLE);
    	mKeyboardView.setOnKeyboardActionListener(this);
    	mKeyboardView.setClickable(true);
    	mKeyboardView.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View arg0) {
				if (mLongPressDel) {
					handleBackspace();
				}
				return false;
			}
    	});
    	return mKeyboardView;
    	
//    	View mInputView = 
//            (View) getLayoutInflater().inflate( R.layout.view, null);
//        if (mReceiver == null) {
//        	IntentFilter filter = new IntentFilter(IME_MESSAGE);
//        	filter.addAction(IME_KEYCODE);
//        	filter.addAction(IME_EDITORCODE);
//        	mReceiver = new AdbReceiver();
//        	registerReceiver(mReceiver, filter);
//        }
//        return mInputView; 
    }
    
    /**
     * Called by the framework when your view for showing candidates needs to be generated.
     */
    @Override
    public View onCreateCandidatesView() {
    	mCandidateView = new CandidateView(this);
    	mCandidateView.setService(this);
    	return mCandidateView;
    }
    
    /**
     * This is the main point where we do our initialization of the input method to begin operating 
     * on an application. At this point we have been bound to the client, and are now receiving all of
     * the detailed information about the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
    	super.onStartInput(attribute, restarting);
    	
    	// reset our state. We want to do this even if restarting, because the underlying state of the
    	// text editor could have changed in any way.
    	mComposing.setLength(0);
    	mCompletions = null;
    }
    
    @Override
    public void onFinishInput() {
    	super.onFinishInput();
    	
    	// clear current composing text and candidates.
    	mComposing.setLength(0);
    	
    	// we only hide the candidates window when finishing input on a particular editor, to avoid
    	// popping the underlying application up and down if the user is entering text into the bottom of
    	// its window.
    	setCandidatesViewShown(false);
    	
    	if (mKeyboardView != null) {
    		mKeyboardView.closing();
    	}
    }
    
    @Override
    public void onDisplayCompletions(CompletionInfo [] completions) {
    	mCompletions = completions;
    	if (completions == null) {
    		setSuggestions(null, false, false);
    		return;
    	}
    	
    	List<String> stringList = new ArrayList<String>();
    	for (int i = 0; i < completions.length; i++) {
    		CompletionInfo ci = completions[i];
    		if (ci != null)
    			stringList.add(ci.getText().toString());
    	}
    	setSuggestions(stringList, true, true);
    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, 
    		int newSelEnd, int candidatesStart, int candidatesEnd) {
    	super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    	
    	// if the current selection in the text view changes, we should clear whatever candidate 
    	// text we have.
    	if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
    		mComposing.setLength(0);
    		InputConnection ic = getCurrentInputConnection();
    		if (ic != null) {
    			ic.finishComposingText();
    		}
    	}
    }
    
    public void onDestroy() {
    	if (mReceiver != null)
    		unregisterReceiver(mReceiver);
    	stopTimer();
    	super.onDestroy();    	
    }
    
    class AdbReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(IME_MESSAGE)) {
				String msg = intent.getStringExtra("msg");
				String outMsg = convertUnicodeArrayToChar(msg);
				InputConnection ic = getCurrentInputConnection();
				if (ic != null)
					ic.commitText(outMsg, 1);
			}
			
			if (intent.getAction().equals(IME_KEYCODE)) {				
				int code = intent.getIntExtra("code", -1);				
				if (code != -1) {
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
				}
			}
			
			if (intent.getAction().equals(IME_EDITORCODE)) {				
				int code = intent.getIntExtra("code", -1);				
				if (code != -1) {
					InputConnection ic = getCurrentInputConnection();
					if (ic != null)
						ic.performEditorAction(code);
				}
			}
		}
    }

	@Override
	public void onKey(int primaryCode, int[] keyCodes) {
		if (primaryCode == Keyboard.KEYCODE_DELETE) {	// delete
			handleBackspace();
		} else if (primaryCode == KEYCODE_CLEAR) {	// clear
			mComposing.setLength(0);
			getCurrentInputConnection().setComposingText("", 0);
			setCandidatesViewShown(false);
		} else if (primaryCode == KEYCODE_UNI) {	// \\u button clicked
			mComposing.append("\\u");
			updateCandidates();
		} else if (primaryCode == KEYCODE_OK) {	// ok
			getCurrentInputConnection().commitText(
					convertUnicodeArrayToChar(mComposing.toString()), 1);
			if (mCandidateView != null) {
				mCandidateView.clear();
				setCandidatesViewShown(false);
			}
		} else {
			handleCharacter(primaryCode, keyCodes);
		}
	}

	@Override
	public void onPress(int primaryCode) {
		mPressTime = System.currentTimeMillis();
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			mLongPressDel = true;
			startTimer();
		}
	}

	@Override
	public void onRelease(int primaryCode) {
		mPressTime = 0;
		mLongPressDel = false;
		mHandler.removeMessages(1);
		stopTimer();
	}

	@Override
	public void onText(CharSequence text) {}
	@Override
	public void swipeDown() {}
	@Override
	public void swipeLeft() {}
	@Override
	public void swipeRight() {}
	@Override
	public void swipeUp() {}
}
