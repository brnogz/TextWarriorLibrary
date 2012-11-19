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
import android.view.MotionEvent;

import com.myopicmobile.textwarrior.common.Pair;

public class YoyoNavigationMethod extends TouchNavigationMethod{
	private boolean _isHandleTouched = false;
	private final Yoyo _yoyo = new Yoyo();

	public YoyoNavigationMethod(FreeScrollingTextField textField){
		super(textField);
	}
	
	@Override
	public boolean onDown(MotionEvent e) {
		super.onDown(e);
		if(!_isCaretTouched){
			int x = (int) e.getX() + _textField.getScrollX();
			int y = (int) e.getY() + _textField.getScrollY();
			_isHandleTouched = _yoyo.isInHandle(x, y);
			
			if(_isHandleTouched){
				_yoyo.setInitialTouch(x, y);
				_yoyo.invalidateHandle();
			}
		}
		
		return true;
	}

	@Override
	public boolean onUp(MotionEvent e) {
		if(_isHandleTouched){
			_isHandleTouched = false;
			_yoyo.clearInitialTouch();
			
			//TODO animate the handle to fly back to the caret position
			Rect caret = _textField.getBoundingBox(_textField.getCaretPosition());
			int x = caret.left + _textField.getPaddingLeft();
			int y = caret.bottom + _textField.getPaddingTop();
			_yoyo.attachYoyo(x, y);
		}
		super.onUp(e);
		return true;
	}
	
	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {

		if(_isHandleTouched){
			//TODO find out if ACTION_UP events are actually passed to onScroll
			if ((e2.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP){
				onUp(e2);
			}
			else{
				moveHandle(e2);
			}
			
			return true;
		}
		else{
			return super.onScroll(e1, e2, distanceX, distanceY);
		}
	}

	private void moveHandle(MotionEvent e) {
		int handleX = (int) e.getX() + _textField.getScrollX();
		int handleY = (int) e.getY() + _textField.getScrollY();
		
		Pair foundIndex = _yoyo.findNearestChar((int) e.getX(), (int) e.getY());
		int newCaretIndex = foundIndex.getFirst();

		if(newCaretIndex >= 0){
			_textField.moveCaret(newCaretIndex);
			//snap the handle to the caret
			Rect newCaretBounds = _textField.getBoundingBox(newCaretIndex);
			int newX = newCaretBounds.left + _textField.getPaddingLeft();
			int newY = newCaretBounds.bottom + _textField.getPaddingTop();

			boolean isExact = (foundIndex.getSecond() != -1);
			if(isExact){
				_yoyo.attachYoyo(newX, newY);
			}
			else{
				_yoyo.stretchYoyoFromTo(newX, newY, handleX, handleY);
			}
		}
		else{
			//not under a character; freely position the handle
			_yoyo.stretchYoyoTo(handleX, handleY);
		}
	}

	
	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		int x = (int) e.getX() + _textField.getScrollX();
		int y = (int) e.getY() + _textField.getScrollY();
		
		//ignore taps on handle
		if(_yoyo.isInHandle(x, y)){
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
		
		//ignore taps on handle
		if(_yoyo.isInHandle(x, y)){
			return true;
		}
		else{
			return super.onDoubleTap(e);
		}
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {

		if(_isHandleTouched){
			onUp(e2);
			return true;
		}
		else{
			return super.onFling(e1, e2, velocityX, velocityY);
		}
	}

	@Override
	public void onTextDrawComplete(Canvas canvas) {		
		if(!_isHandleTouched){
			Rect caret = _textField.getBoundingBox(_textField.getCaretPosition());
			int x = caret.left + _textField.getPaddingLeft();
			int y = caret.bottom + _textField.getPaddingTop();

			_yoyo.setRestingCoord(x, y);
		}

		_yoyo.draw(canvas, _isHandleTouched);
	}

	@Override
	public Rect getCaretBloat() {
		return _yoyo.getRestingSize();
	}
	

	private class Yoyo{
		private final static int YOYO_STRING_RESTING_HEIGHT = 28;
		//For those who want to change HANDLE_IMG, ensure that the image is a square,
		//or modify draw() so that the proper vertical center is calculated
		private final Bitmap HANDLE_IMG = BitmapFactory.decodeResource(
				_textField.getContext().getResources(), R.drawable.yoyo);
		private final Bitmap HANDLE_SELECTED_IMG = BitmapFactory.decodeResource(
				_textField.getContext().getResources(), R.drawable.yoyo_selected);
		private final Rect HANDLE_SIZE;

		//coordinates where the top of the yoyo string is attached
		private int _anchorX = 0;
		private int _anchorY = 0;
		
		//coordinates of the top-left corner of the yoyo handle
		private int _handleX = 0;
		private int _handleY = 0;
		
		//the offset where the handle is first touched,
		//(0,0) being the top-left of the handle
		private int _xOffset = 0;
		private int _yOffset = 0;

		private final static int YOYO_HANDLE_ALPHA = 180;
		private final static int YOYO_STRING_COLOR = 0x808080;
		private final Paint _brush;

		public Yoyo(){
			int radius = getRadius();
			HANDLE_SIZE = new Rect(
					radius,
					0,
					HANDLE_IMG.getWidth() - radius,
					HANDLE_IMG.getHeight() + YOYO_STRING_RESTING_HEIGHT);
			
			_brush = new Paint();
			_brush.setColor(YOYO_STRING_COLOR);
			_brush.setAlpha(YOYO_HANDLE_ALPHA);	
		}

		/**
		 * Draws the yoyo handle and string. The Yoyo handle can extend into 
		 * the padding region.
		 * 
		 * @param canvas
		 * @param activated True if the yoyo is activated. This causes a 
		 * 		different image to be loaded.
		 */
		public void draw(Canvas canvas, boolean activated){
			int radius = getRadius();
			Bitmap handle;
			if(activated){
				_brush.setAlpha(238); //make handle more solid
				canvas.drawLine(_anchorX, _anchorY,
						_handleX + radius, _handleY + radius, _brush);
				handle = HANDLE_SELECTED_IMG;
			}
			else{
				_brush.setAlpha(YOYO_HANDLE_ALPHA);
				canvas.drawLine(_anchorX, _anchorY,
						_handleX + radius, _handleY + 2, _brush);
				// +2 to make the string jut in a little to the handle image
				handle = HANDLE_IMG;
			}
			canvas.drawBitmap(handle, _handleX, _handleY, _brush);
		}
		
		final public int getRadius() {
			return HANDLE_IMG.getWidth() / 2;
		}
		
		public Rect getRestingSize(){
			return HANDLE_SIZE;
		}
		
		/**
		 * Clear the yoyo at the current position and attaches it to (x, y),
		 * with the handle hanging directly below.
		 */
		public void attachYoyo(int x, int y) {
			invalidateYoyo(); //clear old position
			setRestingCoord(x, y);
			invalidateYoyo(); //update new position
		}
		
		/**
		 * Stretch the yoyo with the attachment point at (anchorX, anchorY),
		 * and the handle top-left corner at (handleX, handleY).
		 */
		public void stretchYoyoFromTo(int anchorX, int anchorY, int handleX, int handleY) {
			invalidateYoyo(); //clear old position
			_anchorX = anchorX;
			_anchorY = anchorY;
			_handleX = handleX - _xOffset;
			_handleY = handleY - _yOffset;
			invalidateYoyo(); //update new position
		}
		
		/**
		 * Keep the yoyo attached to the current position but move the handle
		 * to (x, y).
		 */
		public void stretchYoyoTo(int x, int y) {
			stretchYoyoFromTo(_anchorX, _anchorY, x, y);
		}
		
		/**
		 * Sets the yoyo string to be attached at (x, y), with the handle 
		 * hanging directly below, but does not trigger any redrawing
		 */
		public void setRestingCoord(int x, int y){
			_anchorX = x;
			_anchorY = y;
			_handleX = x - getRadius();
			_handleY = y + YOYO_STRING_RESTING_HEIGHT;
		}
		
		private void invalidateYoyo(){
			int handleCenter = _handleX + getRadius();
			int x0, x1, y0, y1;
			if(handleCenter >= _anchorX){
				x0 = _anchorX;
				x1 = handleCenter + 1;
			}
			else{
				x0 = handleCenter;
				x1 = _anchorX + 1;
			}
			
			if(_handleY >= _anchorY){
				y0 = _anchorY;
				y1 = _handleY;
			}
			else{
				y0 = _handleY;
				y1 = _anchorY;
			}

			//invalidate the string area
			_textField.invalidate(x0, y0, x1, y1);
			invalidateHandle();
		}

		public void invalidateHandle() {
			Rect handleExtent = new Rect(_handleX, _handleY,
					_handleX + HANDLE_IMG.getWidth(), _handleY + HANDLE_IMG.getHeight());
			_textField.invalidate(handleExtent);
		}
		
		/**
		 * This method projects a yoyo string directly above the handle and
		 * determines which character it should be attached to, or -1 if no
		 * suitable character can be found.
		 * 
		 * (handleX, handleY) is the handle origin in screen coordinates,
		 * where (0, 0) is the top left corner of the textField, regardless of
		 * its internal scroll values.
		 * 
		 * @return Pair.first contains the nearest character while Pair.second
		 * 			is the exact character found by a strict search 
		 * 
		 */
		public Pair findNearestChar(int handleX, int handleY) {
			int attachedLeft = screenToViewX(handleX) - _xOffset + getRadius();
			int attachedBottom = screenToViewY(handleY) - _yOffset - YOYO_STRING_RESTING_HEIGHT - 1;

			return new Pair(_textField.coordToCharIndex(attachedLeft, attachedBottom),
					_textField.coordToCharIndexStrict(attachedLeft, attachedBottom));
		}

		/**
		 * Records the coordinates of the initial down event on the
		 * handle so that subsequent movement events will result in the
		 * handle being offset correctly.
		 * 
		 * Does not check if isInside(x, y). Calling methods have
		 * to ensure that (x, y) is within the handle area.
		 */
		public void setInitialTouch(int x, int y) {
			_xOffset = x - _handleX;
			_yOffset = y - _handleY;
		}
		
		public void clearInitialTouch(){
			_xOffset = 0;
			_yOffset = 0;
		}
		
		public boolean isInHandle(int x, int y){
			return (x >= _handleX
					&& x < (_handleX + HANDLE_IMG.getWidth())
					&& y >= _handleY
					&& y < (_handleY + HANDLE_IMG.getHeight())
			);
		}
	}//end inner class
}
