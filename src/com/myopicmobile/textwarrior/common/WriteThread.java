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
import java.io.FileOutputStream;
import java.io.IOException;


public class WriteThread extends FileIOThread{
	private DocumentProvider _hDoc;
	// reported progress will be scaled from 0 to MAX_PROGRESS
	private final static int MAX_PROGRESS = 100;
	
	public WriteThread(File file, DocumentProvider hDoc,
	String encoding, String EOLchar){
		super(file, encoding, EOLchar);
		_hDoc = hDoc;
		_totalChar = _hDoc.docLength();
		TextWarriorException.assertVerbose(_totalChar > 0,
				 "File to save must have at least 1 char");
	}

	public void run(){
		_isDone = false;
		_abortFlag.clear();
		
		try{
			realWrite();
		}
		catch (IOException ex) {
			broadcastError(ProgressSource.WRITE,
				ERROR_UNKNOWN, ex.getLocalizedMessage());
	    }
	}

	private void realWrite() throws IOException{
		FileOutputStream fs = new FileOutputStream(_file);

		try{
			resolveAutoEncodingAndEOL();
	        _converter.writeAndConvert(fs, _hDoc, _encoding, _EOLchar, _abortFlag);

	        if(!_abortFlag.isSet()){
				_isDone = true;
            	broadcastComplete(ProgressSource.WRITE);
	        }
            else{
            	broadcastCancel(ProgressSource.WRITE);
            }
		}
		finally{
			fs.close();
		}
	}

	private void resolveAutoEncodingAndEOL() {
		if (_encoding.equals(EncodingScheme.TEXT_ENCODING_AUTO)){
			_encoding = _hDoc.getEncodingScheme();
		}
		if(_EOLchar.equals(EncodingScheme.LINE_BREAK_AUTO)){
			_EOLchar = _hDoc.getEOLType();
		}
	}

	@Override
	public int getMax(){
		return MAX_PROGRESS;
	}

	@Override
	public int getCurrent(){
		double progressProportion = (double) _converter.getProgress()
				/ (double) _totalChar;
		return (int) (progressProportion * MAX_PROGRESS);
	}
	
	private int _totalChar = 0;
}
