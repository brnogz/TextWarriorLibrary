/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class ReadThread extends FileIOThread{
	final protected TextBuffer _buf;
	
	//TODO use DocumentProvider instead of TextBuffer
	public ReadThread(File file, TextBuffer buf,
			String encoding, String EOLchar){
		super(file, encoding, EOLchar);
		_buf = buf;
	}
	
	public void run(){
		_isDone = false;
		_abortFlag.clear();

		try{
			realRead();
		}
		catch(OutOfMemoryError e){
			broadcastError(ProgressSource.READ,
					ERROR_OUT_OF_MEMORY, _outOfMemoryMsg); //TODO localise message
			/* useful memory statistics
			long freeMem = Runtime.getRuntime().freeMemory();
			long max = 1000000 * ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryClass();
			long totalMem = Runtime.getRuntime().totalMemory();
			long maxMem = Runtime.getRuntime().maxMemory();
			*/
		}
		catch (IOException ex) {
			broadcastError(ProgressSource.READ,
					ERROR_UNKNOWN, ex.getLocalizedMessage());
	    }
	}

	private void realRead() throws IOException{
		FileInputStream fs = new FileInputStream(_file);
		
		try{
			detectEncodingAndEOL();
			char[] newBuffer = allocateBuffer();

			Pair statistics = _converter.readAndConvert(fs, newBuffer,
					_encoding, _EOLchar, _abortFlag);

            if(!_abortFlag.isSet()){
            	_buf.setBuffer(newBuffer,
            			_encoding,
            			_EOLchar,
            			statistics.getFirst(),
            			statistics.getSecond());
				_isDone = true;
            	broadcastComplete(ProgressSource.READ);
            }
            else{
            	broadcastCancel(ProgressSource.READ);
            }
		}
		finally{
			fs.close();
		}
	}

	// throws OutOfMemoryError if there is not enough memory or
	// total characters > Integer.MAX_VALUE
	private char[] allocateBuffer(){
		TextWarriorException.assertVerbose(
				!_encoding.equals(EncodingScheme.TEXT_ENCODING_AUTO),
				"AUTO encoding not yet resolved");
		TextWarriorException.assertVerbose(
				!_EOLchar.equals(EncodingScheme.LINE_BREAK_AUTO),
				"AUTO line break terminator not yet resolved");

		long textLength = _file.length();
		if (_encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE) ||
				_encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
			textLength >>>= 1; // 2 bytes in the file == 1 char
		}
		if(textLength > Integer.MAX_VALUE){
			throw new OutOfMemoryError();
		}

		int implSize = TextBuffer.memoryNeeded((int) textLength);
		if(implSize == -1){
			throw new OutOfMemoryError();
		}

		_totalChar = (int) textLength;
		return new char[implSize];
	}

	private void detectEncodingAndEOL() throws IOException{
		if (_encoding.equals(EncodingScheme.TEXT_ENCODING_AUTO)){
			_encoding = _converter.getEncodingScheme(_file);
		}
		if(_EOLchar.equals(EncodingScheme.LINE_BREAK_AUTO)){
			_EOLchar = _converter.getEOLType(_file, _encoding);
		}
	}

	@Override
	public int getMax(){
		return MAX_PROGRESS;
	}

	@Override
	public int getCurrent(){
		double progressProportion = (_totalChar == 0) ? 0 :
			(double) _converter.getProgress() / (double) _totalChar;
		return (int) (progressProportion * MAX_PROGRESS);
	}
	
	
	private int _totalChar = 0;
	/** reported progress will be scaled from 0 to MAX_PROGRESS */
	private final static int MAX_PROGRESS = 100;
	private static final String _outOfMemoryMsg = "Not enough memory";
}