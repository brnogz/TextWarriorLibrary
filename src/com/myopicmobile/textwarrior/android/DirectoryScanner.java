/* 
 * Copyright (C) 2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author Tah Wei Hoon
 * @date 30 Dec 2010
 * 
 * - Removed Cupcake-specific methods, mWriteableOnly, mDirectoriesOnly, 
 * 		file/dir count, .nomedia and mime type handling.
 * - Display last modified date in each entry instead of size
 */

package com.myopicmobile.textwarrior.android;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openintents.filemanager.DirectoryContents;
import org.openintents.filemanager.IconifiedText;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.myopicmobile.textwarrior.android.R;

public class DirectoryScanner extends Thread {

	private static final String TAG = "OIFM_DirScanner";
	
	private File currentDirectory;
	boolean cancel;

	private String mSdCardPath;
	private Context context;
	private Handler handler;
	private long operationStartTime;
	
	// Update progress bar every n files
	static final private int PROGRESS_STEPS = 50;
    

	DirectoryScanner(File directory, Context context, Handler handler, String sdCardPath) {
		super("Directory Scanner");
		currentDirectory = directory;
		this.context = context;
		this.handler = handler;
		this.mSdCardPath = sdCardPath;
	}
	
	private void clearData() {
		// Remove all references so we don't delay the garbage collection.
		context = null;
		handler = null;
	}

	public void run() {
		Log.v(TAG, "Scanning directory " + currentDirectory);
		
		File[] files = currentDirectory.listFiles();

		int totalCount = 0;
		
		if (cancel) {
			Log.v(TAG, "Scan aborted");
			clearData();
			return;
		}
		
		if (files == null) {
			Log.v(TAG, "Returned null - inaccessible directory?");
			totalCount = 0;
		} else {
			totalCount = files.length;
		}
		
		operationStartTime = SystemClock.uptimeMillis();
		
		Log.v(TAG, "Counting files... (total count=" + totalCount + ")");

		int progress = 0;
		
		/** Dir separate for sorting */
		List<IconifiedText> listDir = new ArrayList<IconifiedText>(totalCount);

		/** Files separate for sorting */
		List<IconifiedText> listFile = new ArrayList<IconifiedText>(totalCount);

		/** SD card separate for sorting */
		List<IconifiedText> listSdCard = new ArrayList<IconifiedText>(3);

		// Cache some commonly used icons.
		Drawable sdIcon = context.getResources().getDrawable(R.drawable.icon_sdcard);
		Drawable folderIcon = context.getResources().getDrawable(R.drawable.ic_launcher_folder);
		Drawable genericFileIcon = context.getResources().getDrawable(R.drawable.icon_file);

		
		if (files != null) {
			for (File currentFile : files){ 
				if (cancel) {
					// Abort!
					Log.v(TAG, "Scan aborted while checking files");
					clearData();
					return;
				}

				progress++;
				updateProgress(progress, totalCount);		
				
				String modifiedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
										.format(currentFile.lastModified());
				if (currentFile.isDirectory()) { 
					if (currentFile.getAbsolutePath().equals(mSdCardPath)) {
						listSdCard.add(new IconifiedText( 
								currentFile.getName(), "", sdIcon)); 
					} else {
						listDir.add(new IconifiedText( 
									currentFile.getName(), modifiedDate, folderIcon));
					}
				}else{
					//is file
					listFile.add(new IconifiedText( 
						currentFile.getName(), modifiedDate, genericFileIcon));
				} 
			}
		}
		
		Log.v(TAG, "Sorting results...");
		
		Collections.sort(listDir); 
		Collections.sort(listFile); 

		if (!cancel) {
			Log.v(TAG, "Sending data back to main thread");
			
			DirectoryContents contents = new DirectoryContents();

			contents.setListDir(listDir);
			contents.setListFile(listFile);
			contents.setListSdCard(listSdCard);

			Message msg = handler.obtainMessage(FilePicker.MESSAGE_SHOW_DIRECTORY_CONTENTS);
			msg.obj = contents;
			msg.sendToTarget();
		}

		clearData();
	}
	
	private void updateProgress(int progress, int maxProgress) {
		// Only update the progress bar every n steps...
		if ((progress % PROGRESS_STEPS) == 0) {
			// Also don't update for the first second.
			long curTime = SystemClock.uptimeMillis();
			
			if (curTime - operationStartTime < 1000L) {
				return;
			}
			
			// Okay, send an update.
			Message msg = handler.obtainMessage(FilePicker.MESSAGE_SET_PROGRESS);
			msg.arg1 = progress;
			msg.arg2 = maxProgress;
			msg.sendToTarget();
		}
	}
}
