/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common;

import java.util.Vector;

public class AnalyzeStatisticsThread extends Thread implements ProgressSource{
	protected Flag _abortFlag;
	protected boolean _isDone = false;
	protected Vector<ProgressObserver> _progressObservers;
	private DocumentProvider _hDoc;
	private int _start, _end;
	protected CharEncodingUtils.Statistics _results;
	protected CharEncodingUtils _analyzer = new CharEncodingUtils();
	
	public AnalyzeStatisticsThread(DocumentProvider hDoc,
			int start, int end){
		_hDoc = hDoc;
		_start = start;
		_end = end;
        _abortFlag = new Flag();
        _progressObservers = new Vector<ProgressObserver>();
	}
	
	public void run(){
		_isDone = false;
		_abortFlag.clear();
		_results = null;
		
		_results = _analyzer.analyze(_hDoc, _start, _end, _abortFlag);

		if(!_abortFlag.isSet()){
			_isDone = true;
			broadcastComplete(_results);
		}
		else{
			broadcastCancel();
		}
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

	synchronized protected void broadcastComplete(
			CharEncodingUtils.Statistics results){
		for(ProgressObserver po : _progressObservers){
			po.onComplete(ProgressSource.ANALYZE_TEXT, results);
		}
	}
	
	synchronized protected void broadcastCancel(){
		for(ProgressObserver po : _progressObservers){
			po.onCancel(ProgressSource.ANALYZE_TEXT);
		}
	}

	@Override
	public final int getMin(){
		return 0;
	}

	@Override
	public int getMax(){
		return Math.max(1, _end - _start);
	}

	@Override
	public int getCurrent(){
		return _analyzer.getProgress();
	}

	@Override
	public final boolean isDone(){
		return _isDone;
	}
	
	public final CharEncodingUtils.Statistics getResults(){
		return _results;
	}
}
