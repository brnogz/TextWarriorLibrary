/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;

import org.miscwidgets.widget.Panel;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

public class ClipboardPanel extends Panel{
	protected ImageButton _cut;
	protected ImageButton _copy;
	protected ImageButton _paste;
	
	private final float MIN_FLICK_VELOCITY = 150;
	private boolean mFlushedHandle; //whether the handle should be wrapped to the contents
	GestureDetector _gestureDetector;
	
	public ClipboardPanel(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ClipboardPanel);
		mFlushedHandle = a.getBoolean(R.styleable.ClipboardPanel_flushedHandle, false);

		_gestureDetector = new GestureDetector(context, _contentGestureListener);
		_gestureDetector.setIsLongpressEnabled(false);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		
    	_cut = (ImageButton) findViewById(R.id.cut);
    	_copy = (ImageButton) findViewById(R.id.copy);
    	_paste = (ImageButton) findViewById(R.id.paste);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if(mFlushedHandle){
			// Calculate the dimensions of the wrapped panel contents.
			// If this is a vertical panel, restrict the handle width to the content width;
			// If this is a horizontal panel, restrict the handle height to the content height;
			View content = getContent();
			content.measure(widthMeasureSpec, heightMeasureSpec);
			if (getPosition() == Panel.TOP || getPosition() == Panel.BOTTOM) {
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(
						content.getMeasuredWidth(), MeasureSpec.EXACTLY);
			} else {
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(
						content.getMeasuredHeight(), MeasureSpec.EXACTLY);
			}
		}
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event){
		_gestureDetector.onTouchEvent(event);
		return super.dispatchTouchEvent(event);
	}

	private final GestureDetector.SimpleOnGestureListener _contentGestureListener
		= new GestureDetector.SimpleOnGestureListener() {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			int orientation = getPosition();
			if (isOpen() && ((orientation == Panel.TOP && velocityY < -MIN_FLICK_VELOCITY) ||
						(orientation == Panel.BOTTOM && velocityY > MIN_FLICK_VELOCITY) ||
						(orientation == Panel.LEFT && velocityX < -MIN_FLICK_VELOCITY) ||
						(orientation == Panel.RIGHT && velocityX > MIN_FLICK_VELOCITY))){
				setOpen(false, true);
			}
			return true;
		}
	};

	/**
	 * Enables/disables the individual clipboard buttons
	 * 
	 * @param cut
	 * @param copy
	 * @param paste
	 */
	public void setClipboardButtonState(boolean cut, boolean copy, boolean paste){
    	_cut.setEnabled(cut);
    	_copy.setEnabled(copy);
    	_paste.setEnabled(paste);
	}
	
	public void setCutListener(OnClickListener listener){
		_cut.setOnClickListener(listener);
	}
	
	public void setCopyListener(OnClickListener listener){
		_copy.setOnClickListener(listener);
	}
	
	public void setPasteListener(OnClickListener listener){
		_paste.setOnClickListener(listener);
	}
}
