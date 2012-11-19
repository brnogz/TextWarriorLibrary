/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common;

import java.util.List;

/**
 * Iterator class to access characters of the underlying text buffer.
 * 
 * The usage procedure is as follows:
 * 1. Call seekLine(lineNumber) to mark the position to start iterating
 * 2. Call hasNext() to see if there are any more char
 * 3. Call next() to get the next char
 *
 * If there is more than 1 DocumentProvider pointing to the same Document,
 * changes made by one DocumentProvider will not cause other DocumentProviders
 * to be notified. Implement a publish/subscribe interface if required.
 */
public class DocumentProvider {
	/** Current position in the text. Range [ 0, _theText.getTextLength() ) */
	private int _currIndex;
	private TextBuffer _theText;
	
	public DocumentProvider(){
		_currIndex = 0;
		_theText = new TextBuffer();
	}
	
	public DocumentProvider(TextBuffer buf){
		_currIndex = 0;
		_theText = buf;
	}

	public DocumentProvider(DocumentProvider rhs){
		_currIndex = 0;
		_theText = rhs._theText;
	}
	
	/**
	 * Get a substring of up to maxChars length, starting from charOffset
	 */
	public char[] subSequence(int charOffset, int maxChars){
		return _theText.subSequence(charOffset, maxChars);
	}
	
	public char charAt(int charOffset){
		if(_theText.isValid(charOffset)){
			return _theText.charAt(charOffset);
		}
		else{
			return LanguageCFamily.NULL_CHAR;
		}
	}
	
	/**
	 * Get the row number that charOffset is on
	 */
	public int getRowIndex(int charOffset){
		return _theText.getLineIndex(charOffset);
	}
	
	public int getStartCharOfRow(int rowIndex){
		return _theText.getCharOffset(rowIndex);
	}
	
	/**
	 * Sets the iterator to point at the first character of startingLine.
	 * 
	 * If the line does not exist, hasNext() will return false, and _currIndex 
	 * will be set to -1.
	 * 
	 * @return The index of the first character on startingLine, or -1 
	 * 			if startingLine does not exist
	 */
	public int seekLine(int startingLine){
		_currIndex = _theText.getCharOffset(startingLine);
		return _currIndex;
	}
	
	/**
	 * Sets the iterator to point at startingChar.
	 * 
	 * If startingChar is invalid, hasNext() will return false, and _currIndex 
	 * will be set to -1.
	 */
	public int seekChar(int startingChar){
		if(_theText.isValid(startingChar)){
			_currIndex = startingChar;
		}
		else{
			_currIndex = -1;
		}
		return _currIndex;
	}
	
	public boolean hasNext(){
		return (_currIndex >= 0 &&
				_currIndex < _theText.getTextLength());
	}
	
	/**
	 * Returns the next character and moves the iterator forward.
	 * 
	 * Does not do bounds-checking. It is the responsibility of the caller
	 * to check hasNext() first.
	 * 
	 * @return Next character
	 */
	public char next(){
		char nextChar = _theText.charAt(_currIndex);
		++_currIndex;
		return nextChar;
	}
	
	/**
	 * Inserts c into the document, shifting existing characters from 
	 * insertionPoint (inclusive) to the right
	 */
	public void insertBefore(char c, int insertionPoint, long timestamp){
		char[] a = new char[1];
		a[0] = c;
		_theText.insert(a, insertionPoint, timestamp);
	}

	/**
	 * Inserts characters of cArray into the document, shifting existing
	 * characters from insertionPoint (inclusive) to the right
	 */
	public void insertBefore(char[] cArray, int insertionPoint, long timestamp){
		_theText.insert(cArray, insertionPoint, timestamp);
	}

	/**
	 * Deletes the character at deletionPoint index.
	 */
	public void deleteAt(int deletionPoint, long timestamp){
		_theText.delete(deletionPoint, 1, timestamp);
	}
	

	/**
	 * Deletes up to maxChars number of characters starting from deletionPoint
	 */
	public void deleteAt(int deletionPoint, int maxChars, long time){
		_theText.delete(deletionPoint, maxChars, time);
	}

	/**
	 * Returns true if the underlying text buffer is in batch edit mode
	 */
	public boolean isBatchEdit(){
		return _theText.isBatchEdit();
	}
	
	/**
	 * Signals the beginning of a series of insert/delete operations that can be 
	 * undone/redone as a single unit
	 */
	public void beginBatchEdit(){
		_theText.beginBatchEdit();
	}
	
	/**
	 * Signals the end of a series of insert/delete operations that can be 
	 * undone/redone as a single unit
	 */
	public void endBatchEdit(){
		_theText.endBatchEdit();
	}
	
	/**
	 * Returns the number of characters in the row specified by rowNumber
	 */
	public int rowLength(int rowNumber){
		return _theText.getLineLength(rowNumber);
	}
	
	/**
	 * Returns the number of characters in the document, including the terminal
	 * End-Of-File character
	 */
	public int docLength(){
		return _theText.getTextLength();
	}
	
	/**
	 * Returns the number of rows in the document
	 */
	public int rowCount(){
		return _theText.getLineCount();
	}

	/**
	 * Returns the character encoding scheme used by the document
	 */
	public String getEncodingScheme() {
		return _theText.getEncodingScheme();
	}
	
	/**
	 * Returns the line terminator style used by the document
	 */
	public String getEOLType() {
		return _theText.getEOLType();
	}
	
	//TODO make thread-safe
	/**
	 * Removes spans from the document.
	 * Beware: Not thread-safe! Another thread may be modifying the same spans
	 * returned from getSpans()
	 */
	public void clearSpans(){
		_theText.clearSpans();
	}
	
	/**
	 * Beware: Not thread-safe!
	 */
	public List<Pair> getSpans(){
		return _theText.getSpans();
	}

	/**
	 * Sets the spans to use in the document.
	 * Spans are continuous sequences of characters that have the same format 
	 * like color, font, etc.
	 * 
	 * @param spans A collection of Pairs, where Pair.first is the start 
	 * 		position of the token, and Pair.second is the type of the token.
	 */
	public void setSpans(List<Pair> spans){
		_theText.setSpans(spans);
	}
	
	public boolean canUndo() {
		return _theText.canUndo();
	}
	
	public boolean canRedo() {
		return _theText.canRedo();
	}
	
	public int undo() {
		return _theText.undo();
	}
	
	public int redo() {
		return _theText.redo();
	}
}
