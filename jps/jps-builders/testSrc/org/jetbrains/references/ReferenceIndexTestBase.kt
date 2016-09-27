/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.references

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PathUtil
import com.sun.tools.javac.util.Convert
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.builders.TestProjectBuilderLogger
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex
import org.jetbrains.jps.backwardRefs.LightUsage
import org.jetbrains.jps.backwardRefs.BackwardReferenceIndexWriter
import java.io.File

abstract class ReferenceIndexTestBase : JpsBuildTestCase() {
  public override fun setUp() {
    super.setUp()
    System.setProperty(BackwardReferenceIndexWriter.PROP_KEY, true.toString())
  }

  public override fun tearDown() {
    super.tearDown()
    System.clearProperty(BackwardReferenceIndexWriter.PROP_KEY)
  }

  protected fun assertIndexOnRebuild(vararg files: String) {
    var representativeFile: String? = null
    for (file in files) {
      val addedFile = addFile(file)
      if (representativeFile == null) {
        representativeFile = addedFile
      }
    }
    addModule("m", PathUtil.getParentPath(representativeFile!!))
    rebuildAllModules()
    assertIndexEquals("initialIndex.txt")
  }

  protected fun renameFile(fileToRename: String, newName: String) {
    rename(orCreateProjectDir.path + "/m/" + fileToRename, newName)
  }

  protected fun changeFileContent(name: String, changesSourceFile: String) {
    changeFile("m/" + name, FileUtil.loadFile(File(testDataRootPath + "/" + getTestName(true) + "/" + changesSourceFile), CharsetToolkit.UTF8_CHARSET))
  }

  protected fun addFile(name: String): String {
    return createFile("m/" + name, FileUtil.loadFile(File(getTestDataPath() + name), CharsetToolkit.UTF8_CHARSET))
  }


  protected fun assertIndexEquals(expectedIndexDumpFile: String) {
    assertSameLinesWithFile(testDataRootPath + "/" + getTestName(true) + "/" + expectedIndexDumpFile, indexAsText())
  }

  protected fun indexAsText(): String {
    val pd = createProjectDescriptor(BuildLoggingManager(TestProjectBuilderLogger()))
    val manager = pd.dataManager
    val buildDir = manager.dataPaths.dataStorageRoot
    val index = CompilerBackwardReferenceIndex(buildDir)

    try {
      val fileEnumerator = index.filePathEnumerator
      val nameEnumerator = index.byteSeqEum

      val result = StringBuilder()
      result.append("Backward Hierarchy:\n")
      val hierarchyText = mutableListOf<String>()
      index.backwardHierarchyMap.forEachEntry { superClass, inheritors ->
        val superClassName = superClass.asName(nameEnumerator)
        val inheritorsText = mutableListOf<String>()
        inheritors.forEach { id ->
          inheritorsText.add(id.asName(nameEnumerator))
          true
        }
        inheritorsText.sort()
        hierarchyText.add(superClassName + " -> " + inheritorsText.joinToString(separator = " "))
        true
      }
      hierarchyText.sort()
      result.append(hierarchyText.joinToString(separator = "\n"))

      result.append("\n\nBackward References:\n")
      val referencesText = mutableListOf<String>()
      index.backwardReferenceMap.forEachEntry { usage, files ->
        val referents = mutableListOf<String>()
        files.forEach { id ->
          val file = File(fileEnumerator.valueOf(id))
          val fileName = FileUtil.getNameWithoutExtension(file)
          referents.add(fileName)
        }
        referents.sort()
        referencesText.add(usage.asText(nameEnumerator) + " in " + referents.joinToString(separator = " "))
        true
      }
      referencesText.sort()
      result.append(referencesText.joinToString(separator = "\n"))

      return result.toString()
    } finally {
      index.close()
    }
  }

  private fun getTestDataPath() = testDataRootPath + "/" + getTestName(true) + "/"

  fun Int.asName(byteArrayEnumerator: ByteArrayEnumerator): String = Convert.utf2string(
      byteArrayEnumerator.valueOf(this))

  fun LightUsage.asText(byteArrayEnumerator: ByteArrayEnumerator): String =
      when (this) {
        is LightUsage.LightMethodUsage -> this.owner.asName(byteArrayEnumerator) + "." + this.name.asName(
            byteArrayEnumerator) + "(" + this.parameterCount + ")"
        is LightUsage.LightFieldUsage -> this.owner.asName(byteArrayEnumerator) + "." + this.name.asName(byteArrayEnumerator)
        is LightUsage.LightClassUsage -> this.owner.asName(byteArrayEnumerator)
        is LightUsage.LightFunExprUsage -> "fun_expr(" + this.owner.asName(byteArrayEnumerator) + " at " + this.offset + ")";
        else -> throw UnsupportedOperationException()
      }
}