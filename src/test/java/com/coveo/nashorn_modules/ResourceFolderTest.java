package com.coveo.nashorn_modules;

import org.junit.Test;

import javax.script.ScriptEngineManager;

import jdk.nashorn.api.scripting.NashornScriptEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ResourceFolderTest {
  private ResourceFolder root =
      ResourceFolder.create(
          getClass().getClassLoader(), "com/coveo/nashorn_modules/test1", "UTF-8");

  @Test
  public void rootFolderHasTheExpectedProperties() {
    assertEquals("/", root.getPath());
    assertNull(root.getParent());
  }

  @Test
  public void getFileReturnsTheContentOfTheFileWhenItExists() {
    assertTrue(root.getFile("foo.js").contains("foo"));
  }

  @Test
  public void getFileReturnsNullWhenFileDoesNotExists() {
    assertNull(root.getFile("invalid"));
  }

  @Test
  public void getFolderReturnsAnObjectWithTheExpectedProperties() {
    Folder sub = root.getFolder("subdir");
    assertEquals("/subdir/", sub.getPath());
    assertSame(root, sub.getParent());
    Folder subsub = sub.getFolder("subsubdir");
    assertEquals("/subdir/subsubdir/", subsub.getPath());
    assertSame(sub, subsub.getParent());
  }

  @Test
  public void getFolderNeverReturnsNullBecauseItCannot() {
    assertNotNull(root.getFolder("subdir"));
    assertNotNull(root.getFolder("invalid"));
  }

  @Test
  public void getFileCanBeUsedOnSubFolderIfFileExist() {
    assertTrue(root.getFolder("subdir").getFile("bar.js").contains("bar"));
  }

  @Test
  public void resourceFolderWorksWhenUsedForReal() throws Throwable {
    NashornScriptEngine engine =
        (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    Require.enable(engine, root);
    assertEquals("spam", engine.eval("require('./foo').bar.spam.spam"));
  }
}
