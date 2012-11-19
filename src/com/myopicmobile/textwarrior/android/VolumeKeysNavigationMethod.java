/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package com.myopicmobile.textwarrior.android;


import android.view.KeyEvent;

public class VolumeKeysNavigationMethod extends TouchNavigationMethod{
	public VolumeKeysNavigationMethod(FreeScrollingTextField textField){
		super(textField);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			_textField.moveCaretLeft();
			return true;
		}
		else if(keyCode == KeyEvent.KEYCODE_VOLUME_UP){
			_textField.moveCaretRight();
			return true;
		}
		
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
				keyCode == KeyEvent.KEYCODE_VOLUME_UP){
			return true;
		}

		return false;
	}
}
