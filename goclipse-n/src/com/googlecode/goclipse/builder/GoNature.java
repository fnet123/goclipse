package com.googlecode.goclipse.builder;

import melnorme.lang.ide.core.LangNature;

public class GoNature extends LangNature {
	
	@Override
	protected String getBuilderId() {
		return GoBuilder.BUILDER_ID;
	}
	
}