package com.coveo.nashorn_modules;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.util.ArrayList;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModuleTest {
  @Mock Folder root;
  @Mock Folder rootnm;
  @Mock Folder sub1;
  @Mock Folder sub1nm;
  @Mock Folder sub1sub1;
  @Mock Folder nmsub1;

  NashornScriptEngine engine;
  Module require;

  @Before
  public void before() throws Throwable {
    when(root.getPath()).thenReturn("/");
    when(root.getFolder("node_modules")).thenReturn(rootnm);
    when(root.getFolder("sub1")).thenReturn(sub1);
    when(root.getFile("file1.js")).thenReturn("exports.file1 = 'file1';");
    when(root.getFile("file2.json")).thenReturn("{ \"file2\": \"file2\" }");
    when(rootnm.getPath()).thenReturn("/node_modules/");
    when(rootnm.getParent()).thenReturn(root);
    when(rootnm.getFile("nmfile1.js")).thenReturn("exports.nmfile1 = 'nmfile1';");
    when(rootnm.getFolder("nmsub1")).thenReturn(nmsub1);
    when(nmsub1.getFile("nmsub1file1.js")).thenReturn("exports.nmsub1file1 = 'nmsub1file1';");
    when(nmsub1.getParent()).thenReturn(rootnm);
    when(sub1.getPath()).thenReturn("/sub1/");
    when(sub1.getParent()).thenReturn(root);
    when(sub1.getFolder("sub1")).thenReturn(sub1sub1);
    when(sub1.getFolder("node_modules")).thenReturn(sub1nm);
    when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub1file1 = 'sub1file1';");
    when(sub1nm.getPath()).thenReturn("/sub1/node_modules/");
    when(sub1nm.getParent()).thenReturn(sub1);
    when(sub1nm.getFile("sub1nmfile1.js")).thenReturn("exports.sub1nmfile1 = 'sub1nmfile1';");
    when(sub1sub1.getPath()).thenReturn("/sub1/sub1/");
    when(sub1sub1.getParent()).thenReturn(sub1);
    when(sub1sub1.getFile("sub1sub1file1.js"))
        .thenReturn("exports.sub1sub1file1 = 'sub1sub1file1';");

    engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    require = Require.enable(engine, root);
  }

  @Test
  public void itCanLoadSimpleModules() throws Throwable {
    assertEquals("file1", ((Bindings) require.require("./file1.js")).get("file1"));
  }

  @Test
  public void itCanEnableRequireInDifferentBindingsOnTheSameEngine() throws Throwable {
    NashornScriptEngine engine =
        (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
    Bindings bindings1 = new SimpleBindings();
    Bindings bindings2 = new SimpleBindings();

    Require.enable(engine, root, bindings1);

    assertNull(engine.getBindings(ScriptContext.ENGINE_SCOPE).get("require"));
    assertNotNull(bindings1.get("require"));
    assertNull(bindings2.get("require"));
    assertEquals("file1", ((Bindings) engine.eval("require('./file1')", bindings1)).get("file1"));

    try {
      engine.eval("require('./file1')", bindings2);
      fail();
    } catch (ScriptException ignored) {
    }

    Require.enable(engine, root, bindings2);
    assertNull(engine.getBindings(ScriptContext.ENGINE_SCOPE).get("require"));
    assertNotNull(bindings2.get("require"));
    assertEquals("file1", ((Bindings) engine.eval("require('./file1')", bindings2)).get("file1"));
  }

  @Test
  public void itCanLoadSimpleJsonModules() throws Throwable {
    assertEquals("file2", ((Bindings) require.require("./file2.json")).get("file2"));
  }

  @Test
  public void itCanLoadModulesFromSubFolders() throws Throwable {
    assertEquals("sub1file1", ((Bindings) require.require("./sub1/sub1file1.js")).get("sub1file1"));
  }

  @Test
  public void itCanLoadModulesFromSubFoldersInNodeModules() throws Throwable {
    assertEquals(
        "nmsub1file1", ((Bindings) require.require("nmsub1/nmsub1file1.js")).get("nmsub1file1"));
  }

  @Test
  public void itCanLoadModulesFromSubSubFolders() throws Throwable {
    assertEquals(
        "sub1sub1file1",
        ((Bindings) require.require("./sub1/sub1/sub1sub1file1.js")).get("sub1sub1file1"));
  }

  @Test
  public void itCanLoadModulesFromParentFolders() throws Throwable {
    when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub1file1 = require('../file1').file1;");
    assertEquals("file1", ((Bindings) require.require("./sub1/sub1file1.js")).get("sub1file1"));
  }

  @Test
  public void itCanGoUpAndDownInFolders() throws Throwable {
    when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub1file1 = require('../file1').file1;");
    assertEquals(
        "file1", ((Bindings) require.require("./sub1/../sub1/sub1file1.js")).get("sub1file1"));
  }

  @Test
  public void itCanGoUpAndDownInNodeModulesFolders() throws Throwable {
    assertEquals(
        "nmsub1file1",
        ((Bindings) require.require("nmsub1/../nmsub1/nmsub1file1.js")).get("nmsub1file1"));
  }

  @Test
  public void itCanLoadModulesSpecifyingOnlyTheFolderWhenPackageJsonHasAMainFile()
      throws Throwable {
    Folder dir = mock(Folder.class);
    when(dir.getFile("package.json")).thenReturn("{ \"main\": \"foo.js\" }");
    when(dir.getFile("foo.js")).thenReturn("exports.foo = 'foo';");
    when(root.getFolder("dir")).thenReturn(dir);
    assertEquals("foo", ((Bindings) require.require("./dir")).get("foo"));
  }

  @Test
  public void
      itCanLoadModulesSpecifyingOnlyTheFolderWhenPackageJsonHasAMainFilePointingToAFileInSubDirectory()
          throws Throwable {
    Folder dir = mock(Folder.class);
    Folder lib = mock(Folder.class);
    when(dir.getFile("package.json")).thenReturn("{ \"main\": \"lib/foo.js\" }");
    when(dir.getFolder("lib")).thenReturn(lib);
    when(lib.getFile("foo.js")).thenReturn("exports.foo = 'foo';");
    when(root.getFolder("dir")).thenReturn(dir);
    assertEquals("foo", ((Bindings) require.require("./dir")).get("foo"));
  }

  @Test
  public void
      itCanLoadModulesSpecifyingOnlyTheFolderWhenPackageJsonHasAMainFilePointingToASubDirectory()
          throws Throwable {
    Folder dir = mock(Folder.class);
    Folder lib = mock(Folder.class);
    when(root.getFolder("dir")).thenReturn(dir);
    when(dir.getFolder("lib")).thenReturn(lib);
    when(dir.getFile("package.json")).thenReturn("{\"main\": \"./lib\"}");
    when(lib.getFile("index.js")).thenReturn("exports.foo = 'foo';");
    assertEquals("foo", ((Bindings) require.require("./dir")).get("foo"));
  }

  @Test
  public void
      itCanLoadModulesSpecifyingOnlyTheFolderWhenPackageJsonHasAMainFilePointingToAFileInSubDirectoryReferencingOtherFilesInThisDirectory()
          throws Throwable {
    Folder dir = mock(Folder.class);
    Folder lib = mock(Folder.class);
    when(dir.getFile("package.json")).thenReturn("{ \"main\": \"lib/foo.js\" }");
    when(dir.getFolder("lib")).thenReturn(lib);
    when(lib.getFile("foo.js")).thenReturn("exports.bar = require('./bar');");
    when(lib.getFile("bar.js")).thenReturn("exports.bar = 'bar';");
    when(root.getFolder("dir")).thenReturn(dir);
    assertEquals("bar", ((Bindings) ((Bindings) require.require("./dir")).get("bar")).get("bar"));
  }

  @Test
  public void itCanLoadModulesSpecifyingOnlyTheFolderWhenIndexJsIsPresent() throws Throwable {
    Folder dir = mock(Folder.class);
    when(dir.getFile("index.js")).thenReturn("exports.foo = 'foo';");
    when(root.getFolder("dir")).thenReturn(dir);
    assertEquals("foo", ((Bindings) require.require("./dir")).get("foo"));
  }

  @Test
  public void itCanLoadModulesSpecifyingOnlyTheFolderWhenIndexJsIsPresentEvenIfPackageJsonExists()
      throws Throwable {
    Folder dir = mock(Folder.class);
    when(dir.getFile("package.json")).thenReturn("{ }");
    when(dir.getFile("index.js")).thenReturn("exports.foo = 'foo';");
    when(root.getFolder("dir")).thenReturn(dir);
    assertEquals("foo", ((Bindings) require.require("./dir")).get("foo"));
  }

  @Test
  public void itUsesNodeModulesOnlyForNonPrefixedNames() throws Throwable {
    assertEquals("nmfile1", ((Bindings) require.require("nmfile1")).get("nmfile1"));
  }

  @Test
  public void itFallbacksToNodeModulesWhenUsingPrefixedName() throws Throwable {
    assertEquals("nmfile1", ((Bindings) require.require("./nmfile1")).get("nmfile1"));
  }

  @Test(expected = NashornException.class)
  public void itDoesNotUseModulesOutsideOfNodeModulesForNonPrefixedNames() throws Throwable {
    require.require("file1.js");
  }

  @Test
  public void itUsesNodeModulesFromSubFolderForSubRequiresFromModuleInSubFolder() throws Throwable {
    when(sub1.getFile("sub1file1.js"))
        .thenReturn("exports.sub1nmfile1 = require('sub1nmfile1').sub1nmfile1;");
    assertEquals(
        "sub1nmfile1", ((Bindings) require.require("./sub1/sub1file1")).get("sub1nmfile1"));
  }

  @Test
  public void itLooksAtParentFoldersWhenTryingToResolveFromNodeModules() throws Throwable {
    when(sub1.getFile("sub1file1.js")).thenReturn("exports.nmfile1 = require('nmfile1').nmfile1;");
    assertEquals("nmfile1", ((Bindings) require.require("./sub1/sub1file1")).get("nmfile1"));
  }

  @Test
  public void itCanUseDotToReferenceToTheCurrentFolder() throws Throwable {
    assertEquals("file1", ((Bindings) require.require("./file1.js")).get("file1"));
  }

  @Test
  public void itCanUseDotAndDoubleDotsToGoBackAndForward() throws Throwable {
    assertEquals(
        "file1", ((Bindings) require.require("./sub1/.././sub1/../file1.js")).get("file1"));
  }

  @Test
  public void thePathOfModulesContainsNoDots() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("exports.path = module.filename");
    assertEquals(
        "/file1.js", ((Bindings) require.require("./sub1/.././sub1/../file1.js")).get("path"));
  }

  @Test
  public void itCanLoadModuleIfTheExtensionIsOmitted() throws Throwable {
    assertEquals("file1", ((Bindings) require.require("./file1")).get("file1"));
  }

  @Test(expected = NashornException.class)
  public void itThrowsAnExceptionIfFileDoesNotExists() throws Throwable {
    require.require("./invalid");
  }

  @Test(expected = NashornException.class)
  public void itThrowsAnExceptionIfSubFileDoesNotExists() throws Throwable {
    require.require("./sub1/invalid");
  }

  @Test(expected = NashornException.class)
  public void itThrowsEnExceptionIfFolderDoesNotExists() throws Throwable {
    require.require("./invalid/file1.js");
  }

  @Test(expected = NashornException.class)
  public void itThrowsEnExceptionIfSubFolderDoesNotExists() throws Throwable {
    require.require("./sub1/invalid/file1.js");
  }

  @Test(expected = NashornException.class)
  public void itThrowsAnExceptionIfTryingToGoAboveTheTopLevelFolder() throws Throwable {
    // We need two ".." because otherwise the resolving attempts to load from "node_modules" and
    // ".." validly points to the root folder there.
    require.require("../../file1.js");
  }

  @Test
  public void theExceptionThrownForAnUnknownFileCanBeCaughtInJavaScriptAndHasTheProperCode()
      throws Throwable {
    String code =
        (String)
            engine.eval(
                "(function() { try { require('./invalid'); } catch (ex) { return ex.code; } })();");
    assertEquals("MODULE_NOT_FOUND", code);
  }

  @Test
  public void rootModulesExposeTheExpectedFields() throws Throwable {
    Bindings module = (Bindings) engine.eval("module");
    Bindings exports = (Bindings) engine.eval("exports");
    Bindings main = (Bindings) engine.eval("require.main");

    assertEquals(exports, module.get("exports"));
    assertEquals(new ArrayList(), module.get("children"));
    assertEquals("<main>", module.get("filename"));
    assertEquals("<main>", module.get("id"));
    assertEquals(true, module.get("loaded"));
    assertEquals(null, module.get("parent"));
    assertNotNull(exports);
    assertEquals(module, main);
  }

  @Test
  public void topLevelModulesExposeTheExpectedFields() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn(
            "exports._module = module; exports._exports = exports; exports._main = require.main; exports._filename = __filename; exports._dirname = __dirname;");

    Bindings top = (Bindings) engine.eval("module");
    Bindings module = (Bindings) engine.eval("require('./file1')._module");
    Bindings exports = (Bindings) engine.eval("require('./file1')._exports");
    Bindings main = (Bindings) engine.eval("require('./file1')._main");

    assertEquals(exports, module.get("exports"));
    assertEquals(new ArrayList(), module.get("children"));
    assertEquals("/file1.js", module.get("filename"));
    assertEquals("/file1.js", module.get("id"));
    assertEquals(true, module.get("loaded"));
    assertEquals(top, module.get("parent"));
    assertNotNull(exports);
    assertEquals(top, main);

    assertEquals("file1.js", exports.get("_filename"));
    assertEquals("", exports.get("_dirname"));
  }

  @Test
  public void subModulesExposeTheExpectedFields() throws Throwable {
    when(sub1.getFile("sub1file1.js"))
        .thenReturn(
            "exports._module = module; exports._exports = exports; exports._main = require.main; exports._filename = __filename; exports._dirname = __dirname");

    Bindings top = (Bindings) engine.eval("module");
    Bindings module = (Bindings) engine.eval("require('./sub1/sub1file1')._module");
    Bindings exports = (Bindings) engine.eval("require('./sub1/sub1file1')._exports");
    Bindings main = (Bindings) engine.eval("require('./sub1/sub1file1')._main");

    assertEquals(exports, module.get("exports"));
    assertEquals(new ArrayList(), module.get("children"));
    assertEquals("/sub1/sub1file1.js", module.get("filename"));
    assertEquals("/sub1/sub1file1.js", module.get("id"));
    assertEquals(true, module.get("loaded"));
    assertEquals(top, module.get("parent"));
    assertNotNull(exports);
    assertEquals(top, main);

    assertEquals("sub1file1.js", exports.get("_filename"));
    assertEquals("/sub1", exports.get("_dirname"));
  }

  @Test
  public void subSubModulesExposeTheExpectedFields() throws Throwable {
    when(sub1sub1.getFile("sub1sub1file1.js"))
        .thenReturn(
            "exports._module = module; exports._exports = exports; exports._main = require.main;");

    Bindings top = (Bindings) engine.eval("module");
    Bindings module = (Bindings) engine.eval("require('./sub1/sub1/sub1sub1file1')._module");
    Bindings exports = (Bindings) engine.eval("require('./sub1/sub1/sub1sub1file1')._exports");
    Bindings main = (Bindings) engine.eval("require('./sub1/sub1/sub1sub1file1')._main");

    assertEquals(exports, module.get("exports"));
    assertEquals(new ArrayList(), module.get("children"));
    assertEquals("/sub1/sub1/sub1sub1file1.js", module.get("filename"));
    assertEquals("/sub1/sub1/sub1sub1file1.js", module.get("id"));
    assertEquals(true, module.get("loaded"));
    assertEquals(top, module.get("parent"));
    assertNotNull(exports);
    assertEquals(top, main);
  }

  @Test
  public void requireInRequiredModuleYieldExpectedParentAndChildren() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn("exports._module = module; exports.sub = require('./sub1/sub1file1');");
    when(sub1.getFile("sub1file1.js")).thenReturn("exports._module = module;");

    Bindings top = (Bindings) engine.eval("module");
    Bindings module = (Bindings) engine.eval("require('./file1')._module");
    Bindings subModule = (Bindings) engine.eval("require('./file1').sub._module");

    assertEquals(null, top.get("parent"));
    assertEquals(top, module.get("parent"));
    assertEquals(module, subModule.get("parent"));
    assertEquals(module, ((ArrayList) top.get("children")).get(0));
    assertEquals(subModule, ((ArrayList) module.get("children")).get(0));
    assertEquals(new ArrayList(), subModule.get("children"));
  }

  @Test
  public void loadedIsFalseWhileModuleIsLoadingAndTrueAfter() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn("exports._module = module; exports._loaded = module.loaded;");

    Bindings top = (Bindings) engine.eval("module");
    Bindings module = (Bindings) engine.eval("require('./file1')._module");
    boolean loaded = (boolean) engine.eval("require('./file1')._loaded");

    assertTrue((boolean) top.get("loaded"));
    assertFalse(loaded);
    assertTrue((boolean) module.get("loaded"));
  }

  @Test
  public void loadingTheSameModuleTwiceYieldsTheSameObject() throws Throwable {
    ScriptObjectMirror first = (ScriptObjectMirror) engine.eval("require('./file1');");
    ScriptObjectMirror second = (ScriptObjectMirror) engine.eval("require('./file1');");
    assertTrue(ScriptObjectMirror.identical(first, second));
  }

  @Test
  public void loadingTheSameModuleFromASubModuleYieldsTheSameObject() throws Throwable {
    when(root.getFile("file2.js")).thenReturn("exports.sub = require('./file1');");
    ScriptObjectMirror first = (ScriptObjectMirror) engine.eval("require('./file1');");
    ScriptObjectMirror second = (ScriptObjectMirror) engine.eval("require('./file2').sub;");
    assertTrue(ScriptObjectMirror.identical(first, second));
  }

  @Test
  public void loadingTheSameModuleFromASubPathYieldsTheSameObject() throws Throwable {
    when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub = require('../file1');");
    ScriptObjectMirror first = (ScriptObjectMirror) engine.eval("require('./file1');");
    ScriptObjectMirror second =
        (ScriptObjectMirror) engine.eval("require('./sub1/sub1file1').sub;");
    assertTrue(ScriptObjectMirror.identical(first, second));
  }

  @Test
  public void scriptCodeCanReplaceTheModuleExportsSymbol() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("module.exports = { 'foo': 'bar' }");
    assertEquals("bar", engine.eval("require('./file1').foo;"));
  }

  @Test
  public void itIsPossibleToRegisterGlobalVariablesForAllModules() throws Throwable {
    engine.put("bar", "bar");
    when(root.getFile("file1.js")).thenReturn("exports.foo = function() { return bar; }");
    assertEquals("bar", engine.eval("require('./file1').foo();"));
  }

  @Test
  public void engineScopeVariablesAreVisibleDuringModuleLoad() throws Throwable {
    engine.put("bar", "bar");
    when(root.getFile("file1.js"))
        .thenReturn("var found = bar == 'bar'; exports.foo = function() { return found; }");
    assertEquals(true, engine.eval("require('./file1').foo();"));
  }

  @Test
  public void itCanLoadModulesFromModulesFromModules() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("exports.sub = require('./file2.js');");
    when(root.getFile("file2.js")).thenReturn("exports.sub = require('./file3.js');");
    when(root.getFile("file3.js")).thenReturn("exports.foo = 'bar';");

    assertEquals("bar", engine.eval("require('./file1.js').sub.sub.foo"));
  }

  // Check for https://github.com/coveo/nashorn-commonjs-modules/issues/2
  @Test
  public void itCanCallFunctionsNamedGetFromModules() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("exports.get = function(foo) { return 'bar'; };");

    assertEquals("bar", engine.eval("require('./file1.js').get(123, 456)"));
  }

  // Checks for https://github.com/coveo/nashorn-commonjs-modules/issues/3

  // This one only failed on older JREs
  @Test
  public void itCanUseHighlightJsLibraryFromNpm() throws Throwable {
    File file = new File("src/test/resources/com/coveo/nashorn_modules/test2");
    FilesystemFolder root = FilesystemFolder.create(file, "UTF-8");
    require = Require.enable(engine, root);
    engine.eval("require('highlight.js').highlight('java', '\"foo\"')");
  }

  // This one failed on more recent ones too
  @Test
  public void anotherCheckForIssueNumber3() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn(
            "var a = require('./file2'); function b() {}; b.prototype = Object.create(a.prototype, {});");
    when(root.getFile("file2.js"))
        .thenReturn(
            "module.exports = a; function a() {}; a.prototype = Object.create(Object.prototype, {})");
    require = Require.enable(engine, root);
    engine.eval("require('./file1');");
  }

  // Check for https://github.com/coveo/nashorn-commonjs-modules/issues/4
  @Test
  public void itSupportOverwritingExportsWithAString() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("module.exports = 'foo';");
    assertEquals("foo", engine.eval("require('./file1.js')"));
  }

  // Check for https://github.com/coveo/nashorn-commonjs-modules/issues/4
  @Test
  public void itSupportOverwritingExportsWithAnInteger() throws Throwable {
    when(root.getFile("file1.js")).thenReturn("module.exports = 123;");
    assertEquals(123, engine.eval("require('./file1.js')"));
  }

  // Checks for https://github.com/coveo/nashorn-commonjs-modules/issues/11

  @Test
  public void itCanLoadInvariantFromFbjs() throws Throwable {
    File file = new File("src/test/resources/com/coveo/nashorn_modules/test3");
    FilesystemFolder root = FilesystemFolder.create(file, "UTF-8");
    require = Require.enable(engine, root);
    engine.eval("require('fbjs/lib/invariant')");
  }

  // Checks for https://github.com/coveo/nashorn-commonjs-modules/pull/14

  @Test
  public void itCanShortCircuitCircularRequireReferences() throws Throwable {
    File file = new File("src/test/resources/com/coveo/nashorn_modules/test4/cycles");
    FilesystemFolder root = FilesystemFolder.create(file, "UTF-8");
    require = Require.enable(engine, root);
    engine.eval("require('./main.js')");
  }

  @Test
  public void itCanShortCircuitDeepCircularRequireReferences() throws Throwable {
    File file = new File("src/test/resources/com/coveo/nashorn_modules/test4/deep");
    FilesystemFolder root = FilesystemFolder.create(file, "UTF-8");
    require = Require.enable(engine, root);
    engine.eval("require('./main.js')");
  }

  // Checks for https://github.com/coveo/nashorn-commonjs-modules/issues/15

  @Test
  public void itCanDefinePropertiesOnExportsObject() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn("Object.defineProperty(exports, '__esModule', { value: true });");
    engine.eval("require('./file1.js')");
  }

  @Test
  public void itIncludesFilenameInException() throws Throwable {
    when(root.getFile("file1.js"))
        .thenReturn("\n\nexports.foo = function() { throw \"bad thing\";};");
    try {
      engine.eval("require('./file1').foo();");
      fail("should throw exception");
    } catch (ScriptException e) {
      assertEquals("bad thing in /file1.js at line number 3", e.getMessage().substring(0, 39));
    }
  }
}
