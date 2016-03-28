package com.coveo.nashorn_modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.script.Bindings;
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
  private List<Bindings> children = new ArrayList<>();

  public Module(
      NashornScriptEngine engine,
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

  void setLoaded() {
    module.put("loaded", true);
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

    String[] folders = Arrays.copyOfRange(parts, 0, parts.length - 1);
    String[] filenames = getFilenamesToAttempt(parts[parts.length - 1]);

    Module found = null;
    if (shouldLoadFromNodeModules(module)) {
      // If the path doesn't already start with node_modules, add it.
      if (folders.length == 0 || !folders[0].equals("node_modules")) {
        folders =
            Stream.concat(Stream.of("node_modules"), Arrays.stream(folders)).toArray(String[]::new);
      }

      // When loading from node_modules, we'll try to resolve first from
      // the current folder and then we'll look at all our parents.
      Folder current = folder;
      while (current != null) {
        found = attemptToLoadStartingFromFolder(current, folders, filenames);
        if (found != null) {
          break;
        } else {
          current = current.getParent();
        }
      }
    } else {
      // When not loading from node_modules we will not automatically
      // look up the folder hierarchy, making the process quite simpler.
      found = attemptToLoadStartingFromFolder(folder, folders, filenames);
    }

    if (found == null) {
      throwModuleNotFoundException(module);
    }

    children.add(found.module);

    return found.exports;
  }

  private Module attemptToLoadStartingFromFolder(Folder from, String[] folders, String[] filenames)
      throws ScriptException {
    Folder found = resolveFolder(from, folders);
    if (found == null) {
      return null;
    }

    return attemptToLoadFromThisFolder(found, filenames);
  }

  private Module attemptToLoadFromThisFolder(Folder from, String[] filenames)
      throws ScriptException {
    for (String filename : filenames) {
      Module found = loadModule(from, filename);
      if (found != null) {
        return found;
      }
    }

    return null;
  }

  private Folder resolveFolder(Folder from, String[] folders) {
    Folder current = from;
    for (String name : folders) {
      switch (name) {
        case "":
          throw new IllegalArgumentException();
        case ".":
          continue;
        case "..":
          current = current.getParent();
          break;
        default:
          current = current.getFolder(name);
          break;
      }

      // Whenever we get stuck we bail out
      if (current == null) {
        return null;
      }
    }

    return current;
  }

  private Module loadModule(Folder parent, String name) throws ScriptException {
    String fullPath = parent.getPath() + name;

    Module found = cache.get(fullPath);

    if (found == null) {
      found = loadModuleDirectly(parent, fullPath, name);
    }

    if (found == null) {
      found = loadModuleThroughFolderName(parent, name);
    }

    if (found != null) {
      // We keep a cache entry for the requested path even though the code that
      // compiles the module also adds it to the cache with the potentially different
      // effective path. This avoids having to load package.json every time, etc.
      cache.put(fullPath, found);
    }

    return found;
  }

  private Module loadModuleDirectly(Folder parent, String fullPath, String name)
      throws ScriptException {
    String code = parent.getFile(name);
    if (code == null) {
      return null;
    }

    return compileModuleAndPutInCache(parent, fullPath, code);
  }

  private Module loadModuleThroughFolderName(Folder parent, String name) throws ScriptException {
    Folder fileAsFolder = parent.getFolder(name);
    if (fileAsFolder == null) {
      return null;
    }

    Module found = loadModuleThroughPackageJson(fileAsFolder);

    if (found == null) {
      found = loadModuleThroughIndexJs(fileAsFolder);
    }

    return found;
  }

  private Module loadModuleThroughPackageJson(Folder parent) throws ScriptException {
    String packageJson = parent.getFile("package.json");
    if (packageJson == null) {
      return null;
    }

    String mainFile = getMainFileFromPackageJson(packageJson);
    if (mainFile == null) {
      return null;
    }

    String[] parts = Paths.splitPath(mainFile);
    String[] folders = Arrays.copyOfRange(parts, 0, parts.length - 1);
    String filename = parts[parts.length - 1];
    Folder folder = resolveFolder(parent, folders);
    if (folder == null) {
      return null;
    }

    String code = folder.getFile(filename);
    return compileModuleAndPutInCache(folder, folder.getPath() + filename, code);
  }

  private Module loadModuleThroughIndexJs(Folder parent) throws ScriptException {
    String code = parent.getFile("index.js");
    if (code == null) {
      return null;
    }

    return compileModuleAndPutInCache(parent, parent.getPath() + "index.js", code);
  }

  private Module compileModuleAndPutInCache(Folder parent, String fullPath, String code)
      throws ScriptException {

    Module created;
    String lowercaseFullPath = fullPath.toLowerCase();
    if (lowercaseFullPath.endsWith(".js")) {
      created = compileJavaScriptModule(parent, fullPath, code);
    } else if (lowercaseFullPath.endsWith(".json")) {
      created = compileJsonModule(parent, fullPath, code);
    } else {
      // Unsupported module type
      return null;
    }

    // We keep a cache entry for the compiled module using it's effective path, to avoid
    // recompiling even if module is requested through a different initial path.
    cache.put(fullPath, created);

    return created;
  }

  private Module compileJavaScriptModule(Folder parent, String fullPath, String code)
      throws ScriptException {
    Bindings moduleGlobal = new SimpleBindings();
    Module created = new Module(engine, parent, cache, fullPath, moduleGlobal, this, this.main);
    engine.eval(code, moduleGlobal);
    created.setLoaded();
    return created;
  }

  private Module compileJsonModule(Folder parent, String fullPath, String code)
      throws ScriptException {
    Bindings moduleGlobal = new SimpleBindings();
    Module created = new Module(engine, parent, cache, fullPath, moduleGlobal, this, this.main);
    created.exports = parseJson(code);
    created.setLoaded();
    return created;
  }

  private String getMainFileFromPackageJson(String packageJson) throws ScriptException {
    Bindings parsed = parseJson(packageJson);
    return (String) parsed.get("main");
  }

  private Bindings parseJson(String json) throws ScriptException {
    // Pretty lame way to parse JSON but hey...
    ScriptObjectMirror jsJson = (ScriptObjectMirror) engine.eval("JSON");
    return (Bindings) jsJson.callMember("parse", json);
  }

  private void throwModuleNotFoundException(String module) throws ScriptException {
    ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
    Bindings error = (Bindings) ctor.newObject("Module not found: " + module);
    error.put("code", "MODULE_NOT_FOUND");
    throw new ECMAException(error, null);
  }

  private static boolean shouldLoadFromNodeModules(String module) {
    return !(module.startsWith("/") || module.startsWith("../") || module.startsWith("./"));
  }

  private static String[] getFilenamesToAttempt(String filename) {
    return new String[] {filename, filename + ".js", filename + ".json"};
  }
}
