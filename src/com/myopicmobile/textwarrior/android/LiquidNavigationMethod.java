/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.android;


import com.myopicmobile.textwarrior.common.TextWarriorException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

public class LiquidNavigationMethod extends TouchNavigationMethod{
	// FLAME_IMG is used as a button background. When pressed, a large
	// GLOW_IMG is overlaid.
	private final Bitmap FLAME_IMG = BitmapFactory.decodeResource(
			_textField.getContext().getResources(), R.drawable.fire);
	private final Bitmap GLOW_IMG = BitmapFactory.decodeResource(
			_textField.getContext().getResources(), R.drawable.fire_glow);
	
	private boolean _isButtonPressed = false;
	private Paint _brush = new Paint();
	private final static int BUTTON_ALPHA = 128;
	
	public LiquidNavigationMethod(FreeScrollingTextField textField){
		super(textField);
		SensorManager sm = (SensorManager) textField.getContext()
				.getSystemService(Context.SENSOR_SERVICE);
		
		if(sm.getDefaultSensor(Sensor.TYPE_ORIENTATION) == null){
			Log.w(TextWarriorApplication.LOG_TAG,
			"Liquid navigation selected but device does not have an orientation sensor");
		}
		
		TextWarriorException.assertVerbose(GLOW_IMG.getWidth() >= FLAME_IMG.getWidth()
				 && GLOW_IMG.getHeight() >= FLAME_IMG.getHeight(),
		 	"Flame glow cannot be smaller than flame image");
		
		_brush.setAlpha(BUTTON_ALPHA);
	}

	@Override
	public boolean onDown(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();
		_isButtonPressed = isInButton(x, y);
		
		if(_isButtonPressed){
			startSensing();
			
			invalidateGlow();
			return true;
		}
		else{
			return super.onDown(e);
		}
	}

	@Override
	public boolean onUp(MotionEvent e) {
		if(_isButtonPressed){
			stopSensing();
			_textField.stopAutoScrollCaret();
			
			_isButtonPressed = false;
			_prevRoll = INIT_ROLL;
			_prevPitch = INIT_PITCH;
			invalidateGlow();
		}
		return super.onUp(e);
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		if(_isButtonPressed){
			//TODO find out if ACTION_UP events are actually passed to onScroll
			if ((e2.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP){
				onUp(e2);
			}

			//do nothing
			return true;
		}
		else{
			return super.onScroll(e1, e2, distanceX, distanceY);
		}
	}
	
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		if(_isButtonPressed){
			onUp(e2);
			return true;
		}
		else{
			return super.onFling(e1, e2, velocityX, velocityY);
		}
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();

		//ignore taps on button
		if(isInButton(x, y)){
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

		//ignore taps on button
		if(isInButton(x, y)){
			return true;
		}
		else{
			return super.onDoubleTap(e);
		}
	}
	
	private boolean isInButton(int x, int y) {
		Rect bounds = getBoundingBox(FLAME_IMG);
		return (x >= bounds.left && x < bounds.right
				&& y >= bounds.top && y < bounds.bottom
				);
	}
	

	/**
	 * Returns the bounding box of image when it is positioned at the 
	 * bottom-left/right corner of _textField. The corner used depends on 
	 * whether the navigation mode is right- or left-handed.
	 */
	private Rect getBoundingBox(Bitmap image){
		int bottom = _textField.getScrollY() + _textField.getHeight();
		int top = bottom - image.getHeight();
		
		int left, right;
		if(isRightHanded()){
			right = _textField.getScrollX() + _textField.getWidth();
			left = right - image.getWidth();
		}
		else{
			left = _textField.getScrollX();
			right = left + image.getWidth();
		}
		
		return new Rect(left, top, right, bottom);
	}

	
	private void startSensing() {
		SensorManager sm = (SensorManager) _textField.getContext()
			.getSystemService(Context.SENSOR_SERVICE);
		Sensor orientationSensor = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		sm.registerListener(orientationListener,
				orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
		_textField.post(_updateSensorResults);
	}
	
	private void stopSensing() {
		_textField.removeCallbacks(_updateSensorResults);
		SensorManager sm = (SensorManager) _textField.getContext()
			.getSystemService(Context.SENSOR_SERVICE);
		Sensor orientationSensor = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		sm.unregisterListener(orientationListener, orientationSensor);
	}
	
	@Override
	void onPause() {
		//TODO investigate on different Android devices whether a
		//MotionEvent.ACTION_UP is generated while a finger is still on the
		//screen and TextWarrior goes to the background because the device enters
		//sleep mode or an incoming call occurs, etc. If yes, there is no need
		//to call onUp() again here.
		onUp(null);
		super.onPause();
	}


	private final SensorEventListener orientationListener = new SensorEventListener() {
		public void onSensorChanged(SensorEvent sensorEvent) {
			_currPitch = sensorEvent.values[1];
			_currRoll = sensorEvent.values[2];
		}
		
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			//do nothing
		}
	};



	private final static int UP = 0x10;
	private final static int DOWN = 0x20;
	private final static int LEFT = 1;
	private final static int RIGHT = 2;
	private final static int ORIENTATION_X_MASK = 0x0F;
	private final static int ORIENTATION_Y_MASK = 0xF0;
	
	private final boolean isLeft(int orientation){
		return (orientation & ORIENTATION_X_MASK) == LEFT;
	}
	
	private final boolean isRight(int orientation){
		return (orientation & ORIENTATION_X_MASK) == RIGHT;
	}
	
	private final boolean isUp(int orientation){
		return (orientation & ORIENTATION_Y_MASK) == UP;
	}
	
	private final boolean isDown(int orientation){
		return (orientation & ORIENTATION_Y_MASK) == DOWN;
	}

	private final static float INIT_ROLL = 0.0f;
	private final static float INIT_PITCH = -10.0f;
	private float _prevRoll = INIT_ROLL;
	private float _prevPitch = INIT_PITCH;
	private float _currRoll = INIT_ROLL;
	private float _currPitch = INIT_PITCH;
	private final static int UPDATE_PERIOD = 200;
	
	//Thresholds where a rotation is recognized to be in a certain direction
	private final static float UP_THRESHOLD = -10;
	private final static float DOWN_THRESHOLD = -60;
	private final static float LEFT_THRESHOLD = 20;
	private final static float RIGHT_THRESHOLD = -20;
	
	private int calcOrientation(float pitch, float roll){
		WindowManager sm = (WindowManager) _textField.getContext()
			.getSystemService(Context.WINDOW_SERVICE);
		int screenOrientation = sm.getDefaultDisplay().getOrientation();
		
		float correctedPitch, correctedRoll;
		switch(screenOrientation){
		case Surface.ROTATION_0:
			correctedPitch = pitch;
			correctedRoll = roll;
			break;
		case Surface.ROTATION_90:
			correctedPitch = -roll;
			correctedRoll = pitch;
			break;
		case Surface.ROTATION_180:
			correctedPitch = -pitch;
			correctedRoll = -roll;
			break;
		case Surface.ROTATION_270:
			correctedPitch = roll;
			correctedRoll = -pitch;
			break;
		default:
			Log.w(TextWarriorApplication.LOG_TAG, "Unsupported screen orientation");
			correctedPitch = pitch;
			correctedRoll = roll;
			break;
		}

		int correctedOrientation = 0;
		if(correctedRoll >= LEFT_THRESHOLD){
			correctedOrientation |= LEFT;
		}
		else if(correctedRoll <= RIGHT_THRESHOLD){
			correctedOrientation |= RIGHT;
		}

		if(correctedPitch >= UP_THRESHOLD){
			correctedOrientation |= UP;
		}
		else if(correctedPitch <= DOWN_THRESHOLD){
			correctedOrientation |= DOWN;
		}
		
		return correctedOrientation;
	}
	
	private final Runnable _updateSensorResults = new Runnable(){
		public void run(){
			int orientation = calcOrientation(_currPitch, _currRoll);
			int prevOrientation = calcOrientation(_prevPitch, _prevRoll);
			
			if(isLeft(orientation)){
				if(isRight(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_RIGHT);
				}
				if(!isLeft(prevOrientation)){
					_textField.autoScrollCaret(FreeScrollingTextField.SCROLL_LEFT);
				}
			}
			else if(isRight(orientation)){
				if(isLeft(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_LEFT);
				}
				if(!isRight(prevOrientation)){
					_textField.autoScrollCaret(FreeScrollingTextField.SCROLL_RIGHT);
				}
			}
			else{
				//neutral orientation in x-axis; stop existing x-scrolls
				if(isLeft(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_LEFT);
				}
				else if(isRight(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_RIGHT);
				}
			}
			
			if(isUp(orientation)){
				if(isDown(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_DOWN);
				}
				if(!isUp(prevOrientation)){
					_textField.autoScrollCaret(FreeScrollingTextField.SCROLL_UP);
				}
			}
			else if(isDown(orientation)){
				if(isUp(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_UP);
				}
				if(!isDown(prevOrientation)){
					_textField.autoScrollCaret(FreeScrollingTextField.SCROLL_DOWN);
				}
			}
			else{
				//neutral orientation in y-axis; stop existing y-scrolls
				if(isUp(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_UP);
				}
				else if(isDown(prevOrientation)){
					_textField.stopAutoScrollCaret(FreeScrollingTextField.SCROLL_DOWN);
				}
			}

			_prevPitch = _currPitch;
			_prevRoll = _currRoll;
			
			_textField.postDelayed(_updateSensorResults, UPDATE_PERIOD);
		}
	};


	private void invalidateGlow() {
		_textField.invalidate(getBoundingBox(GLOW_IMG));
	}
	
	@Override
	public void onTextDrawComplete(Canvas canvas) {
		Rect buttonBounds = getBoundingBox(FLAME_IMG);

		canvas.drawBitmap(FLAME_IMG, buttonBounds.left, buttonBounds.top, _brush);
		
		if(_isButtonPressed){
			_brush.setAlpha(255);

			if(isRightHanded()){
				int left = buttonBounds.right - GLOW_IMG.getWidth();
				int top = buttonBounds.bottom - GLOW_IMG.getHeight();
				canvas.drawBitmap(GLOW_IMG, left, top, _brush);
			}
			else{
				Matrix m = new Matrix();
				m.preScale(-1, 1); //flip the glow image
				m.postTranslate(GLOW_IMG.getWidth() + buttonBounds.left,
					buttonBounds.bottom - GLOW_IMG.getHeight());
				canvas.drawBitmap(GLOW_IMG, m, _brush);
			}
			
			_brush.setAlpha(BUTTON_ALPHA);
		}
	}

}
