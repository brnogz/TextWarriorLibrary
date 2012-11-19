/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

/*
 *****************************************************************************
 *
 * --------------------------------- row length
 * Hello World(\n)                 | 12
 * This is a test of the caret(\n) | 28
 * func|t|ions(\n)                 | 10
 * of this program(EOF)            | 16
 * ---------------------------------
 * 
 * The figure illustrates the convention for counting characters.
 * Rows 36 to 39 of a hypothetical text file are shown.
 * The 0th char of the file is off-screen.
 * Assume the first char on screen is the 257th char.
 * The caret is before the char 't' of the word "functions". The caret is drawn
 * as a filled blue rectangle enclosing the 't'.
 * 
 * _caretPosition == 257 + 12 + 28 + 4 == 301
 * 
 * Note 1: EOF (End Of File) is a real char with a length of 1
 * Note 2: Characters enclosed in parentheses are non-printable
 * 
 *****************************************************************************
 *
 * There is a difference between rows and lines in TextWarrior.
 * Rows are displayed while lines are a pure logical construct.
 * When there is no word-wrap, a line of text is displayed as a row on screen.
 * With word-wrap, a very long line of text may be split across several rows 
 * on screen.
 * 
 */
package com.myopicmobile.textwarrior.android;

import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.ClipboardManager;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Scroller;

import com.myopicmobile.textwarrior.common.ColorScheme;
import com.myopicmobile.textwarrior.common.DocumentProvider;
import com.myopicmobile.textwarrior.common.LanguageCFamily;
import com.myopicmobile.textwarrior.common.Lexer;
import com.myopicmobile.textwarrior.common.Pair;
import com.myopicmobile.textwarrior.common.RowListener;
import com.myopicmobile.textwarrior.common.TextWarriorException;

/**
 * A custom text view that uses a solid shaded caret (aka cursor) instead of a 
 * blinking caret and allows a variety of navigation methods to be easily
 * integrated.
 * 
 * Responsibilities
 * 1. Display text
 * 2. Display padding
 * 3. Scrolling
 * 4. Store and display caret position and selection range
 * 5. Store font type, font size, and tab length
 * 6. Interpret non-touch input events and shortcut keystrokes, triggering
 *    the appropriate inner class controller actions
 * 7. Reset view, set cursor position and selection range
 *
 * Inner class controller responsibilities
 * 1. Caret movement
 * 2. Activate/deactivate selection mode
 * 3. Cut, copy, paste, delete, insert
 * 4. Schedule areas to repaint and analyze for spans in response to edits
 * 5. Directs scrolling if caret movements or edits causes the caret to be off-screen
 * 6. Notify rowListeners when caret row changes
 * 7. Provide helper methods for InputConnection to setComposingText from the IME
 *
 * This class is aware that the underlying text buffer uses an extra char (EOF) 
 * to mark the end of the text. The text size reported by the text buffer includes
 * this extra char. Some bounds manipulation is done so that this implementation
 * detail is hidden from client classes.
 */
public class FreeScrollingTextField extends View{

	protected DocumentProvider _hDoc; // the model in MVC
	TextFieldController _fieldController; // the controller in MVC
	TextFieldInputConnection _inputConnection;
	private Scroller _scroller;
	protected TouchNavigationMethod _navMethod;
	protected RowListener _rowLis;
	protected SelectionModeListener _selModeLis;
	protected boolean _isDirty = false;

	protected int _caretPosition = 0;
	private int _caretRow = 0; // can be calculated, but stored for efficiency purposes
	protected int _selectionAnchor = -1; // inclusive
	protected int _selectionEdge = -1; // exclusive

	private Paint _brush;
	/** Max amount that can be scrolled horizontally for the current frame */
	private int _xExtent = 0;
	protected int _tabLength = DEFAULT_TAB_LENGTH_SPACES;


	/** Scale factor for the width of a caret when on a \n or EOF char.
	 *  A factor of 1.0 is equals to the width of a space character */
	protected static float EMPTY_CARET_WIDTH_SCALE = 0.75f;
	/** When in selection mode, the caret height is scaled by this factor */
	protected static float SEL_CARET_HEIGHT_SCALE = 0.5f;
	protected static int DEFAULT_TAB_LENGTH_SPACES = 4;
	protected static int BASE_TEXT_SIZE_PIXELS = 16;


	public FreeScrollingTextField(Context context, AttributeSet attrs){
		super(context, attrs);
		_hDoc = new DocumentProvider();
		_navMethod = new TouchNavigationMethod(this);
		_scroller = new Scroller(context);
		initView();
	}
	
	public FreeScrollingTextField(Context context, AttributeSet attrs, int defStyle){
		super(context, attrs, defStyle);
		_hDoc = new DocumentProvider();
		_navMethod = new TouchNavigationMethod(this);
		_scroller = new Scroller(context);
		initView();
	}

	private void initView(){
		_fieldController = this.new TextFieldController();

		_brush = new Paint();
		_brush.setAntiAlias(true);
		_brush.setTextSize(BASE_TEXT_SIZE_PIXELS);
		
		setBackgroundColor(ColorScheme.backgroundColor);
		setFocusableInTouchMode(true);
		setHapticFeedbackEnabled(true);
		
		_rowLis = new RowListener() {
			@Override
			public void onRowChange(int newRowIndex) {
				// Do nothing
			}
		};
		
		_selModeLis = new SelectionModeListener() {
			@Override
			public void onSelectionModeChanged(boolean active) {
				// Do nothing
			}
		};
		resetView();

		//TODO find out if this function works
		//setScrollContainer(true);
	}

	private void resetView(){
		_caretPosition = 0;
		_caretRow = 0;
		_xExtent = 0;
		_fieldController.setSelectText(false);
		_fieldController.stopTextComposing();
		_hDoc.clearSpans();
		_rowLis.onRowChange(0);
		scrollTo(0, 0);
	}

	public void changeDocumentProvider(DocumentProvider hDoc){
		_hDoc = hDoc;
		resetView();
		_fieldController.cancelSpanning(); //stop existing lex threads
		_fieldController.determineSpans();
		invalidate();
	}

	DocumentProvider createDocumentProvider(){
		return new DocumentProvider(_hDoc);
	}

	public void setRowListener(RowListener rLis){
		_rowLis = rLis;
	}

	public void setSelModeListener(SelectionModeListener sLis){
		_selModeLis = sLis;
	}

	/**
	 * Sets the caret navigation method used by this text field
	 */
	public void setNavigationMethod(TouchNavigationMethod navMethod) {
		_navMethod = navMethod;
	}

	public void setDirty(boolean set){
		_isDirty = set;
	}

	public boolean isDirty(){
		return _isDirty;
	}

	@Override
	public InputConnection onCreateInputConnection (EditorInfo outAttrs){
		outAttrs.inputType = InputType.TYPE_CLASS_TEXT
			| InputType.TYPE_TEXT_FLAG_MULTI_LINE;
		outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
			| EditorInfo.IME_ACTION_DONE
			| EditorInfo.IME_FLAG_NO_EXTRACT_UI;
		if (_inputConnection == null){
			_inputConnection = this.new TextFieldInputConnection(this);
		}
		else{
			_inputConnection.resetComposingState();
		}
		return _inputConnection;
	}

	@Override
	public boolean onCheckIsTextEditor(){
		return true;
	}

	@Override
	public boolean isSaveEnabled() {
		return true;
	}

  //---------------------------------------------------------------------
  //------------------------- Layout methods ----------------------------
//TODO test with height less than 1 complete row
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(useAllDimensions(widthMeasureSpec),
        		useAllDimensions(heightMeasureSpec));
    }

    @Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		makeCharVisible(_caretPosition);
	}

	private int useAllDimensions(int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int result = MeasureSpec.getSize(measureSpec);

        if (specMode != MeasureSpec.EXACTLY && specMode != MeasureSpec.AT_MOST) {
            result = Integer.MAX_VALUE;
			TextWarriorException.assertVerbose(false,
			 	"MeasureSpec cannot be UNSPECIFIED. Setting dimensions to max.");
        }

        return result;
    }

	final protected int getNumVisibleRows(){
		return (int) Math.ceil((double) getContentHeight() / rowHeight());
	}

	final protected int rowHeight(){
		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
		//TODO confirm ascent and leading are negative in Android
		return (-metrics.ascent - metrics.leading + metrics.descent);
	}
	
	/*	 
     The only methods that have to worry about padding are invalidate, draw 
	 and computeVerticalScrollRange() methods. Other methods can assume that
	 the text completely fills a rectangular viewport given by getContentWidth()
	 and getContentHeight()
	 */
	final public int getContentHeight(){
		return getHeight() - getPaddingTop() - getPaddingBottom();
	}

	final public int getContentWidth(){
		return getWidth() - getPaddingLeft() - getPaddingRight();
	}
	
	
	//---------------------------------------------------------------------
	//-------------------------- Paint methods ----------------------------
	/**
	 * The first row of text to paint, which may be partially visible.
	 * Deduced from the clipping rectangle given to onDraw()
	 */
	private int getBeginPaintRow(Canvas canvas){
		Rect bounds = canvas.getClipBounds();
		return bounds.top / rowHeight(); 
	}

	/**
	 * The last row of text to paint, which may be partially visible.
	 * Deduced from the clipping rectangle given to onDraw()
	 */
	private int getEndPaintRow(Canvas canvas){
		//clip top and left are inclusive; bottom and right are exclusive
		Rect bounds = canvas.getClipBounds();
		return (bounds.bottom - 1) / rowHeight(); 
	}
	
	/**
	 * @return The x-value of the baseline for drawing text on the given row
	 */
	private int getPaintBaseline(int row){
		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
		return (row + 1) * rowHeight() - metrics.descent - 1;
		//TODO confirm if origin used in Canvas.drawText is on ascent section
		// or descent section. If in descent section, don't need to subtract 1
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.save();
		
		//translate clipping region to create padding around edges
		canvas.clipRect(getScrollX() + getPaddingLeft(),
				getScrollY() + getPaddingTop(),
				getScrollX() + getWidth() - getPaddingRight(),
				getScrollY() + getHeight() - getPaddingBottom());
		canvas.translate(getPaddingLeft(), getPaddingTop());
		realDraw(canvas);
		
		canvas.restore();
 		
 		_navMethod.onTextDrawComplete(canvas);
	}

	private void realDraw(Canvas canvas){
 		int beginPaintRow = getBeginPaintRow(canvas);
	    int currentIndex = _hDoc.getStartCharOfRow(beginPaintRow);
 		if(currentIndex < 0){
 			return;
 		}

		//----------------------------------------------
		// set up span coloring settings
		//----------------------------------------------
		int spanIndex = 0;
		List<Pair> spans = _hDoc.getSpans();

	    // There must be at least one span to paint, even for an empty file,
	    // where the span contains only the EOF character
		TextWarriorException.assertVerbose(!spans.isEmpty(),
		 	"No spans to paint in TextWarrior.paint()");

		Pair nextSpan = (Pair) spans.get(spanIndex++);
		Pair currSpan;
		do{
			currSpan = nextSpan;
			if(spanIndex < spans.size()){
				nextSpan = (Pair) spans.get(spanIndex++);
			}
			else{
				nextSpan = null;
			}
		}
		while(nextSpan != null &&
				nextSpan.getFirst() <= currentIndex);
		
		int spanColor = ColorScheme.getTokenColor(currSpan.getSecond());
		_brush.setColor(spanColor);

		//----------------------------------------------
		// set up graphics settings
		//----------------------------------------------
	    int paintX = 0;
	    int paintY = getPaintBaseline(beginPaintRow);
	    int endY = getPaintBaseline(getEndPaintRow(canvas));

	    //----------------------------------------------
	    // start painting!
	    //----------------------------------------------
	    _hDoc.seekChar(currentIndex);
	    while (paintY <= endY && _hDoc.hasNext()){
	     	// check if formatting changes are needed
	     	if (reachedNextSpan(currentIndex, nextSpan)){
	 			currSpan = nextSpan;
	 			spanColor = ColorScheme.getTokenColor(currSpan.getSecond());
	 			_brush.setColor(spanColor);
	
	 			if(spanIndex < spans.size()){
	 				nextSpan = (Pair) spans.get(spanIndex++);
	 			}
	 			else{
	 				nextSpan = null;
	 			}
	     	}
	
	     	char c = _hDoc.next();
//TODO investigate performance gain	 if (paintX < getScrollX() + getWidth()){

	    	if (currentIndex == _caretPosition){
	    		paintX += drawCaret(canvas, c, paintX, paintY);
	    	}
	    	else if (_fieldController.inSelectionRange(currentIndex)){
	    		paintX += drawSelectedText(canvas, c, paintX, paintY);
	    	}
	    	else{
	    		paintX += drawChar(canvas, c, paintX, paintY);
	    	}

	     	++currentIndex;
	     	if (c == LanguageCFamily.NEWLINE){
	 	 		paintY += rowHeight();
	     		if (paintX > _xExtent){
	     			_xExtent = paintX;
	     		}
	 	 		paintX = 0;
	     	}
		} // end while
	    
 		if (paintX > _xExtent){
 			// record widest line seen so far
 			_xExtent = paintX;
 		}
	}
	
	private int drawChar(Canvas canvas, char c, int paintX, int paintY){
		if(c != LanguageCFamily.NEWLINE &&
				c != LanguageCFamily.EOF &&
				c != LanguageCFamily.TAB){
 			char[] ca = {c};
	 		canvas.drawText(ca, 0, 1, paintX, paintY, _brush);
		}

		return getAdvance(c);
	}

	// paintY is the baseline for text, NOT the top extent
	private void drawTextBackground(Canvas canvas, int paintX, int paintY,
			int advance){
		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
 		canvas.drawRect(paintX,
 				//TODO confirm if origin is in ascent or descent section
 				paintY + metrics.ascent + metrics.leading + 1,
 				paintX + advance,
 				paintY + metrics.descent + 1, 
 				_brush);
	}

	private int drawSelectedText(Canvas canvas, char c, int paintX, int paintY){
		int oldColor = _brush.getColor();
		int advance = getAdvance(c);

		_brush.setColor(ColorScheme.selBackgroundColor);
		drawTextBackground(canvas, paintX, paintY, advance);

		_brush.setColor(ColorScheme.selForegroundColor);
		drawChar(canvas, c, paintX, paintY);

		_brush.setColor(oldColor);
		return advance;
	}


	private int drawCaret(Canvas canvas, char c, int paintX, int paintY){
		int originalColor = _brush.getColor();
		int textColor = originalColor;
		int advance = getAdvance(c);

	  	if(_caretPosition == _selectionAnchor &&
	  			_caretPosition != _selectionEdge){
	  		// draw selection background
			_brush.setColor(ColorScheme.selBackgroundColor);
			drawTextBackground(canvas, paintX, paintY, advance);
			textColor = ColorScheme.caretForegroundColor;
	  	}

	  	int caretColor = (isFocused()) ? ColorScheme.caretBackgroundColor:
	  		ColorScheme.caretDisabledColor;
		_brush.setColor(caretColor);
	  	if (_caretPosition == _selectionEdge ||
	  			_caretPosition == _selectionAnchor){
	  		// draw half caret
	  		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
	 		canvas.drawRect(paintX,
	 				paintY + (metrics.ascent * SEL_CARET_HEIGHT_SCALE),
	 				paintX + advance,
	 				paintY + metrics.descent + 1, 
	 				_brush);
	  	}
	  	else{
	  		// draw full caret
			drawTextBackground(canvas, paintX, paintY, advance);
			textColor = ColorScheme.caretForegroundColor;
	  	}

		_brush.setColor(textColor);
  		// draw text
		drawChar(canvas, c, paintX, paintY);
		_brush.setColor(originalColor);
		return advance;
	}
	
	/**
	 * Returns printed width of c.
	 * 
	 * Takes into account user-specified tab width and also handles
	 * application-defined widths for NEWLINE and EOF
	 * 
	 * @param c Character to measure
	 * @return Advance of character
	 */
	protected int getAdvance(char c){
		int advance;
		
		switch (c){
 		case LanguageCFamily.NEWLINE: // fall-through
 		case LanguageCFamily.EOF:
 			advance = getEmptyAdvance();
 			break;
 		case LanguageCFamily.TAB:
 			advance = getTabAdvance();
 			break;
 		default:
 			char[] ca = {c};
 			advance = (int) _brush.measureText(ca, 0, 1);
 			break;	
		}
		
		return advance;
	}
	
	final protected int getEmptyAdvance(){
		return (int) (EMPTY_CARET_WIDTH_SCALE * _brush.measureText(" ", 0, 1));
	}
	
	final protected int getTabAdvance(){
		return _tabLength * (int) _brush.measureText(" ", 0, 1);
	}
	
	/**
	 * Invalidate rows from startRow (inclusive) to endRow (exclusive)
	 */
	private void invalidateRows(int startRow, int endRow) {
		TextWarriorException.assertVerbose(startRow <= endRow && startRow >= 0,
	 		"Invalid startRow and/or endRow");

        //TODO The descent of (startRow-1) and the ascent of (startRow+1)
		//may jut inside startRow, so parts of these rows have to be invalidated
		//as well. This is a problem for Thai, Vietnamese and Indic scripts
		int top = startRow * rowHeight() + getPaddingTop();
        if (startRow > 0) {
	  		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
            top -= metrics.descent;
        }
        Rect caretSpill = _navMethod.getCaretBloat();
        top = Math.max(0, top - caretSpill.top);
        
		super.invalidate(0,
			top,
			getScrollX() + getWidth(),
			endRow * rowHeight() + getPaddingTop() + caretSpill.bottom);
	}

	/**
	 * Invalidate rows from startRow (inclusive) to the end of the field
	 */
	private void invalidateFromRow(int startRow) {
		TextWarriorException.assertVerbose(startRow >= 0,
	 		"Invalid startRow");
		
        //TODO The descent of (startRow-1) and the ascent of (startRow+1)
		//may jut inside startRow, so parts of these rows have to be invalidated
		//as well. This is a problem for Thai, Vietnamese and Indic scripts
		int top = startRow * rowHeight() + getPaddingTop();
        if (startRow > 0) {
	  		Paint.FontMetricsInt metrics = _brush.getFontMetricsInt();
            top -= metrics.descent;
        }
        Rect caretSpill = _navMethod.getCaretBloat();
        top = Math.max(0, top - caretSpill.top);
        
		super.invalidate(0,
			top,
			getScrollX() + getWidth(),
			getScrollY() + getHeight());
	}

	private void invalidateCaretRow(){
		invalidateRows(_caretRow, _caretRow+1);
	}

	public void invalidateSelectionRows(){
		int startRow = _hDoc.getRowIndex(_selectionAnchor);
		int endRow = _hDoc.getRowIndex(_selectionEdge);

		invalidateRows(startRow, endRow+1);
	}

	/**
	 * Scrolls the text horizontally and/or vertically if the character 
	 * specified by charOffset is not in the visible text region.
	 * The view is invalidated if it is scrolled.
	 * 
	 * @param charOffset The index of the character to make visible
	 * @return True if the drawing area was scrolled horizontally
	 * 			and/or vertically
	 */
	public boolean makeCharVisible(int charOffset){
		TextWarriorException.assertVerbose(
				charOffset >= 0 && charOffset < _hDoc.docLength(),
				"Invalid charOffset given");
		int scrollVerticalBy = makeCharRowVisible(charOffset);
		int scrollHorizontalBy = makeCharColumnVisible(charOffset);
		
		if (scrollVerticalBy == 0 && scrollHorizontalBy == 0){
			return false;
		}
		else{
			scrollBy(scrollHorizontalBy, scrollVerticalBy);
			return true;
		}
	}
 
	/**
	 * Calculates the amount to scroll vertically if the char is not
	 * in the visible region.
	 * 
	 * @param charOffset The index of the character to make visible
	 * @return The amount to scroll vertically
	 */
	private int makeCharRowVisible(int charOffset){
		int scrollBy = 0;
		int charTop = _hDoc.getRowIndex(charOffset) * rowHeight();
		int charBottom = charTop + rowHeight();

		if (charTop < getScrollY()){
			scrollBy = charTop - getScrollY();
		}
		else if (charBottom > (getScrollY() + getContentHeight())){
			scrollBy = charBottom - getScrollY() - getContentHeight();
		}

		return scrollBy;
	}

	/**
	 * Calculates the amount to scroll horizontally if the char is not
	 * in the visible region.
	 * 
	 * @param charOffset The index of the character to make visible
	 * @return The amount to scroll horizontally
	 */
	private int makeCharColumnVisible(int charOffset){
		int scrollBy = 0;
		Pair visibleRange = getCharExtent(charOffset);

	    int charLeft = visibleRange.getFirst();
	    int charRight = visibleRange.getSecond();

	    if (charRight > (getScrollX() + getContentWidth())){
	    	scrollBy = charRight - getScrollX() - getContentWidth();
	    }

	    if (charLeft < getScrollX()){
	    	scrollBy = charLeft - getScrollX();
	    }
	    
	    return scrollBy;
	}
	
	/**
	 * Calculates the x-coordinate extent of charOffset.
	 * 
	 * @return The x-values of left and right edges of charOffset. Pair.first 
	 * 		contains the left edge and Pair.second contains the right edge
	 */
	protected Pair getCharExtent(int charOffset){
		int rowIndex = _hDoc.getRowIndex(charOffset);
		int charCount = _hDoc.seekLine(rowIndex);
		int left = 0;
		int right = 0;

		while(charCount <= charOffset && _hDoc.hasNext()){
			left = right;
			char c = _hDoc.next();
			switch (c){
			case LanguageCFamily.NEWLINE:
			case LanguageCFamily.EOF:
				right += getEmptyAdvance();
				break;
			case LanguageCFamily.TAB:
				right += getTabAdvance();
				break;
			default:
				char[] ca = {c};
				right += (int) _brush.measureText(ca, 0, 1);
				break;
			}
			++charCount;
		}
 
		return new Pair(left, right);
	}
	
	/**
	 * Returns the bounding box of a character in the text field.
	 * The coordinate system used is one where (0, 0) is the top left corner
	 * of the text, before padding is added.
	 * 
	 * @param charOffset The character offset of the character of interest
	 * 
	 * @return Rect(left, top, right, bottom) of the character bounds,
	 * 		or Rect(-1, -1, -1, -1) if there is no character at that coordinate.
	 */
	public Rect getBoundingBox(int charOffset){
		if(charOffset < 0 || charOffset >= _hDoc.docLength()){
			return new Rect(-1, -1, -1, -1);
		}

		int row = _hDoc.getRowIndex(charOffset);
		int top = row * rowHeight();
		int bottom = top + rowHeight();
		
		Pair xExtent = getCharExtent(charOffset);
		int left = xExtent.getFirst();
		int right = xExtent.getSecond();
		
		return new Rect(left, top, right, bottom);
	}

	
	//---------------------------------------------------------------------
	//------------------- Scrolling and touch -----------------------------
	/**
	 * Maps a coordinate to the character that it is on. If the coordinate is
	 * on empty space, the nearest character on the corresponding row is returned.
	 * If there is no character on the row, -1 is returned.
	 * 
	 * The coordinates passed in should not have padding applied to them.
	 * 
	 * @param x x-coordinate
	 * @param y y-coordinate
	 * 
	 * @return The index of the closest character, or -1 if there is
	 * 			no character or nearest character at that coordinate
	 */
	public int coordToCharIndex(int x, int y){
		int row = y / rowHeight();
		int charIndex = _hDoc.seekLine(row);
		if(charIndex >= 0){
			if(x < 0){
				return charIndex; // coordinate is outside, to the left of view
			}
			
			int extent = 0;
			while(extent < x && _hDoc.hasNext()){
				char c = _hDoc.next();
				if (c == LanguageCFamily.NEWLINE || c == LanguageCFamily.EOF){
					break;
				}
				else if (c == LanguageCFamily.TAB){
					extent += getTabAdvance();
				}
				else{
					char[] ca = {c};
					extent += (int) _brush.measureText(ca, 0, 1);
				}
				++charIndex;
			}
			if(extent > x){
				//went one past the mapped char
				--charIndex;
			}
			return charIndex;
		}
		else{
			//non-existent row
			return -1;
		}
	}
	
	/**
	 * Maps a coordinate to the character that it is on.
	 * Returns -1 if there is no character on the coordinate.
	 * 
	 * The coordinates passed in should not have padding applied to them.
	 * 
	 * @param x x-coordinate
	 * @param y y-coordinate
	 * 
	 * @return The index of the character that is on the coordinate,
	 * 			or -1 if there is no character at that coordinate.
	 */
	public int coordToCharIndexStrict(int x, int y){
		int row = y / rowHeight();
		int charIndex = _hDoc.seekLine(row);
		
		if(charIndex >= 0 && x >= 0){
			int extent = 0;
			while(extent < x && _hDoc.hasNext()){
				char c = _hDoc.next();
				if (c == LanguageCFamily.NEWLINE || c == LanguageCFamily.EOF){
					break;
				}
				else if (c == LanguageCFamily.TAB){
					extent += getTabAdvance();
				}
				else{
					char[] ca = {c};
					extent += (int) _brush.measureText(ca, 0, 1);
				}
				++charIndex;
			}
			
			if(extent < x){
				charIndex = -1; //no char on x
			}
			else{
				//went one past the mapped char
				--charIndex;
			}
		}
		else{
			//non-existent row
			charIndex = -1;
		}
		
		return charIndex;
	}
	
	/**
	 * Not private to allow access by TouchNavigationMethod
	 * 
	 * @return The maximum x-value that can be scrolled to for the current rows
	 * of text in the viewport.
	 */
	int getMaxScrollX(){
		return Math.max(0,
				_xExtent - getContentWidth() + _navMethod.getCaretBloat().right);
	}
	
	/**
	 * Not private to allow access by TouchNavigationMethod
	 * 
	 * @return The maximum y-value that can be scrolled to.
	 */
	int getMaxScrollY(){
		return Math.max(0,
			_hDoc.rowCount()*rowHeight() - getContentHeight() + _navMethod.getCaretBloat().bottom);
	}
	
	@Override
	protected int computeVerticalScrollOffset() {
		return getScrollY();
	}

	@Override
	protected int computeVerticalScrollRange() {
		return _hDoc.rowCount() * rowHeight() + getPaddingTop() + getPaddingBottom();
	}

	@Override
	public void computeScroll() {
		if (_scroller.computeScrollOffset()){
			scrollTo(_scroller.getCurrX(), _scroller.getCurrY());
		}
	}

	/**
	 * Start fling scrolling
	 */
	public void flingScroll(int velocityX, int velocityY) {
		_scroller.fling(getScrollX(), getScrollY(), velocityX, velocityY,
				0, getMaxScrollX(), 0, getMaxScrollY());
		// Keep on drawing until the animation has finished.
		postInvalidate();
	}

	public boolean isFlingScrolling() {
		return !_scroller.isFinished();
	}

	public void stopFlingScrolling() {
		_scroller.forceFinished(true);
	}


	//---------------------------------------------------------------------
	//--------------------------  Caret Scroll  ---------------------------
	public final static int SCROLL_UP = 0;
	public final static int SCROLL_DOWN = 1;
	public final static int SCROLL_LEFT = 2;
	public final static int SCROLL_RIGHT = 3;
	private final static long SCROLL_PERIOD = 250;
	
	/**
	 * Starting scrolling continuously in scrollDir.
	 * Not private to allow access by TouchNavigationMethod.
	 * 
	 * @return True if auto-scrolling started
	 */	
	boolean autoScrollCaret(int scrollDir) {
		boolean scrolled = false;
		switch(scrollDir){
		case SCROLL_UP:
			removeCallbacks(_scrollCaretUpTask);
			if((!caretOnFirstRowOfFile())){
				post(_scrollCaretUpTask);
				scrolled = true;
			}
			break;
		case SCROLL_DOWN:
			removeCallbacks(_scrollCaretDownTask);
			if(!caretOnLastRowOfFile()){
				post(_scrollCaretDownTask);
				scrolled = true;
			}
			break;
		case SCROLL_LEFT:
			removeCallbacks(_scrollCaretLeftTask);
			if (_caretPosition > 0 &&
    				_caretRow == _hDoc.getRowIndex(_caretPosition - 1)){
				post(_scrollCaretLeftTask);
				scrolled = true;
			}
			break;
		case SCROLL_RIGHT:
			removeCallbacks(_scrollCaretRightTask);
    		if (!caretOnEOF() &&
    				_caretRow == _hDoc.getRowIndex(_caretPosition + 1)){
    			post(_scrollCaretRightTask);
				scrolled = true;
    		}
			break;
		default:
			 TextWarriorException.assertVerbose(false,
			 	"Invalid scroll direction");
			break;
		}
		return scrolled;
	}
	
	/**
	 * Stops automatic scrolling initiated by autoScrollCaret(int).
	 * Not private to allow access by TouchNavigationMethod
	 */
	void stopAutoScrollCaret() {
		removeCallbacks(_scrollCaretDownTask);
		removeCallbacks(_scrollCaretUpTask);
		removeCallbacks(_scrollCaretLeftTask);
		removeCallbacks(_scrollCaretRightTask);
	}
	
	/**
	 * Stops automatic scrolling in scrollDir direction.
	 * Not private to allow access by TouchNavigationMethod
	 */
	void stopAutoScrollCaret(int scrollDir) {
		switch(scrollDir){
		case SCROLL_UP:
			removeCallbacks(_scrollCaretUpTask);
			break;
		case SCROLL_DOWN:
			removeCallbacks(_scrollCaretDownTask);
			break;
		case SCROLL_LEFT:
			removeCallbacks(_scrollCaretLeftTask);
			break;
		case SCROLL_RIGHT:
			removeCallbacks(_scrollCaretRightTask);
			break;
		default:
			 TextWarriorException.assertVerbose(false,
			 	"Invalid scroll direction");
			break;
		}
	}
	
	private final Runnable _scrollCaretDownTask = new Runnable(){
		public void run(){
			_fieldController.moveCaretDown();
			if(!caretOnLastRowOfFile()){
				postDelayed(_scrollCaretDownTask, SCROLL_PERIOD);
			}
		}
	};

	private final Runnable _scrollCaretUpTask = new Runnable(){
		public void run(){
			_fieldController.moveCaretUp();
			if(!caretOnFirstRowOfFile()){
				postDelayed(_scrollCaretUpTask, SCROLL_PERIOD);
			}
		}
	};

	private final Runnable _scrollCaretLeftTask = new Runnable(){
		public void run(){
			_fieldController.moveCaretLeft();
    		if (_caretPosition > 0 &&
    				_caretRow == _hDoc.getRowIndex(_caretPosition - 1)){
    			postDelayed(_scrollCaretLeftTask, SCROLL_PERIOD);
    		}
		}
	};

	private final Runnable _scrollCaretRightTask = new Runnable(){
		public void run(){
			_fieldController.moveCaretRight();
    		if (!caretOnEOF() &&
    				_caretRow == _hDoc.getRowIndex(_caretPosition + 1)){
    			postDelayed(_scrollCaretRightTask, SCROLL_PERIOD);
    		}
		}
	};
	
	
	//---------------------------------------------------------------------
	//------------------------- Caret methods -----------------------------
	
	public int getCaretRow(){
		return _caretRow;
	}
  
	public int getCaretPosition(){
		return _caretPosition;
	}
	
	/**
	 * This helper method should only be used by internal methods after setting
	 * _caretPosition, in order to to recalculate the new row the caret is on.
	 * Typically, the return value is then used to set _caretRow. 
	 */
	private int determineCaretRow(){
		return _hDoc.getRowIndex(_caretPosition);
	}
	
	/**
	 * Sets the caret to position i, scrolls it to view and invalidates 
	 * the necessary areas for redrawing
	 * 
	 * @param i The character index that the caret should be set to
	 */
	public void moveCaret(int i) {
		_fieldController.moveCaret(i);
	}
	
	/**
	 * Sets the caret one position back, scrolls it on screen, and invalidates
	 * the necessary areas for redrawing.
	 * 
	 * If the caret is already on the first character, nothing will happen.
	 */
	public void moveCaretLeft() {
		_fieldController.moveCaretLeft();
	}
	
	/**
	 * Sets the caret one position forward, scrolls it on screen, and 
	 * invalidates the necessary areas for redrawing.
	 * 
	 * If the caret is already on the last character, nothing will happen.
	 */
	public void moveCaretRight() {
		_fieldController.moveCaretRight();
	}
	
	/**
	 * Sets the caret one row down, scrolls it on screen, and invalidates the
	 * necessary areas for redrawing.
	 * 
	 * If the caret is already on the last row, nothing will happen.
	 */
	public void moveCaretDown() {
		_fieldController.moveCaretDown();
	}
	
	/**
	 * Sets the caret one row up, scrolls it on screen, and invalidates the
	 * necessary areas for redrawing.
	 * 
	 * If the caret is already on the first row, nothing will happen.
	 */
	public void moveCaretUp() {
		_fieldController.moveCaretUp();
	}

	/**
	 * Scrolls the caret into view if it is not on screen
	 */
	public void focusCaret() {
		makeCharVisible(_caretPosition);
	}

	/**
	 * @return The column number where charOffset appears on
	 */
	protected int getColumn(int charOffset){
		int row = _hDoc.getRowIndex(charOffset);
		TextWarriorException.assertVerbose(row >= 0,
 			"Invalid char offset given to getColumn");
		int firstCharOfRow = _hDoc.getStartCharOfRow(row);
		return charOffset - firstCharOfRow;
	}

	final protected boolean caretOnFirstRowOfFile(){
		return (_caretRow == 0);
	}

	final protected boolean caretOnLastRowOfFile(){
		return (_caretRow == (_hDoc.rowCount()-1));
	}
  
	final protected boolean caretOnEOF(){
		return (_caretPosition == (_hDoc.docLength()-1));
	}


	//---------------------------------------------------------------------
	//------------------------- Text Selection ----------------------------

	public final boolean isSelectText(){
		return _fieldController.isSelectText();
	}
	
	/**
	 * Enter or exit select mode.
	 * Invalidates necessary areas for repainting.
	 * 
	 * @param mode If true, enter select mode; else exit select mode
	 */
	public void selectText(boolean mode){
		if(_fieldController.isSelectText() && !mode){
			invalidateSelectionRows();
			_fieldController.setSelectText(false);
		}
		else if(!_fieldController.isSelectText() && mode){
			invalidateCaretRow();
			_fieldController.setSelectText(true);
		}
	}
	
	public void selectAll(){
		_fieldController.setSelectionRange(0, _hDoc.docLength()-1, false);
	}

	public void setSelectionRange(int beginPosition, int numChars){
		_fieldController.setSelectionRange(beginPosition, numChars, true);
	}
	
	public boolean inSelectionRange(int charOffset){
		return _fieldController.inSelectionRange(charOffset);
	}
	
	public int getSelectionStart(){
		return _selectionAnchor;
	}

	public int getSelectionEnd(){
		return _selectionEdge;
	}
	
	public void focusSelectionStart(){
		_fieldController.focusSelection(true);
	}

	public void focusSelectionEnd(){
		_fieldController.focusSelection(false);
	}
	
	public void cut(ClipboardManager cb) {
		_fieldController.cut(cb);
	}

	public void copy(ClipboardManager cb) {
		_fieldController.copy(cb);
	}
	
	public void paste(String text) {
		_fieldController.paste(text);
	}
	
	//---------------------------------------------------------------------
	//------------------------- Formatting methods ------------------------
	
	private boolean reachedNextSpan(int charIndex, Pair span){
		return (span == null) ? false : (charIndex == span.getFirst());
	}

	public void respan() {
		_fieldController.determineSpans();
	}
	
	public void cancelSpanning() {
		_fieldController.cancelSpanning();
	}

	/**
	 * Sets the text to use the new typeface, scrolls the view to display the 
	 * caret if needed, and invalidates the entire view
	 */
	public void setTypeface(Typeface typeface) {
		_brush.setTypeface(typeface);
		if(!makeCharVisible(_caretPosition)){
			invalidate();
		}
	}
	
	/**
	 * Sets the text size to be factor of the base text size, scrolls the view 
	 * to display the caret if needed, and invalidates the entire view
	 */
	public void setZoom(float factor){
		if(factor <= 0){
			return;
		}
		
		int newSize = (int) (factor * BASE_TEXT_SIZE_PIXELS);
		_brush.setTextSize(newSize);
		if(!makeCharVisible(_caretPosition)){
			invalidate();
		}
	}
	
	/**
	 * Sets the length of a tab character, scrolls the view to display the 
	 * caret if needed, and invalidates the entire view
	 * 
	 * @param spaceCount The number of spaces a tab represents
	 */
	public void setTabSpaces(int spaceCount){
		if(spaceCount < 0){
			return;
		}
		
		_tabLength = spaceCount;
		if(!makeCharVisible(_caretPosition)){
			invalidate();
		}
	}
	
	//---------------------------------------------------------------------
	//------------------------- Event handlers ----------------------------
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event){
		// Let the touch navigation method intercept the key event first
		if(_navMethod.onKeyDown(keyCode, event)){
			return true;
		}
		
    	if (KeysInterpreter.isNavigationKey(event)){
    		if(event.isShiftPressed() && !isSelectText()){
				invalidateCaretRow();
    			_fieldController.setSelectText(true);
    		}
    		else if(!event.isShiftPressed() && isSelectText()){
				invalidateSelectionRows();
    			_fieldController.setSelectText(false);
    		}

    		switch(keyCode){
    		case KeyEvent.KEYCODE_DPAD_RIGHT:
    			_fieldController.moveCaretRight();
    			break;
    		case KeyEvent.KEYCODE_DPAD_LEFT:
    			_fieldController.moveCaretLeft();
    			break;
    		case KeyEvent.KEYCODE_DPAD_DOWN:
    			_fieldController.moveCaretDown();
    			break;
    		case KeyEvent.KEYCODE_DPAD_UP:
    			_fieldController.moveCaretUp();
    			break;
    		default:
    			break;
    		}
			//event.startTracking(); // let IME receive long press events
    		return true;
        }
    	
    	char c = KeysInterpreter.keyEventToPrintableChar(event);
		if(c != LanguageCFamily.NULL_CHAR){
			_fieldController.onPrintableChar(c, event.getEventTime());
			//event.startTracking(); // let IME receive long press events
			return true;
		}

    	return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(_navMethod.onKeyUp(keyCode, event)){
			return true;
		}
		
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		// TODO Test on real device
		int deltaX = Math.round(event.getX());
		int deltaY = Math.round(event.getY());
		while(deltaX > 0){
			_fieldController.moveCaretRight();
			--deltaX;
		}
		while(deltaX < 0){
			_fieldController.moveCaretLeft();
			++deltaX;
		}
		while(deltaY > 0){
			_fieldController.moveCaretDown();
			--deltaY;
		}
		while(deltaY < 0){
			_fieldController.moveCaretUp();
			++deltaY;
		}
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event){
		if(isFocused()){
			_navMethod.onTouchEvent(event);
		}
		else{
			if((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP
					&& isInView((int) event.getX(), (int) event.getY())){
			// somehow, the framework does not automatically change the focus
			// to this view when it is touched
				requestFocus();
			}
		}
		return true;
	}

	final private boolean isInView(int x, int y) {
		return (x >= 0 && x < getWidth() &&
				y >= 0 && y < getHeight());
	}

	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		invalidateCaretRow();
	}


	public void showIME(boolean show){
		InputMethodManager im = (InputMethodManager) getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if(show){
			im.showSoftInput(this, 0);
		}
		else{
			im.hideSoftInputFromWindow(this.getWindowToken(), 0); 
		}
	}


	/**
	 * Some navigation methods use sensors or have states for their widgets.
	 * They should be notified of application lifecycle events so they can
	 * start/stop sensing and load/store their GUI state.
	 * 
	 * Not public. Should only be called by {@link TextWarriorApplication}
	 */
	void onPause() {
		_navMethod.onPause();
	}

	void onResume() {
		_navMethod.onResume();
	}
	
	void onDestroy() {
		_fieldController.cancelSpanning();
	}
	
	//*********************************************************************
    //************************ Controller logic ***************************
    //*********************************************************************

	private class TextFieldController
	implements Lexer.LexCallback{
		private boolean _isInSelectionMode = false;
		private Lexer _lexer = new Lexer(this);

		/**
		 * Analyze the text for programming language keywords and redraws the
		 * text view when done. The global programming language used is set with
		 * the static method Lexer.setLanguage(Language)
		 * 
		 * Does nothing if the Lexer language is not a programming language
		 */
		public void determineSpans() {
			_lexer.tokenize(_hDoc);
		}
		
		public void cancelSpanning() {
			_lexer.cancelTokenize();
		}
		
		@Override
		//This is usually called from a non-UI thread
		public void lexDone(final List<Pair> results) {
			post(new Runnable(){
				public void run(){
					_hDoc.setSpans(results);
					invalidate();
				}
			});
		}

	    //- TextFieldController -----------------------------------------------
	    //---------------------------- Key presses ----------------------------
		
		//TODO minimise invalidate calls from moveCaretXX() and deletion
		public void onPrintableChar(char c, long eventTime) {
			// delete currently selected text, if any
			boolean selectionDeleted = false;
			if(_isInSelectionMode){
				selectionDelete();
				selectionDeleted = true;
			}

			switch(c){
			case LanguageCFamily.BACKSPACE:
				if(selectionDeleted){
					break;
				}
				
				if (_caretPosition > 0){
					moveCaretLeft();
					char deleted = _hDoc.charAt(_caretPosition);
					_hDoc.deleteAt(_caretPosition, System.nanoTime());

					if (deleted == LanguageCFamily.NEWLINE){
						// mark rest of screen from caret for repainting
						invalidateFromRow(_caretRow);
					}
				}
				break;
				
			case LanguageCFamily.NEWLINE:
				// mark rest of screen from caret for repainting
				invalidateFromRow(_caretRow);
			//fall-through
				
			default:
				_hDoc.insertBefore(c, _caretPosition, System.nanoTime());
				moveCaretRight();
				break;
			}

			setDirty(true);
			determineSpans();
		}

		public void moveCaretDown(){
	    	if (!caretOnLastRowOfFile()){
	    		int currCaret = _caretPosition;
	    		int currRow = _caretRow;
	    		int newRow = currRow + 1;
	    		int currColumn = getColumn(currCaret);
	    		int currRowLength = _hDoc.rowLength(currRow);
	    		int newRowLength = _hDoc.rowLength(newRow);
	    		
	    		if (currColumn < newRowLength){
		    		// Position at the same column as old row.
		    		_caretPosition += currRowLength;
		    	}
		    	else{
		    		// Column does not exist in the new row (new row is too short).
		    		// Position at end of new row instead.
		    		_caretPosition +=
		    			currRowLength - currColumn + newRowLength - 1;
		    	}
	    		++_caretRow;

	    		updateSelectionRange(currCaret, _caretPosition);
	    		if (!makeCharVisible(_caretPosition)){
	    			invalidateRows(currRow, newRow + 1);
	    		}
	    		_rowLis.onRowChange(newRow);
	    		stopTextComposing();
	    	}
	    }

		public void moveCaretUp(){
	    	if (!caretOnFirstRowOfFile()){
	    		int currCaret = _caretPosition;
	    		int currRow = _caretRow;
	    		int newRow = currRow - 1;
	    		int currColumn = getColumn(currCaret);
	    		int newRowLength = _hDoc.rowLength(newRow);

		    	if (currColumn < newRowLength){
		    		// Position at the same column as old row.
		    		_caretPosition -= newRowLength;
		    	}
		    	else{
		    		// Column does not exist in the new row (new row is too short).
		    		// Position at end of new row instead.
		    		_caretPosition -= (currColumn + 1);
		    	}
	    		--_caretRow;

	    		updateSelectionRange(currCaret, _caretPosition);
	    		if (!makeCharVisible(_caretPosition)){
	    			invalidateRows(newRow, currRow + 1);
	    		}
	    		_rowLis.onRowChange(newRow);
	    		stopTextComposing();
	    	}
	    }

		public void moveCaretRight(){
	    	if(!caretOnEOF()){
	    		int currRow = _caretRow;
	    		++_caretPosition;
	    		int newRow = determineCaretRow();
	    		if(currRow != newRow){
	    			_caretRow = newRow;
	    			_rowLis.onRowChange(newRow);
	    		}
	    		updateSelectionRange(_caretPosition-1, _caretPosition);
	    		if (!makeCharVisible(_caretPosition)){
	    			invalidateRows(currRow, newRow + 1);
	    		}
	    		stopTextComposing();
	    	}
	    }

		public void moveCaretLeft(){
	    	if(_caretPosition > 0){
	    		int currRow = _caretRow;
	    		--_caretPosition;
	    		int newRow = determineCaretRow();
	    		if(currRow != newRow){
	    			_caretRow = newRow;
	    			_rowLis.onRowChange(newRow);
	    		}
	    		updateSelectionRange(_caretPosition+1, _caretPosition);
	    		if (!makeCharVisible(_caretPosition)){
	    			invalidateRows(newRow, currRow + 1);
	    		}
	    		stopTextComposing();
	    	}
	    }

		public void moveCaret(int i) {
			if(i < 0 || i >= _hDoc.docLength()){
				TextWarriorException.assertVerbose(false,
					"Invalid caret position");
				return;
			}

    		updateSelectionRange(_caretPosition, i);
			_caretPosition = i;
			updateAfterCaretJump();
		}

		private void updateAfterCaretJump() {
			int oldRow = _caretRow;
			_caretRow = determineCaretRow();

			if (oldRow != _caretRow){
				_rowLis.onRowChange(_caretRow);
			}
			if(!makeCharVisible(_caretPosition)){
				invalidateRows(oldRow, oldRow+1);
				invalidateCaretRow();
			}
			stopTextComposing();
		}

		private void stopTextComposing(){
			InputMethodManager im = (InputMethodManager) getContext()
				.getSystemService(Context.INPUT_METHOD_SERVICE);
			// This is an overkill way to inform the InputMethod that the caret
			// might have changed position and it should re-evaluate the
			// caps mode to use.
			im.restartInput(FreeScrollingTextField.this);
			
			if(_inputConnection != null && _inputConnection.isComposingStarted()){
				_inputConnection.resetComposingState();
			}
		}
		
	    //- TextFieldController -----------------------------------------------
	    //-------------------------- Selection mode ---------------------------
		public final boolean isSelectText(){
			return _isInSelectionMode;
		}
		
		/**
		 * Enter or exit select mode.
		 * Does not invalidate view.
		 * 
		 * @param mode If true, enter select mode; else exit select mode
		 */
		public void setSelectText(boolean mode){
			if(!(mode ^ _isInSelectionMode)){
				return;
			}

			if(mode){
				_selectionAnchor = _caretPosition;
				_selectionEdge = _caretPosition;
			}
			else{
				_selectionAnchor = -1;
				_selectionEdge = -1;
			}
			_isInSelectionMode = mode;
			_selModeLis.onSelectionModeChanged(mode);
		}

		public boolean inSelectionRange(int charOffset){
			if(_selectionAnchor < 0){
				return false;
			}
		
			return (_selectionAnchor <= charOffset &&
					charOffset < _selectionEdge);
		}
	    
		/**
		 * Selects numChars count of characters starting from beginPosition.
		 * Invalidates necessary areas.
		 * 
		 * @param beginPosition
		 * @param numChars
		 * @param scrollToStart If true, the start of the selection will be scrolled
		 * into view. Otherwise, the end of the selection will be scrolled.
		 */
		public void setSelectionRange(int beginPosition, int numChars,
				boolean scrollToStart){
			TextWarriorException.assertVerbose(
				(beginPosition >= 0) && numChars <= (_hDoc.docLength()-1) && numChars >= 0,
			 	"Invalid range to select");

			if(_isInSelectionMode){
				// unhighlight previous selection
				invalidateSelectionRows();
			}
			else{
				// unhighlight caret
				invalidateCaretRow();
				setSelectText(true);
			}

			_selectionAnchor = beginPosition;
			_selectionEdge = _selectionAnchor + numChars;
			
			_caretPosition = _selectionEdge;
			stopTextComposing();
			int newRow = determineCaretRow();
			if(newRow != _caretRow){
				_caretRow = newRow;
				_rowLis.onRowChange(newRow);
			}
			
			boolean scrolled = makeCharVisible(_selectionEdge);

			if(scrollToStart){
				//TODO reduce unnecessary scrolling and write a method to scroll
				// the beginning of multi-line selections as far left as possible
				scrolled = makeCharVisible(_selectionAnchor);
			}


			if (!scrolled){
				invalidateSelectionRows();
			}
		}

		/**
		 * Moves the caret to an edge of selected text and scrolls it to view.
		 * 
		 * @param beginning If true, moves the caret to the beginning of
		 * the selection. Otherwise, moves the caret to the end of the selection.
		 * In all cases, the caret is scrolled to view if it is not visible. 
		 */
		public void focusSelection(boolean start){
			if(_isInSelectionMode){
				if(start && _caretPosition != _selectionAnchor){
					_caretPosition = _selectionAnchor;
					updateAfterCaretJump();
				}
				else if(!start &&_caretPosition != _selectionEdge){
					_caretPosition = _selectionEdge;
					updateAfterCaretJump();
				}
			}
		}

		
	    /**
	     * Used by internal methods to update selection boundaries when a new 
	     * caret position is set.
	     * Does nothing if not in selection mode.
	     */
	    private void updateSelectionRange(int oldCaretPosition, int newCaretPosition){
	    	if (!_isInSelectionMode){
	    		return;
	    	}

	    	if (oldCaretPosition < _selectionEdge){
	    		if(newCaretPosition > _selectionEdge){
	    			_selectionAnchor = _selectionEdge;
	    			_selectionEdge = newCaretPosition;
	    		}
	    		else{
	    			_selectionAnchor = newCaretPosition;
	    		}
	    		
	    	}
	    	else{
	    		if(newCaretPosition < _selectionAnchor){
	    			_selectionEdge = _selectionAnchor;
	    			_selectionAnchor = newCaretPosition;
	    		}
	    		else{
	    			_selectionEdge = newCaretPosition;
	    		}
	    	}
		}
	    
		
	    //- TextFieldController -----------------------------------------------
	    //------------------------ Cut, copy, paste ---------------------------

		/**
		 * Convenience method for consecutive copy and paste calls
		 */
		public void cut(ClipboardManager cb) {
			copy(cb);
			selectionDelete();
		}
		
		/**
		 * Copies the selected text to the clipboard.
		 * 
		 * Does nothing if not in select mode.
		 */
		public void copy(ClipboardManager cb) {
			//TODO catch OutOfMemoryError
			if(_isInSelectionMode &&
					_selectionAnchor < _selectionEdge){
				char[] contents = _hDoc.subSequence(_selectionAnchor,
					_selectionEdge - _selectionAnchor);
				cb.setText(new String(contents));
			}
		}
		
		/**
		 * Inserts text at the caret position.
		 * Existing selected text will be deleted and select mode will end.
		 * The deleted area will be invalidated.
		 * 
		 * After insertion, the inserted area will be invalidated.
		 */
		public void paste(String text){
			if(text == null){
				return;
			}
			
			_hDoc.beginBatchEdit();
			selectionDelete();
			int originalRow = _caretRow;
			_hDoc.insertBefore(text.toCharArray(), _caretPosition, System.nanoTime());
			_hDoc.endBatchEdit();
			
			_caretPosition += text.length();
			int newRow = determineCaretRow();
			if(newRow != originalRow){
				_caretRow = newRow;
				_rowLis.onRowChange(newRow);
			}

			setDirty(true);
			determineSpans();
			stopTextComposing();

			if(!makeCharVisible(_caretPosition)){
				if(originalRow == newRow){
					//pasted text only affects current row
					invalidateRows(originalRow, newRow+1);
				}
				else{
					invalidateFromRow(originalRow);
				}
			}
		}
		
		/**
		 * Deletes selected text, exits select mode and invalidates deleted area.
		 * If the selected range is empty, this method exits select mode and
		 * invalidates the caret.
		 * 
		 * Does nothing if not in select mode.
		 */
		public void selectionDelete(){
			if(!_isInSelectionMode){
				return;
			}
			
			int totalChars = _selectionEdge - _selectionAnchor;

			if(totalChars > 0){
				int newRow = _hDoc.getRowIndex(_selectionAnchor);
				boolean isSingleRowSel = _hDoc.getRowIndex(_selectionEdge) == newRow;
				_hDoc.deleteAt(_selectionAnchor, totalChars, System.nanoTime());

				_caretPosition = _selectionAnchor;
				if(newRow != _caretRow){
					_caretRow = newRow;
					_rowLis.onRowChange(newRow);
				}
				setDirty(true);
				determineSpans();
				setSelectText(false);
				stopTextComposing();

				if(!makeCharVisible(_caretPosition)){
					if(isSingleRowSel){
						invalidateRows(newRow, newRow+1);
					}
					else{
						invalidateFromRow(newRow);
					}
				}
			}
			else{
				setSelectText(false);
				invalidateCaretRow();
			}
		}
		

	    //- TextFieldController -----------------------------------------------
	    //----------------- Helper methods for InputConnection ----------------
		 
		/**
		 * Deletes existing selected text, then deletes charCount number of 
		 * characters starting at from, and inserts text in its place.
		 * 
		 * Unlike paste or selectionDelete, does not signal the end of
		 * text composing to the IME.
		 */
		void replaceComposingText(int from, int charCount, String text){
			int startInvalidateRow = _caretRow;
			boolean invalidateSingleRow = true;
			boolean dirty = false;

			//delete selection
			if(_isInSelectionMode){
				int totalChars = _selectionEdge - _selectionAnchor;

				if(totalChars > 0){
					_hDoc.deleteAt(_selectionAnchor, totalChars, System.nanoTime());
					_caretPosition = _selectionAnchor;
					int newRow = determineCaretRow();
					if(newRow < startInvalidateRow){
						startInvalidateRow = newRow;
						invalidateSingleRow = false;
					}
					dirty = true;
				}

				setSelectText(false);
			}

			//delete requested chars
			if(charCount > 0){
				_hDoc.deleteAt(from, charCount, System.nanoTime());
				_caretPosition = from;
				int newRow = determineCaretRow();
				if(newRow < startInvalidateRow){
					startInvalidateRow = newRow;
					invalidateSingleRow = false;
				}
				dirty = true;
			}

			//insert
			if(text != null && text.length() > 0){
				_hDoc.insertBefore(text.toCharArray(), _caretPosition, System.nanoTime());
				_caretPosition += text.length();
				dirty = true;
			}
			
			if(dirty){
				setDirty(true);
				determineSpans();
			}

			int newRow = determineCaretRow();
			if(newRow != _caretRow){
				_caretRow = newRow;
				_rowLis.onRowChange(newRow);
				invalidateSingleRow = false;
			}

			if(!makeCharVisible(_caretPosition)){
				if(invalidateSingleRow){
					invalidateRows(newRow, newRow+1);
				}
				else{
					invalidateFromRow(startInvalidateRow);
				}
			}
		}
		
		/**
		 * Delete leftLength characters of text before the current caret
		 * position, and delete rightLength characters of text after the current 
		 * cursor position.
		 * 
		 * Unlike paste or selectionDelete, does not signal the end of
		 * text composing to the IME.
		 */
		void deleteAroundComposingText(int left, int right){
			int start = _caretPosition - left;
			if(start < 0){
				start = 0;
			}
			int end = _caretPosition + right;
			if(end > (_hDoc.docLength()-1)){ //don't include terminal EOF
				end = _hDoc.docLength()-1;
			}
			replaceComposingText(start, end-start, "");
		}

		String getTextAfterCursor(int maxLen){
			return new String(_hDoc.subSequence(_caretPosition, maxLen));
		}

		String getTextBeforeCursor(int maxLen){
			int start = _caretPosition - maxLen;
			if(start < 0){
				start = 0;
			}
			return new String(_hDoc.subSequence(start, _caretPosition-start));
		}
	}//end inner controller class





	//*********************************************************************
    //************************** InputConnection **************************
    //*********************************************************************
	/*
	 * Does not provide ExtractedText related methods
	 */
	private class TextFieldInputConnection extends BaseInputConnection{
		private boolean _isComposing = false;
		private int _composingCharCount = 0;

		public TextFieldInputConnection(FreeScrollingTextField v){
			super(v, true);
		}
		
		public void resetComposingState(){
			_composingCharCount = 0;
			_isComposing = false;
			_hDoc.endBatchEdit();
		}

		/**
		 * Only true when the InputConnection has not been used by the IME yet.
		 * Can be programatically cleared by resetComposingState()
		 */
		public boolean isComposingStarted() {
			return _isComposing;
		}

		@Override
		public boolean setComposingText(CharSequence text, int newCursorPosition){
			_isComposing = true;
			if(!_hDoc.isBatchEdit()){
				_hDoc.beginBatchEdit();
			}
			
			_fieldController.replaceComposingText(
					getCaretPosition() - _composingCharCount,
					_composingCharCount,
					text.toString());
			_composingCharCount = text.length();
			
			//TODO reduce invalidate calls
			if(newCursorPosition > 1){
				_fieldController.moveCaret(_caretPosition + newCursorPosition - 1);
			}
			else if (newCursorPosition <= 0){
				_fieldController.moveCaret(_caretPosition - text.length() - newCursorPosition);
			}
			return true;
		}
		
		@Override
		public boolean commitText(CharSequence text, int newCursorPosition) {
			_isComposing = true;
			_fieldController.replaceComposingText(
					getCaretPosition() - _composingCharCount,
					_composingCharCount,
					text.toString());
			_composingCharCount = 0;
			_hDoc.endBatchEdit();

			//TODO reduce invalidate calls
			if(newCursorPosition > 1){
				_fieldController.moveCaret(_caretPosition + newCursorPosition - 1);
			}
			else if (newCursorPosition <= 0){
				_fieldController.moveCaret(_caretPosition - text.length() - newCursorPosition);
			}
			return true;
		}


		@Override
		public boolean deleteSurroundingText(int leftLength, int rightLength){
			if(_composingCharCount != 0){
				Log.d(TextWarriorApplication.LOG_TAG,
						"Warning: Implmentation of InputConnection.deleteSurroundingText" +
						" will not skip composing text");
			}
			
			_fieldController.deleteAroundComposingText(leftLength, rightLength);
			return true;
		}

		@Override
		public boolean finishComposingText() {
			resetComposingState();
			return true;
		}

		@Override
		public int getCursorCapsMode(int reqModes) {
			int capsMode = 0;

			// Ignore InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; not used in TextWarrior
			
			if((reqModes & InputType.TYPE_TEXT_FLAG_CAP_WORDS)
					== InputType.TYPE_TEXT_FLAG_CAP_WORDS){
				int prevChar = _caretPosition - 1;
				if(prevChar < 0 || Lexer.getLanguage().isWhitespace(_hDoc.charAt(prevChar)) ){
					capsMode |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
					
					//set CAP_SENTENCES if client is interested in it
					if((reqModes & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
							== InputType.TYPE_TEXT_FLAG_CAP_SENTENCES){
						capsMode |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
					}
				}
			}
			
			// Strangely, Android soft keyboard does not set TYPE_TEXT_FLAG_CAP_SENTENCES
			// in reqModes even if it is interested in doing auto-capitalization.
			// Android bug? Therefore, we assume TYPE_TEXT_FLAG_CAP_SENTENCES
			// is always set to be on the safe side.
			else {
				LanguageCFamily lang = Lexer.getLanguage();

				int prevChar = _caretPosition - 1;
				int whitespaceCount = 0;
				boolean capsOn = true;

				// Turn on caps mode only for the first char of a sentence.
				// A fresh line is also considered to start a new sentence.
				// The position immediately after a period is considered lower-case.
				// Examples: "abc.com" but "abc. Com"
				while(prevChar >= 0){
					char c = _hDoc.charAt(prevChar);
					if(c == LanguageCFamily.NEWLINE){
						break;
					}
					
					if(!lang.isWhitespace(c)){
						if(whitespaceCount == 0 || !lang.isSentenceTerminator(c) ){
							capsOn = false;
						}
						break;
					}
					
					++whitespaceCount;
					--prevChar;
				}
				
				if(capsOn){
					capsMode |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
				}
			}
			
			return capsMode;
		}

		@Override
		public CharSequence getTextAfterCursor(int maxLen, int flags) {
			return _fieldController.getTextAfterCursor(maxLen); //ignore flags
		}

		@Override
		public CharSequence getTextBeforeCursor(int maxLen, int flags) {
			return _fieldController.getTextBeforeCursor(maxLen); //ignore flags
		}

		@Override
		public boolean setSelection(int start, int end) {
			if(start == end){
				_fieldController.moveCaret(start);
			}
			else{
				_fieldController.setSelectionRange(start, end-start, false);
			}
			return true;
		}
		
	}// end inner class


}
