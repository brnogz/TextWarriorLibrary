/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package com.myopicmobile.textwarrior.common;

public class ColorScheme {
	/* In ARGB format: 0xAARRGGBB */
	public static final int foregroundColor = 0xFF000000; //BLACK
	public static final int backgroundColor = 0xFFFFFFFF; //WHITE
	
	public static final int selForegroundColor = 0xFFFFFFFF; //WHITE
	public static final int selBackgroundColor = 0xFF800000; //MAROON
	
	public static final int caretForegroundColor = 0xFFFFFFFF; //WHITE
	public static final int caretBackgroundColor = 0xFF0000FF; //BLUE
	public static final int caretDisabledColor = 0xFF808080; //GREY
	
	public static final int commentColor = 0xFF3F7F5F; // Eclipse default color
	public static final int keywordColor = 0xFF7F0055; // Eclipse default color
	public static final int literalColor = 0xFF2A00FF; // Eclipse default color
	public static final int secondaryColor = 0xFF8B0000; //DARKRED;
	
	// Currently, color scheme is tightly coupled with semantics of the token types
	public static int getTokenColor(int tokenType){
		int color;
		switch(tokenType){
		 case Lexer.NORMAL:
		     color = ColorScheme.foregroundColor;
			 break;
		 case Lexer.KEYWORD:
		     color = ColorScheme.keywordColor;
			 break;
		 case Lexer.DOUBLE_SYMBOL_LINE: //fall-through
		 case Lexer.DOUBLE_SYMBOL_DELIMITED_MULTILINE:
		 case Lexer.SINGLE_SYMBOL_LINE_B:
		     color = ColorScheme.commentColor;
			 break;
		 case Lexer.SINGLE_SYMBOL_DELIMITED_A: //fall-through
		 case Lexer.SINGLE_SYMBOL_DELIMITED_B:
		     color = ColorScheme.literalColor;
			 break;
		 case Lexer.SINGLE_SYMBOL_LINE_A: //fall-through
		 case Lexer.SINGLE_SYMBOL_WORD:
		     color = ColorScheme.secondaryColor;
			 break;
		 default:
			 TextWarriorException.assertVerbose(false,
			 	"Invalid token type");
		     color = ColorScheme.foregroundColor;
			 break;
		}
		return color;
	}
}
