package com.coveo.nashorn_modules;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import javax.script.Bindings;
import javax.script.ScriptEngineManager;

import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RequireTest {
    @Mock
    Folder root;
    @Mock
    Folder sub1;
    @Mock
    Folder sub1sub1;

    NashornScriptEngine engine;
    Module require;

    @Before
    public void before() throws Throwable {
        when(root.getPath()).thenReturn("/");
        when(root.getFolder("sub1")).thenReturn(sub1);
        when(root.getFile("file1.js")).thenReturn("exports.file1 = 'file1';");
        when(sub1.getPath()).thenReturn("/sub1/");
        when(sub1.getParent()).thenReturn(root);
        when(sub1.getFolder("sub1")).thenReturn(sub1sub1);
        when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub1file1 = 'sub1file1';");
        when(sub1sub1.getPath()).thenReturn("/sub1/sub1/");
        when(sub1sub1.getParent()).thenReturn(sub1);
        when(sub1sub1.getFile("sub1sub1file1.js")).thenReturn("exports.sub1sub1file1 = 'sub1sub1file1';");
        engine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
        require = Module.registerMainRequire(engine, root);
    }

    @Test
    public void itCanLoadSimpleModules() throws Throwable {
        assertEquals("file1", require.require("file1.js").get("file1"));
    }

    @Test
    public void itCanLoadModulesFromSubFolders() throws Throwable {
        assertEquals("sub1file1", require.require("sub1/sub1file1.js").get("sub1file1"));
    }

    @Test
    public void itCanLoadModulesFromParentFolders() throws Throwable {
        when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub1file1 = require('../file1').file1;");
        assertEquals("file1", require.require("sub1/sub1file1.js").get("sub1file1"));
    }

    @Test
    public void itCanUseDotToReferenceToTheCurrentFolder() throws Throwable {
        assertEquals("file1", require.require("./file1.js").get("file1"));
    }

    @Test
    public void itCanUseDotAndDoubleDotsToGoBackAndForward() throws Throwable {
        assertEquals("file1", require.require("./sub1/.././sub1/../file1.js").get("file1"));
    }

    @Test
    public void thePathOfModulesContainsNoDots() throws Throwable {
        when(root.getFile("file1.js")).thenReturn("exports.path = module.filename");
        assertEquals("/file1.js", require.require("./sub1/.././sub1/../file1.js").get("path"));
    }

    @Test
    public void itCanLoadModulesFromSubSubFolders() throws Throwable {
        assertEquals("sub1sub1file1", require.require("sub1/sub1/sub1sub1file1.js").get("sub1sub1file1"));
    }

    @Test
    public void itCanLoadModuleIfTheExtensionIsOmitted() throws Throwable {
        assertEquals("file1", require.require("file1").get("file1"));
    }

    @Test(expected = NashornException.class)
    public void itThrowsAnExceptionIfFileDoesNotExists() throws Throwable {
        require.require("invalid");
    }

    @Test(expected = NashornException.class)
    public void itThrowsAnExceptionIfSubFileDoesNotExists() throws Throwable {
        require.require("sub1/invalid");
    }

    @Test(expected = NashornException.class)
    public void itThrowsEnExceptionIfFolderDoesNotExists() throws Throwable {
        require.require("invalid/file1.js");
    }

    @Test(expected = NashornException.class)
    public void itThrowsEnExceptionIfSubFolderDoesNotExists() throws Throwable {
        require.require("sub1/invalid/file1.js");
    }

    @Test(expected = NashornException.class)
    public void itThrowsAnExceptionIfTryingToGoAboveTheTopLevelFolder() throws Throwable {
        require.require("../file1.js");
    }

    @Test
    public void theExceptionThrownForAnUnknownFileCanBeCaughtInJavaScriptAndHasTheProperCode() throws Throwable {
        String code = (String) engine.eval("(function() { try { require('invalid'); } catch (ex) { return ex.code; } })();");
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
        when(root.getFile("file1.js")).thenReturn("exports._module = module; exports._exports = exports; exports._main = require.main;");

        Bindings top = (Bindings) engine.eval("module");
        Bindings module = (Bindings) engine.eval("require('file1')._module");
        Bindings exports = (Bindings) engine.eval("require('file1')._exports");
        Bindings main = (Bindings) engine.eval("require('file1')._main");

        assertEquals(exports, module.get("exports"));
        assertEquals(new ArrayList(), module.get("children"));
        assertEquals("/file1.js", module.get("filename"));
        assertEquals("/file1.js", module.get("id"));
        assertEquals(true, module.get("loaded"));
        assertEquals(top, module.get("parent"));
        assertNotNull(exports);
        assertEquals(top, main);
    }

    @Test
    public void subModulesExposeTheExpectedFields() throws Throwable {
        when(sub1.getFile("sub1file1.js")).thenReturn("exports._module = module; exports._exports = exports; exports._main = require.main;");

        Bindings top = (Bindings) engine.eval("module");
        Bindings module = (Bindings) engine.eval("require('sub1/sub1file1')._module");
        Bindings exports = (Bindings) engine.eval("require('sub1/sub1file1')._exports");
        Bindings main = (Bindings) engine.eval("require('sub1/sub1file1')._main");

        assertEquals(exports, module.get("exports"));
        assertEquals(new ArrayList(), module.get("children"));
        assertEquals("/sub1/sub1file1.js", module.get("filename"));
        assertEquals("/sub1/sub1file1.js", module.get("id"));
        assertEquals(true, module.get("loaded"));
        assertEquals(top, module.get("parent"));
        assertNotNull(exports);
        assertEquals(top, main);
    }

    @Test
    public void subSubModulesExposeTheExpectedFields() throws Throwable {
        when(sub1sub1.getFile("sub1sub1file1.js")).thenReturn("exports._module = module; exports._exports = exports; exports._main = require.main;");

        Bindings top = (Bindings) engine.eval("module");
        Bindings module = (Bindings) engine.eval("require('sub1/sub1/sub1sub1file1')._module");
        Bindings exports = (Bindings) engine.eval("require('sub1/sub1/sub1sub1file1')._exports");
        Bindings main = (Bindings) engine.eval("require('sub1/sub1/sub1sub1file1')._main");

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
        when(root.getFile("file1.js")).thenReturn("exports._module = module; exports.sub = require('sub1/sub1file1');");
        when(sub1.getFile("sub1file1.js")).thenReturn("exports._module = module;");

        Bindings top = (Bindings) engine.eval("module");
        Bindings module = (Bindings) engine.eval("require('file1')._module");
        Bindings subModule = (Bindings) engine.eval("require('file1').sub._module");

        assertEquals(null, top.get("parent"));
        assertEquals(top, module.get("parent"));
        assertEquals(module, subModule.get("parent"));
        assertEquals(module, ((ArrayList) top.get("children")).get(0));
        assertEquals(subModule, ((ArrayList) module.get("children")).get(0));
        assertEquals(new ArrayList(), subModule.get("children"));
    }

    @Test
    public void loadedIsFalseWhileModuleIsLoadingAndTrueAfter() throws Throwable {
        when(root.getFile("file1.js")).thenReturn("exports._module = module; exports._loaded = module.loaded;");

        Bindings top = (Bindings) engine.eval("module");
        Bindings module = (Bindings) engine.eval("require('file1')._module");
        boolean loaded = (boolean) engine.eval("require('file1')._loaded");

        assertTrue((boolean) top.get("loaded"));
        assertFalse(loaded);
        assertTrue((boolean) module.get("loaded"));
    }

    @Test
    public void loadingTheSameModuleTwiceYieldsTheSameObject() throws Throwable {
        Object first = engine.eval("require('file1');");
        Object second = engine.eval("require('file1');");
        assertSame(first, second);
    }

    @Test
    public void loadingTheSameModuleFromASubModuleYieldsTheSameObject() throws Throwable {
        when(root.getFile("file2.js")).thenReturn("exports.sub = require('file1');");
        Object first = engine.eval("require('file1');");
        Object second = engine.eval("require('file2').sub;");
        assertSame(first, second);
    }

    @Test
    public void loadingTheSameModuleFromASubPathYieldsTheSameObject() throws Throwable {
        when(sub1.getFile("sub1file1.js")).thenReturn("exports.sub = require('../file1');");
        Object first = engine.eval("require('file1');");
        Object second = engine.eval("require('sub1/sub1file1').sub;");
        assertSame(first, second);
    }
}
