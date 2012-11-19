/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.myopicmobile.textwarrior.common.TextWarriorException;

public class TrackpadNavigationMethod extends TouchNavigationMethod{
	private boolean _isTrackpadPressed = false;
	private final Trackpad _trackpad = new Trackpad();
	
	public TrackpadNavigationMethod(FreeScrollingTextField textField){
		super(textField);
	}

	@Override
	public boolean onDown(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();
		_isTrackpadPressed = _trackpad.isInTrackpad(x, y);
		
		if(_isTrackpadPressed){
			return true;
		}
		else{
			return super.onDown(e);
		}
	}

	@Override
	public boolean onUp(MotionEvent e) {
		if(_isTrackpadPressed){
			_isTrackpadPressed = false;
			_xAccum = 0.0f;
			_yAccum = 0.0f;
		}
		super.onUp(e);
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		if(_isTrackpadPressed){
			if(_trackpad.isOpen()){
				moveCaretWithTrackpad(-distanceX, -distanceY);
			}
			//TODO find out if ACTION_UP events are actually passed to onScroll
			if ((e2.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP){
				onUp(e2);
			}
			
			return true;
		}
		else{
			return super.onScroll(e1, e2, distanceX, distanceY);
		}
	}
	
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		if(_isTrackpadPressed){
			onUp(e2);
			return true;
		}
		else{
			return super.onFling(e1, e2, velocityX, velocityY);
		}
	}

//TODO test on a multitouch device with individual tracking of points
/*
	private final static int INVALID_ID = -1;
	private int _secondPointerId = INVALID_ID;
	private float _lastSecondaryX = INVALID_ID;
	private float _lastSecondaryY = INVALID_ID;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int pointerId = getPointerId(event);
		int action = event.getAction() & MotionEvent.ACTION_MASK;
		
		//detect secondary motion events
		if (event.getPointerCount() == 2
				&& _secondPointerId == INVALID_ID
				&& action == MotionEvent.ACTION_POINTER_DOWN){
			_secondPointerId = pointerId;
			int pointerIndex = event.findPointerIndex(pointerId);
			_lastSecondaryX = event.getX(pointerIndex);
			_lastSecondaryY = event.getY(pointerIndex);
		}
		else if(_secondPointerId == pointerId){
			if (action == MotionEvent.ACTION_MOVE){
				int pointerIndex = event.findPointerIndex(pointerId);
				float distanceX = event.getX(pointerIndex);
				float distanceY = event.getY(pointerIndex);
				
				moveCaretWithTrackpad(distanceX - _lastSecondaryX,
						distanceY - _lastSecondaryY);
				_lastSecondaryX = distanceX;
				_lastSecondaryY = distanceY;
			}
			else if(action == MotionEvent.ACTION_POINTER_UP){
				_secondPointerId = INVALID_ID;
			}
		}

		super.onTouchEvent(event);
		return true;
	}
*/

	//number of pixels to scroll to move the caret one unit
	private final static int MOVEMENT_PIXELS = 16;
	//for use in determining whether the displacement is mainly on the x or y axis
	private final static double MIN_ATAN = 0.322; // == atan(1/3)
	private float _xAccum = 0.0f;
	private float _yAccum = 0.0f;
	
	private void moveCaretWithTrackpad(float distanceX, float distanceY){
		//reset accumulators when polarity of displacement changes
		if((_xAccum < 0 && distanceX > 0) || (_xAccum > 0 && distanceX < 0)){
			_xAccum = 0;
		}
		if((_yAccum < 0 && distanceY > 0) || (_yAccum > 0 && distanceY < 0)){
			_yAccum = 0;
		}

		double angle = Math.atan2(Math.abs(distanceX), Math.abs(distanceY));
		
		if(angle >= MIN_ATAN){
			//non-negligible x-axis movement
			float x = _xAccum + distanceX;
			int xUnits = ((int) x) / MOVEMENT_PIXELS;
			_xAccum = x - (xUnits * MOVEMENT_PIXELS);
			
			while(xUnits > 0){
				_textField.moveCaretRight();
				--xUnits;
			}
			while(xUnits < 0){
				_textField.moveCaretLeft();
				++xUnits;
			}
		}

		if((Math.PI/2 - angle) >= MIN_ATAN){
			//non-negligible y-axis movement
			float y = _yAccum + distanceY;
			int yUnits = ((int) y) / MOVEMENT_PIXELS;
			_yAccum = y - (yUnits * MOVEMENT_PIXELS);
			
			for(int i = yUnits; i > 0; --i){
				_textField.moveCaretDown();
			}
			for(int i = yUnits; i < 0; ++i){
				_textField.moveCaretUp();
			}
		}
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();

		if(_trackpad.isInTrackpad(x, y)){
			_trackpad.toggleDisplay();
			return true;
		}
		else{
			return super.onSingleTapConfirmed(e);
		}
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();
		
		//ignore taps on trackpad
		if(_trackpad.isInTrackpad(x, y)){
			return true;
		}
		else{
			return super.onDoubleTap(e);
		}
	}

	@Override
	public void onTextDrawComplete(Canvas canvas) {
		_trackpad.draw(canvas);
	}

	
	
	private class Trackpad{
		private final Bitmap OPEN_IMG = BitmapFactory.decodeResource(
				_textField.getContext().getResources(), R.drawable.trackpad_open);
		private final Bitmap CLOSE_IMG = BitmapFactory.decodeResource(
				_textField.getContext().getResources(), R.drawable.trackpad_close);
		private Drawable _trackpad_right_img, _trackpad_left_img;

		private final static int TRACKPAD_ALPHA = 225;
		private Paint _brush = new Paint();

		private final static int MAX_WIDTH_DP = 240;
		private final static int MAX_HEIGHT_DP = 140;
		private final int MAX_WIDTH;
		private final int MAX_HEIGHT;
		
		private int _width;
		private int _height;
		
		public Trackpad(){
			_width = OPEN_IMG.getWidth();
			_height = OPEN_IMG.getHeight();
			
			float dpScale = _textField.getResources().getDisplayMetrics().density;
			MAX_WIDTH = (int) (MAX_WIDTH_DP * dpScale + 0.5f);
			MAX_HEIGHT = (int) (MAX_HEIGHT_DP * dpScale + 0.5f);

			 TextWarriorException.assertVerbose(_width <= MAX_WIDTH,
			 	"Trackpad button width cannot be more than MAX_WIDTH");
			 TextWarriorException.assertVerbose(_height <= MAX_HEIGHT,
			 	"Trackpad button height cannot be more than MAX_HEIGHT");
			 TextWarriorException.assertVerbose(
					 (CLOSE_IMG.getWidth() == _width)
					 && (CLOSE_IMG.getHeight() == _height),
			 	"Open and close buttons have to be the same size");
			 
			_brush.setAlpha(TRACKPAD_ALPHA);
		}

		public boolean isInTrackpad(int x, int y) {
			Rect bounds = getTrackpadBoundingBox();
			return (x >= bounds.left && x < bounds.right
					&& y >= bounds.top && y < bounds.bottom
					);
		}
		
		public boolean isOpen(){
			return _state == OPENED;
		}


		private final static int CLOSED = 0;
		private final static int OPENED = 1;
		private final static int OPENING = 2;
		private final static int CLOSING = 3;
		private int _state = CLOSED;
		public void toggleDisplay(){
			if(_state == OPENED){
				close();
			}
			else if(_state == CLOSED){
				open();
			}
		}

		private final static long ANIMATION_DURATION = 100000000; //nanoseconds
		long _animationStartTime;
		public void open(){
			if(_state == CLOSED){
				_state = OPENING;
				
				_animationStartTime = System.nanoTime();
				_textField.post(showTrackpad);
			}
		}
		
		private Runnable showTrackpad = new Runnable(){
			public void run(){
				int baseWidth = CLOSE_IMG.getWidth();
				int baseHeight = CLOSE_IMG.getHeight();
				int widthDelta = MAX_WIDTH - baseWidth;
				int heightDelta = MAX_HEIGHT - baseHeight;
				
				float elapsedTime = System.nanoTime() - _animationStartTime;
				
				if(elapsedTime < ANIMATION_DURATION){
					_width = baseWidth +
							(int) (elapsedTime / ANIMATION_DURATION * widthDelta);
					_height = baseHeight +
							(int) (elapsedTime / ANIMATION_DURATION * heightDelta);
					invalidateTrackpad();
					_textField.post(showTrackpad);
				}
				else {
					_state = OPENED;
					_width = MAX_WIDTH;
					_height = MAX_HEIGHT;
					invalidateTrackpad();
				}
			}
		};
		
		public void close(){
			if(_state == OPENED){
				_state = CLOSING;

				_animationStartTime = System.nanoTime();
				_textField.post(hideTrackpad);
			}
		}

		private Runnable hideTrackpad = new Runnable(){
			public void run(){
				int baseWidth = OPEN_IMG.getWidth();
				int baseHeight = OPEN_IMG.getHeight();
				int widthDelta = MAX_WIDTH - baseWidth;
				int heightDelta = MAX_HEIGHT - baseHeight;
				
				float elapsedTime = System.nanoTime() - _animationStartTime;

				invalidateTrackpad();
				if(elapsedTime < ANIMATION_DURATION){
					_width = baseWidth +
							(int) ((1.0 - elapsedTime / ANIMATION_DURATION) * widthDelta);
					_height = baseHeight +
							(int) ((1.0 - elapsedTime / ANIMATION_DURATION) * heightDelta);
					_textField.post(hideTrackpad);
				}
				else {
					_state = CLOSED;
					_width = baseWidth;
					_height = baseHeight;
				}
			}
		};
		
		private void invalidateTrackpad(){
			_textField.invalidate(getTrackpadBoundingBox());
		}
		
		private Rect getTrackpadBoundingBox(){
			int bottom = _textField.getScrollY() + _textField.getHeight();
			int top = bottom - _height;
			
			int left, right;
			if(isRightHanded()){
				right = _textField.getScrollX() + _textField.getWidth();
				left = right - _width;
			}
			else{
				left = _textField.getScrollX();
				right = left + _width;
			}
			
			return new Rect(left, top, right, bottom);
		}

		
		public void draw(Canvas c){
			Rect bounds = getTrackpadBoundingBox();
			float trackpadLeft = bounds.left;
			float trackpadTop = bounds.top;
			
			boolean rightHanded = isRightHanded();
			Drawable trackpad = rightHanded ? getTrackpadRightImg()
					: getTrackpadLeftImg();
			
			Bitmap button;
			int buttonLeft, buttonTop;
			if(_state == CLOSED){
				button = OPEN_IMG;
				buttonLeft = (int) trackpadLeft;
				buttonTop = (int) trackpadTop;
			}
			else{
				button = CLOSE_IMG;
				buttonLeft = rightHanded ? bounds.right - CLOSE_IMG.getWidth()
							: (int) trackpadLeft;
				buttonTop = bounds.bottom - CLOSE_IMG.getHeight();
			}

			c.drawBitmap(button, buttonLeft, buttonTop, _brush);
			trackpad.setBounds((int) trackpadLeft, (int) trackpadTop,
					(int) trackpadLeft + _width, (int) trackpadTop + _height);
			trackpad.draw(c);
		}

		//lazy load images
		final private Drawable getTrackpadRightImg(){
			if(_trackpad_right_img == null){
				_trackpad_right_img = _textField.getContext().getResources()
		 			.getDrawable(R.drawable.trackpad_right);
			}
			return _trackpad_right_img;
		}
		
		final private Drawable getTrackpadLeftImg(){
			if(_trackpad_left_img == null){
				 _trackpad_left_img = _textField.getContext().getResources()
			 		.getDrawable(R.drawable.trackpad_left);
			}
			return _trackpad_left_img;
		}
	}//end inner class
}
