/*******************************************************************************
 * Copyright (c) 2015, 2015 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package LANG_PROJECT_ID.ide.ui.editor;

import java.util.Collections;
import java.util.List;

import melnorme.lang.ide.ui.text.completion.LangCompletionProposalComputer;
import melnorme.lang.ide.ui.text.completion.LangContentAssistInvocationContext;
import melnorme.lang.tooling.completion.CompletionSoftFailure;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

final class LANGUAGE_CompletionProposalComputer extends LangCompletionProposalComputer {
	@Override
	protected List<ICompletionProposal> doComputeCompletionProposals(LangContentAssistInvocationContext context,
			int offset) throws CoreException, CompletionSoftFailure {
		return Collections.EMPTY_LIST; // TODO: LANG
	}
}