/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class PauseAction extends XDebuggerActionBase {
  @Override
  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getPauseHandler();
  }

  @Override
  protected boolean isHidden(AnActionEvent event) {
    if (!ApplicationManager.getApplication().isInternal() || !Registry.is("debugger.merge.pause.and.resume")) {
      return super.isHidden(event);
    }
    Project project = event.getProject();
    if (project == null) {
      return false;
    }
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null || session.isStopped()) {
      return false;
    }
    return super.isHidden(event) || session.isPaused();
  }
}
