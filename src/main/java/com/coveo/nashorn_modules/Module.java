package com.coveo.nashorn_modules;

import java.util.ArrayList;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ECMAException;

public class Module extends SimpleBindings implements RequireFunction {
    private NashornScriptEngine engine;
    private Folder folder;
    private ModuleCache cache;

    private Module main;
    private Bindings module = new SimpleBindings();
    private Bindings exports = new SimpleBindings();
    private List<Bindings> children = new ArrayList<Bindings>();

    public Module(NashornScriptEngine engine,
                  Folder folder,
                  ModuleCache cache,
                  String filename,
                  Bindings global,
                  Module parent,
                  Module main) {

        this.engine = engine;
        this.folder = folder;
        this.cache = cache;
        this.main = main != null ? main : this;
        put("main", this.main.module);

        global.put("require", this);

        global.put("module", module);
        global.put("exports", exports);

        module.put("exports", exports);
        module.put("children", children);
        module.put("filename", filename);
        module.put("id", filename);
        module.put("loaded", false);
        module.put("parent", parent != null ? parent.module : null);
    }

    private void setLoaded() {
        module.put("loaded", true);
    }

    public static Module registerMainRequire(NashornScriptEngine engine, Folder folder) throws ScriptException {
        Bindings global = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        Module module = new Module(engine, folder, new ModuleCache(), "<main>", global, null, null);
        module.setLoaded();
        return module;
    }

    @Override
    public Bindings require(String module) throws ScriptException {
        if (module == null) {
            throwModuleNotFoundException("<null>");
        }

        String[] parts = Paths.splitPath(module);
        if (parts.length == 0) {
            throwModuleNotFoundException(module);
        }

        Folder resolved = resolveFolder(parts);
        if (resolved == null) {
            throwModuleNotFoundException(module);
        }

        String filename = parts[parts.length - 1];
        String[] namesToAttempt = new String[]{filename, filename + ".js", filename + ".json"};

        Module found = null;
        for (String name : namesToAttempt) {
            found = loadModule(resolved, name);
            if (found != null) {
                break;
            }
        }

        if (found == null) {
            throwModuleNotFoundException(module);
        }

        children.add(found.module);

        return found.exports;
    }

    private Folder resolveFolder(String[] parts) {
        Folder current = folder;

        for (int i = 0; i < parts.length - 1; ++i) {
            String part = parts[i].trim();

            switch (part) {
                case "":
                    throw new IllegalArgumentException();
                case ".":
                    continue;
                case "..":
                    current = current.getParent();
                    break;
                default:
                    current = current.getFolder(part);
                    break;
            }

            if (current == null) {
                break;
            }
        }

        return current;
    }

    private Module loadModule(Folder parent, String name) throws ScriptException {
        String fullPath = parent.getPath() + name;

        Module module = cache.get(fullPath);

        if (module == null) {
            String code = parent.getFile(name);
            if (code == null) {
                return null;
            }

            Bindings moduleGlobal = new SimpleBindings();
            module = new Module(engine, parent, cache, fullPath, moduleGlobal, this, this.main);
            engine.eval(code, moduleGlobal);
            module.setLoaded();

            cache.put(fullPath, module);
        }

        return module;
    }

    private void throwModuleNotFoundException(String module) throws ScriptException {
        ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
        Bindings error = (Bindings) ctor.newObject("Module not found: " + module);
        error.put("code", "MODULE_NOT_FOUND");
        throw new ECMAException(error, null);
    }
}
