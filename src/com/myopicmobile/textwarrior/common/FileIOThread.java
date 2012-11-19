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
import java.util.Vector;


public abstract class FileIOThread extends Thread implements ProgressSource{
	final protected File _file;
	protected String _encoding;
	protected String _EOLchar;
	protected Flag _abortFlag;
	protected boolean _isDone = false;
	protected Vector<ProgressObserver> _progressObservers;
	protected CharEncodingUtils _converter = new CharEncodingUtils();

	public FileIOThread(File file, String encoding, String EOLchar){
		_file = file;
		_encoding = encoding;
		_EOLchar = EOLchar;
        _abortFlag = new Flag();
        _progressObservers = new Vector<ProgressObserver>();
	}

	@Override
	public final void forceStop(){
		if(!_abortFlag.isSet()){
			_abortFlag.set();
		}
	}

	@Override
	synchronized public final void registerObserver(ProgressObserver po){
		_progressObservers.addElement(po);
	}

	@Override
	synchronized public final void removeObservers(){
		_progressObservers.clear();
	}

	synchronized protected void broadcastComplete(int requestCode){
		for(ProgressObserver po : _progressObservers){
			po.onComplete(requestCode, null);
		}
	}

	synchronized protected void broadcastError(int requestCode,
			int errorCode, final String msg){
		for(ProgressObserver po : _progressObservers){
			po.onError(requestCode, errorCode, msg);
		}
	}
	
	synchronized protected void broadcastCancel(int requestCode){
		for(ProgressObserver po : _progressObservers){
			po.onCancel(requestCode);
		}
	}

	@Override
	public final int getMin(){
		return 0;
	}

	@Override
	public final boolean isDone(){
		return _isDone;
	}
}
