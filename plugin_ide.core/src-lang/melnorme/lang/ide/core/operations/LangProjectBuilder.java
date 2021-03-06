/*******************************************************************************
 * Copyright (c) 2014 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package melnorme.lang.ide.core.operations;


import static melnorme.lang.ide.core.operations.build.BuildManagerMessages.MSG_Starting_LANG_Build;
import static melnorme.lang.ide.core.utils.TextMessageUtils.headerVeryBig;
import static melnorme.utilbox.core.Assert.AssertNamespace.assertNotNull;
import static melnorme.utilbox.core.Assert.AssertNamespace.assertTrue;

import java.text.MessageFormat;
import java.util.Map;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import melnorme.lang.ide.core.LangCore;
import melnorme.lang.ide.core.LangCore_Actual;
import melnorme.lang.ide.core.operations.ILangOperationsListener_Default.IToolOperationMonitor;
import melnorme.lang.ide.core.operations.build.BuildManager;
import melnorme.lang.ide.core.utils.EclipseUtils;
import melnorme.lang.ide.core.utils.ResourceUtils;
import melnorme.lang.tooling.common.ops.CommonOperation;
import melnorme.utilbox.collections.HashMap2;
import melnorme.utilbox.concurrency.OperationCancellation;
import melnorme.utilbox.core.CommonException;
import melnorme.utilbox.misc.Location;

public abstract class LangProjectBuilder extends IncrementalProjectBuilder {
	
	protected final BuildManager buildManager = LangCore.getBuildManager();
	
	public LangProjectBuilder() {
	}
	
	protected Location getProjectLocation() throws CommonException {
		return ResourceUtils.getProjectLocation2(getProject());
	}
	
	protected ToolManager getToolManager() {
		return LangCore.getToolManager();
	}
	
	/* ----------------- helpers ----------------- */
	
	protected void deleteProjectBuildMarkers() {
		try {
			getProject().deleteMarkers(LangCore_Actual.BUILD_PROBLEM_ID, true, IResource.DEPTH_INFINITE);
		} catch (CoreException ce) {
			LangCore.logStatus(ce);
		}
	}
	
	protected String getBuildProblemId() {
		return LangCore_Actual.BUILD_PROBLEM_ID;
	}
	
	protected boolean isFirstProjectOfKind() throws CoreException {
		for (IBuildConfiguration buildConfig : getContext().getAllReferencedBuildConfigs()) {
			if(buildConfig.getProject().hasNature(LangCore.NATURE_ID)) {
				return false;
			}
		}
		return true;
	}
	
	protected boolean isLastProjectOfKind() throws CoreException {
		for (IBuildConfiguration buildConfig : getContext().getAllReferencingBuildConfigs()) {
			if(buildConfig.getProject().hasNature(LangCore.NATURE_ID)) {
				return false;
			}
		}
		return true;
	}
	
	/* ----------------- Build ----------------- */
	
	@Override
	protected void startupOnInitialize() {
		assertTrue(getProject() != null);
	}
	
	protected static HashMap2<String, IToolOperationMonitor> workspaceOpMonitorMap = new HashMap2<>();
	protected IToolOperationMonitor workspaceOpMonitor;
	
	protected void prepareForBuild(IProgressMonitor pm) throws CoreException, OperationCancellation {
		handleBeginWorkspaceBuild(pm);
	}
	
	protected void handleBeginWorkspaceBuild(IProgressMonitor pm) throws CoreException, OperationCancellation {
		workspaceOpMonitor = workspaceOpMonitorMap.get(LangCore.NATURE_ID);
		
		if(workspaceOpMonitor != null) {
			return;
		}
		workspaceOpMonitor = getToolManager().startNewBuildOperation();
		workspaceOpMonitorMap.put(LangCore.NATURE_ID, workspaceOpMonitor);
		
		ResourceUtils.getWorkspace().addResourceChangeListener(new IResourceChangeListener() {
			@Override
			public void resourceChanged(IResourceChangeEvent event) {
				int type = event.getType();
				if(type == IResourceChangeEvent.POST_BUILD || type == IResourceChangeEvent.PRE_BUILD) {
					workspaceOpMonitor = null;
					workspaceOpMonitorMap.remove(LangCore.NATURE_ID);
					ResourceUtils.getWorkspace().removeResourceChangeListener(this);
				}
			}
		}, IResourceChangeEvent.POST_BUILD | IResourceChangeEvent.PRE_BUILD);
		
		workspaceOpMonitor.writeInfoMessage(
			headerVeryBig(MessageFormat.format(MSG_Starting_LANG_Build, LangCore_Actual.NAME_OF_LANGUAGE))
		);
		
		clearWorkspaceErrorMarkers(pm);
	}
	
	protected void clearWorkspaceErrorMarkers(IProgressMonitor pm) throws CoreException, OperationCancellation {
		clearErrorMarkers(getProject(), pm);
		
		for(IBuildConfiguration buildConfig : getContext().getAllReferencingBuildConfigs()) {
			clearErrorMarkers(buildConfig.getProject(), pm);
		}
	}
	
	protected void clearErrorMarkers(IProject project, IProgressMonitor pm) 
			throws CoreException, OperationCancellation {
		CommonOperation clearMarkersOp = buildManager.newProjectClearMarkersOperation(workspaceOpMonitor, project);
		EclipseUtils.execute_asCore(EclipseUtils.om(pm), clearMarkersOp);
	}
	
	protected void handleEndWorkspaceBuild2() {
		workspaceOpMonitor = null;
	}
	
	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
		assertTrue(kind != CLEAN_BUILD);
		
		IProject project = assertNotNull(getProject());
		
		try {
			prepareForBuild(monitor);
			
			return doBuild(project, kind, args, monitor);
		} 
		catch(OperationCancellation cancel) {
			forgetLastBuiltState();
			return null;
		} catch(CoreException ce) {
			forgetLastBuiltState();
			
			if(monitor.isCanceled()) {
				// This shouldn't usually happen, a OperationCancellation should have been thrown,
				// but sometimes its not wrapped correctly.
				return null;
			}
			LangCore.logStatus(ce);
			throw ce;
		}
		finally {
			getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
			if(isLastProjectOfKind()) {
				handleEndWorkspaceBuild2();
			}
		}
		
	}
	
	@SuppressWarnings("unused")
	protected IProject[] doBuild(final IProject project, int kind, Map<String, String> args, IProgressMonitor monitor)
			throws CoreException, OperationCancellation {
		
		if(kind == IncrementalProjectBuilder.AUTO_BUILD) {
			return null; // Ignore auto build
		}
		try {
			createBuildOp().execute(EclipseUtils.om(monitor));
		} catch (CommonException ce) {
			throw LangCore.createCoreException(ce);
		}
		return null;
	}
	
	protected CommonOperation createBuildOp() throws CommonException {
		return buildManager.newProjectBuildOperation(workspaceOpMonitor, getProject(), false, false);
	}
	
	/* ----------------- Clean ----------------- */
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		deleteProjectBuildMarkers();
		
		try {
			ProcessBuilder pb = createCleanPB();
			doClean(monitor, pb);
		} catch (OperationCancellation e) {
			// return
		} catch (CommonException ce) {
			throw LangCore.createCoreException(ce);
		}
	}
	
	protected abstract ProcessBuilder createCleanPB() throws CoreException, CommonException;
	
	protected void doClean(IProgressMonitor pm, ProcessBuilder pb) 
			throws CoreException, CommonException, OperationCancellation {
		getToolManager().newRunBuildToolOperation(pb, pm).runProcess();
	}
	
}