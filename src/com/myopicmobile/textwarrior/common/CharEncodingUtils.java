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
import java.io.InputStream;
import java.io.OutputStream;


/**
 * 
 * Helper class to convert between different line terminator and encoding formats
 * and analyze a piece of text.
 * 
 * It is strongly recommended to use the native charset converters of the 
 * platform, if available, since the converters here do not handle errors well.
 * 
 * XXtoUTF16BE methods normalise all line terminator types to '\n' as a side-effect.
 * 
 * UTF16BEtoXX methods assume the line terminator type is '\n', add a BOM to the 
 * output stream as a side-effect and do not check the validity of the input UTF16 chars.
 * Malformed surrogate pairs and illegal Unicode values will be copied over as is.
 * 
 * UTF16toUTF16BE does not check the validity of the input chars.
 * Malformed surrogate pairs and illegal Unicode values will be copied over as is.
 * 
 * This class is not thread-safe. Only one thread should be accessing an object 
 * at a time.
 *
 */
// To implement multi-threaded access, please examine how _unitsDone is used
public class CharEncodingUtils {
	private int _unitsDone = 0;
	
	/**
	 * Returns the progress of the current operation. The units used depends on
	 * the type of operation.
	 * 
	 * Not synchronized; other threads calling this may get outdated values
	 * but it should be all right if only an approximate value is needed 
	 */
	public int getProgress(){
		return _unitsDone;
	}
	
	/**
	 * Returns the encoding scheme used in file, according to the byte-order mark.
	 * If there is no byte-order mark, TEXT_ENCODING_UTF8 is returned.
	 * 
	 * Therefore, encoding schemes such as ASCII and Latin-1 will be erroneously
	 * recognized as UTF-8.
	 * 
	 * @return One of TEXT_ENCODING_UTF8, TEXT_ENCODING_UTF16BE or 
	 * 			TEXT_ENCODING_UTF16LE
	 */
	public String getEncodingScheme(File file)
	throws IOException{
		byte[] byteOrderMark = {0, 0, 0};
		FileInputStream fs = new FileInputStream(file);
		
		try{
			for(int i = 0; i < 3; ++i){
				byteOrderMark[i] = (byte) fs.read();
			}
		}
		finally{
			fs.close();
			fs = null;
		}
		
		if (byteOrderMark[0] == (byte) 0xFE &&
			byteOrderMark[1] == (byte) 0xFF){
			return EncodingScheme.TEXT_ENCODING_UTF16BE;
		}
		else if (byteOrderMark[0] == (byte) 0xFF &&
				byteOrderMark[1] == (byte) 0xFE){
			return EncodingScheme.TEXT_ENCODING_UTF16LE;
		}
		/* Uncomment if UTF-8 should not be the default encoding
		else if (byteOrderMark[0] == (byte) 0xEF &&
				byteOrderMark[1] == (byte) 0xBB &&
				byteOrderMark[2] == (byte) 0xBF){
			return EncodingScheme.TEXT_ENCODING_UTF8;
		}
		*/
		else{
			//TODO more robust algorithm for differentiating between encoding schemes without BOM
			return EncodingScheme.TEXT_ENCODING_UTF8;
		}
	}
	
	/**
	 * Returns the line terminator style used in file.
	 * 
	 * @return One of LINE_BREAK_LF, LINE_BREAK_CR or LINE_BREAK_CRLF
	 */
	public String getEOLType(File file, String encoding)
	throws IOException{
		FileInputStream fs = new FileInputStream(file);
		String EOLType = null;
		int c;
		int prev = 0;
		
		try{
			if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE)){
				fs.read(); // discard upper byte
			}
			
			while (EOLType == null &&
			(c = fs.read()) != -1){
				if (c == '\n' && prev != '\r'){
					EOLType = EncodingScheme.LINE_BREAK_LF;
				}
				if (prev == '\r'){
					if(c == '\n'){
						EOLType = EncodingScheme.LINE_BREAK_CRLF;
					}
					else{
						EOLType = EncodingScheme.LINE_BREAK_CR;
					}
				}
	
				prev = c;
				if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE) ||
				encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
					fs.read(); // discard upper byte
				}
			}
		}
		finally{
			fs.close();
			fs = null;
		}

		if (EOLType == null){
			return EncodingScheme.LINE_BREAK_LF;
		}
		else{
			return EOLType;
		}
	}

	/**
	 * Skips the byte-order mark, if any, and returns the first byte after that.
	 * If there are no valid bytes, -1 is returned
	 */
	private int stripByteOrderMark(InputStream byteStream)
	throws IOException{
		int firstByte = byteStream.read();
		if (firstByte == 0xFE || firstByte == 0xFF){
			// UTF-16
			byteStream.read(); // discard 2nd BOM byte
			firstByte = byteStream.read();
			_unitsDone += 2;
		}
		else if (firstByte == 0xEF){
			// UTF-8
			byteStream.read(); // discard 2nd BOM byte
			byteStream.read(); // discard 3rd BOM byte
			firstByte = byteStream.read();
			_unitsDone += 3;
		}
		return firstByte;
	}
	
	public void writeByteOrderMark(OutputStream byteStream, String encoding)
	throws IOException{
		if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE)){
	    	byteStream.write(0xFE);
	    	byteStream.write(0xFF);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
	    	byteStream.write(0xFF);
	    	byteStream.write(0xFE);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF8)){
	    	byteStream.write(0xEF);
	    	byteStream.write(0xBB);
	    	byteStream.write(0xBF);
		}
	}
	
	/**
	 * Reads bytes from byteStream into buffer, converting to UTF-16BE encoding
	 * and normalising all line terminators to UNIX style '\n'
	 * 
	 * @param byteStream
	 * @param buffer
	 * @param encoding Encoding scheme of byteStream. Cannot be Auto! Call 
	 * 			getEncodingScheme() to find the exact type first
	 * @param EOLchar Line terminator style of byteStream
	 * @param abort Other threads can set this to abort the read operation
	 * @return Pair(size of converted text, number of lines)
	 */
	public Pair readAndConvert(InputStream byteStream,
	char[] buffer, String encoding, String EOLchar, Flag abort)
	throws IOException{
		_unitsDone = 0;

		if(encoding.equals(EncodingScheme.TEXT_ENCODING_LATIN1)){
			return Latin1toUTF16BE(byteStream, buffer, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE)){
			return UTF16toUTF16BE(byteStream, buffer, true, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
			return UTF16toUTF16BE(byteStream, buffer, false, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF8)){
			return UTF8toUTF16BE(byteStream, buffer, EOLchar, abort);
		}
		else{
			TextWarriorException.assertVerbose(false,
					"Unsupported encoding option" + encoding);
			return new Pair(0, 0);
		}
	}
	
	private Pair Latin1toUTF16BE(InputStream byteStream,
	char[] buffer, String EOLchar, Flag abort)
	throws IOException{
		int currCharRead;
		int lineCount = 1;
		int totalChar = 0;
	
		while((currCharRead = byteStream.read()) != -1 && !abort.isSet()){
			++_unitsDone;

			if (currCharRead == '\r'){
				if(EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
					//TODO assert there is a valid '/n' after this '/r'
					byteStream.read(); // discard the next char '\n'
					++_unitsDone;
				}
				currCharRead = '\n';
			}
			
			if (currCharRead == '\n'){
				++lineCount;
			}
			buffer[totalChar++] = (char) currCharRead;
		}

		return new Pair(totalChar, lineCount);
	}

	private Pair UTF16toUTF16BE(InputStream byteStream,
	char[] buffer, boolean isBigEndian, String EOLchar, Flag abort)
	throws IOException{
		int byte0, byte1;
		int lineCount = 1;
		int totalChar = 0;
		char currCharRead;

		byte0 = stripByteOrderMark(byteStream);
		while(byte0 != -1
		&& (byte1 = byteStream.read()) != -1
		&& !abort.isSet()){
			_unitsDone += 2;
			//TODO place conditional outside loop
			if(isBigEndian){
				currCharRead = (char) (byte1 | (byte0 << 8));
			}
			else{
				currCharRead = (char) (byte0 | (byte1 << 8));
			}
			
			if (currCharRead == '\r'){
				if(EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
					//TODO assert there is a valid '/n' after this '/r'
					byteStream.read(); // discard the next 2 bytes representing '\n'
					byteStream.read();
					++_unitsDone;
				}
				currCharRead = '\n';
			}
			
			if (currCharRead == '\n'){
				++lineCount;
			}
			
			buffer[totalChar++] = currCharRead;
			byte0 = byteStream.read();
		}

		return new Pair(totalChar, lineCount);
	}
	
	public void writeAndConvert(OutputStream byteStream,
	DocumentProvider hDoc, String encoding, String EOLchar, Flag abort)
	throws IOException{
		_unitsDone = 0;
		hDoc.seekChar(0);

		if(encoding.equals(EncodingScheme.TEXT_ENCODING_LATIN1)){
			UTF16BEtoLatin1(byteStream, hDoc, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16BE)){
			UTF16BEtoUTF16(byteStream, hDoc, true, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF16LE)){
			UTF16BEtoUTF16(byteStream, hDoc, false, EOLchar, abort);
		}
		else if(encoding.equals(EncodingScheme.TEXT_ENCODING_UTF8)){
			UTF16BEtoUTF8(byteStream, hDoc, EOLchar, abort);
		}
		else{
			TextWarriorException.assertVerbose(false,
					"Unsupported encoding option" + encoding);
		}
	}
	
	private void UTF16BEtoLatin1(OutputStream byteStream,
	DocumentProvider hDoc, String EOLchar, Flag abort)
	throws IOException{
		while(hDoc.hasNext() && !abort.isSet()){
			char curr = hDoc.next();
	    	++_unitsDone;
	    	
	    	if(curr == LanguageCFamily.EOF){
	    		break;
	    	}

			// convert '\n' to desired line terminator symbol
	    	if (curr == '\n' &&
	    	EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
		    	byteStream.write('\r');
	    	}
	    	else if (curr == '\n' &&
	    	EOLchar.equals(EncodingScheme.LINE_BREAK_CR)){
	    		curr = '\r';
	    	}
	    	byteStream.write(curr);
		}
	}
	
	private void UTF16BEtoUTF16(OutputStream byteStream,
	DocumentProvider hDoc, boolean isBigEndian, String EOLchar, Flag abort)
	throws IOException{
		String bom = isBigEndian ? EncodingScheme.TEXT_ENCODING_UTF16BE
				: EncodingScheme.TEXT_ENCODING_UTF16LE;
		writeByteOrderMark(byteStream, bom);

		while(hDoc.hasNext() && !abort.isSet()){
			char curr = hDoc.next();
	    	++_unitsDone;

	    	if(curr == LanguageCFamily.EOF){
	    		break;
	    	}

			// convert '\n' to desired line terminator symbol
	    	if (curr == '\n' &&
	    	EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
	    		if(isBigEndian){
		    		byteStream.write(0); byteStream.write('\r');
		    	}
	    		else{
		    		byteStream.write('\r'); byteStream.write(0);
	    		}
	    	}
	    	else if (curr == '\n' &&
	    	EOLchar.equals(EncodingScheme.LINE_BREAK_CR)){
	    		curr = '\r';
	    	}
			//TODO place conditional outside loop
	    	if(isBigEndian){
	    		byteStream.write(curr >>> 8);
	    		byteStream.write(curr & 0xFF);
	    	}
	    	else{
	    		byteStream.write(curr & 0xFF);
	    		byteStream.write(curr >>> 8);
	    	}
		}
	}
	
	
	/*
	 * The following UTF encoding form conversion methods were modified from
	 * an algorithm by Richard Gillam, pp. 543, Unicode Demystified, 2003
	 */
	
	// Lookup table to keep track of how many more bytes left to process to get 
	// a character. First index is the number of bytes of a UTF-8 character that
	// has been processed. Second index is the top 5 bits of the current byte.
	// Result of the lookup is the number of bytes left to process, or
	// -1 == illegal lead byte; -2 == illegal trailing byte
	private static final byte[][] states = {
		// 00 08 10 18 20 28 30 38 40 48 50 58 60 68 70 78
		// 80 88 90 98 A0 A8 B0 B8 C0 C8 D0 D8 E0 E8 F0 F8
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		-1, -1, -1, -1, -1, -1, -1, -1, 1, 1, 1, 1, 2, 2, 3, -1},
		{-2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2,
		 0, 0, 0, 0, 0, 0, 0, 0, -2, -2, -2, -2, -2, -2, -2, -2},
		{-2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2,
		 1, 1, 1, 1, 1, 1, 1, 1, -2, -2, -2, -2, -2, -2, -2, -2},
		{-2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2,
		 2, 2, 2, 2, 2, 2, 2, 2, -2, -2, -2, -2, -2, -2, -2, -2},
		};
	
	private static final byte[] masks = { 0x7F, 0x1F, 0x0F, 0x07 };

	
	private Pair UTF8toUTF16BE(InputStream byteStream,
	char[] buffer, String EOLchar, Flag abort)
	throws IOException{
		int currByte;
		int utf32Char = 0;
		int lineCount = 1;
		int totalChar = 0;
		int state = 0;
		byte mask = 0;

		currByte = stripByteOrderMark(byteStream);
		while(currByte != -1 && !abort.isSet()){
			++_unitsDone;
			state = states[state][currByte >>> 3];
			
			switch(state){
			case 0:
				utf32Char += currByte & 0x7F;
				
				if (utf32Char == '\r'){
					if(EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
						//TODO assert there is a valid '/n' after this '/r'
						byteStream.read(); // discard the next char '\n'
						++_unitsDone;
					}
					utf32Char = '\n';
				}

				if(utf32Char <= 0xFFFF){
					if (utf32Char == '\n'){
						++lineCount;
					}
					buffer[totalChar++] = (char) utf32Char;
				}
				else{
					// not in the BMP; split into surrogate pair
					buffer[totalChar++] = (char) ((utf32Char >> 10) + 0xD7C0);
					buffer[totalChar++] = (char) ((utf32Char & 0x03FF) + 0xDC00);
				}
				utf32Char = 0;
				mask = 0;
				break;
				
			case 1: // fall-through
			case 2: // fall-through
			case 3:
				if (mask == 0){
					mask = masks[state];
				}
				utf32Char += currByte & mask;
				utf32Char <<= 6;
				mask = (byte) 0x3F;
				break;
				
			case -2: // fall-through
			case -1:
				//TODO replace malformed sequence with the Unicode replacement char 0xFFFD
				/*
				buffer[totalChar++] = 0xDBBF;
				buffer[totalChar++] = 0xDFFD;
				*/
				// Since FFFD in UTF-16 requires a surrogate pair, and
				// TextWarrior cannot handle surrogate pairs yet, use '?' instead
				buffer[totalChar++] = '?';
				state = 0;
				utf32Char = 0;
				mask = 0;
				if(state == -2){
					// keep this byte for the next while iteration
					continue;
				}
				break;
			}
			currByte = byteStream.read();
		}

		return new Pair(totalChar, lineCount);
	}
	
	private void UTF16BEtoUTF8(OutputStream byteStream,
	DocumentProvider hDoc, String EOLchar, Flag abort)
	throws IOException{
		writeByteOrderMark(byteStream, EncodingScheme.TEXT_ENCODING_UTF8);
		while(hDoc.hasNext() && !abort.isSet()){
			int utf32Char = 0;
			char curr = hDoc.next();
	    	++_unitsDone;

	    	if(curr == LanguageCFamily.EOF){
	    		break;
	    	}
	    	
			if(curr < 0xD800 || curr > 0xDFFF){
				utf32Char = curr;
			}
			else{
				// combine surrogate pair to UTF-32 value
				utf32Char = (curr-0xD7C0) << 10;
				utf32Char += hDoc.next() & 0x03FF;
			}
	    	
			// Encode variable number of UTF-8 bytes depending on the UTF-32 value
		    if (utf32Char < 0x80){
		    	// convert '\n' to desired line terminator symbol
		    	if (utf32Char == '\n' &&
		    	EOLchar.equals(EncodingScheme.LINE_BREAK_CRLF)){
					byteStream.write('\r');
		    	}
		    	else if (utf32Char == '\n' &&
		    	EOLchar.equals(EncodingScheme.LINE_BREAK_CR)){
		    		utf32Char = '\r';
		    	}
		    	byteStream.write(utf32Char);
		    }
		    else if (utf32Char < 0x800){
		    	byteStream.write((utf32Char >> 6) + 0xC0);
		    	byteStream.write((utf32Char & 0x3F) + 0x80);
		    }
		    else if (utf32Char < 0x10000){
		    	byteStream.write((utf32Char >> 12) + 0xE0);
		    	byteStream.write(((utf32Char >> 6) & 0x3F) + 0x80);
		    	byteStream.write((utf32Char & 0x3F) + 0x80);
		    }
		    else{
		    	byteStream.write((utf32Char >> 18) + 0xF0);
		    	byteStream.write(((utf32Char >> 12) & 0x3F) + 0x80);
		    	byteStream.write(((utf32Char >> 6) & 0x3F) + 0x80);
		    	byteStream.write((utf32Char & 0x3F) + 0x80);
		    }
		}
	}

	/**
	 * Analyze word and character count from start to end-1 position in src
	 */
	public Statistics analyze(DocumentProvider src, int start, int end, Flag abort) {
		if(start < 0 || start >= src.docLength()){
			TextWarriorException.assertVerbose(false,
				"Invalid start index");
			return new Statistics(0, 0, 0, 0);
		}
		if(start > end){
			TextWarriorException.assertVerbose(false,
				"Start index cannot be greater than end index");
			return new Statistics(0, 0, 0, 0);
		}
		if(start == end){
			return new Statistics(0, 0, 0, 0);
		}
		
		int wordCount = 0;
		_unitsDone = 0;
		int whiteSpaceCount = 0;
		int lines = 1;
		LanguageCFamily charSet = Lexer.getLanguage();
		
		char firstChar = src.charAt(start);
		//whether the current char and possibly the ones before are whitespace
		boolean whiteSpaceRun = charSet.isWhitespace(firstChar);
		
		src.seekChar(start);
		while(src.hasNext() && _unitsDone < (end-start)
				&& !abort.isSet()){
			char c = src.next();
			++_unitsDone;

			if(c == '\n'){
				++lines;
			}
			
			if(charSet.isWhitespace(c)){
				++whiteSpaceCount;
				
				if(!whiteSpaceRun){
					whiteSpaceRun = true;
					++wordCount;
				}
			}
			else{
				whiteSpaceRun = false;
			}
		}
		
		if(!whiteSpaceRun){
			// the final word didn't end with whitespace
			++wordCount;
		}
		if(!src.hasNext() && _unitsDone > 0){
			// exclude the terminal EOF character
			--_unitsDone;
			--whiteSpaceCount;
		}
		
		return new Statistics(wordCount, _unitsDone, whiteSpaceCount, lines);
	}

	public static class Statistics{
		public int wordCount = 0;
		public int charCount = 0;
		public int whitespaceCount = 0;
		public int lineCount = 0;
		
		public Statistics(){
		}
		
		public Statistics(int words, int chars, int whitespaces, int lines){
			wordCount = words;
			charCount = chars;
			whitespaceCount = whitespaces;
			lineCount = lines;
		}
	}
}