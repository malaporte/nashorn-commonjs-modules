package com.coveo.nashorn_modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  private Bindings module;
  private List<Bindings> children = new ArrayList<>();
  private Object exports;

  public Module(
      NashornScriptEngine engine,
      Folder folder,
      ModuleCache cache,
      String filename,
      Bindings module,
      Bindings exports,
      Module parent,
      Module main)
      throws ScriptException {

    this.engine = engine;
    this.folder = folder;
    this.cache = cache;
    this.main = main != null ? main : this;
    this.module = module;
    this.exports = exports;

    put("main", this.main.module);

    module.put("exports", exports);
    module.put("children", children);
    module.put("filename", filename);
    module.put("id", filename);
    module.put("loaded", false);
    module.put("parent", parent != null ? parent.module : null);
  }

  public void setLoaded() {
    module.put("loaded", true);
  }

  @Override
  public Object require(String module) throws ScriptException {
    if (module == null) {
      throwModuleNotFoundException("<null>");
    }

    String[] parts = Paths.splitPath(module);
    if (parts.length == 0) {
      throwModuleNotFoundException(module);
    }

    String[] folderParts = Arrays.copyOfRange(parts, 0, parts.length - 1);

    String filename = parts[parts.length - 1];

    Module found = null;

    // First we try to resolve the module from the current folder, ignoring node_modules
    if (isPrefixedModuleName(module)) {
      found = attemptToLoadFromThisFolder(folder, folderParts, filename);
    }

    // Then, if not successful, we'll look at node_modules in the current folder and then
    // in all parent folders until we reach the top.
    if (found == null) {
      found = searchForModuleInNodeModules(folder, folderParts, filename);
    }

    if (found == null) {
      throwModuleNotFoundException(module);
    }

    assert found != null;
    children.add(found.module);

    return found.exports;
  }

  private Module searchForModuleInNodeModules(Folder from, String[] folderParts, String filename)
      throws ScriptException {
    Folder current = from;
    while (current != null) {
      Folder nodeModules = current.getFolder("node_modules");

      if (nodeModules != null) {
        Module found = attemptToLoadFromThisFolder(nodeModules, folderParts, filename);
        if (found != null) {
          return found;
        }
      }

      current = current.getParent();
    }

    return null;
  }

  private Module attemptToLoadFromThisFolder(Folder from, String[] folders, String filename)
      throws ScriptException {

    Folder resolvedFolder = resolveFolder(from, folders);
    if (resolvedFolder == null) {
      return null;
    }

    String requestedFullPath = resolvedFolder.getPath() + filename;
    Module found = cache.get(requestedFullPath);
    if (found != null) {
      return found;
    }

    // First we try to load as a file, trying out various variations on the path
    found = loadModuleAsFile(resolvedFolder, filename);

    // Then we try to load as a directory
    if (found == null) {
      found = loadModuleAsFolder(resolvedFolder, filename);
    }

    if (found != null) {
      // We keep a cache entry for the requested path even though the code that
      // compiles the module also adds it to the cache with the potentially different
      // effective path. This avoids having to load package.json every time, etc.
      cache.put(requestedFullPath, found);
    }

    return found;
  }

  private Module loadModuleAsFile(Folder parent, String filename) throws ScriptException {

    String[] filenamesToAttempt = getFilenamesToAttempt(filename);
    for (String tentativeFilename : filenamesToAttempt) {

      String code = parent.getFile(tentativeFilename);
      if (code != null) {
        String fullPath = parent.getPath() + tentativeFilename;
        return compileModuleAndPutInCache(parent, fullPath, code);
      }
    }

    return null;
  }

  private Module loadModuleAsFolder(Folder parent, String name) throws ScriptException {
    Folder fileAsFolder = parent.getFolder(name);
    if (fileAsFolder == null) {
      return null;
    }

    Module found = loadModuleThroughPackageJson(fileAsFolder);

    if (found == null) {
      found = loadModuleThroughIndexJs(fileAsFolder);
    }

    if (found == null) {
      found = loadModuleThroughIndexJson(fileAsFolder);
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

    return loadModuleAsFile(folder, filename);
  }

  private String getMainFileFromPackageJson(String packageJson) throws ScriptException {
    Bindings parsed = parseJson(packageJson);
    return (String) parsed.get("main");
  }

  private Module loadModuleThroughIndexJs(Folder parent) throws ScriptException {
    String code = parent.getFile("index.js");
    if (code == null) {
      return null;
    }

    return compileModuleAndPutInCache(parent, parent.getPath() + "index.js", code);
  }

  private Module loadModuleThroughIndexJson(Folder parent) throws ScriptException {
    String code = parent.getFile("index.json");
    if (code == null) {
      return null;
    }

    return compileModuleAndPutInCache(parent, parent.getPath() + "index.json", code);
  }

  private Module compileModuleAndPutInCache(Folder parent, String fullPath, String code)
      throws ScriptException {

    Module created;
    String lowercaseFullPath = fullPath.toLowerCase();
    String fileEnding = fullPath.substring(fullPath.lastIndexOf('.'), fullPath.length());
    FileHandler fileHandler = Require.getFileHandler(fileEnding);
    if (fileHandler != null) {
      created = fileHandler.compile(parent, fullPath, code, this);
    } else if (lowercaseFullPath.endsWith(".js")) {
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

    Bindings module = engine.createBindings();
    Bindings exports = engine.createBindings();
    Module created = new Module(engine, parent, cache, fullPath, module, exports, this, this.main);

    String[] split = Paths.splitPath(fullPath);
    String filename = split[split.length - 1];
    String dirname = fullPath.substring(0, Math.max(fullPath.length() - filename.length() - 1, 0));

    // This mimics how Node wraps module in a function. I used to pass a 2nd parameter
    // to eval to override global context, but it caused problems Object.create.
    ScriptObjectMirror function =
        (ScriptObjectMirror)
            engine.eval(
                "(function (exports, require, module, __filename, __dirname) {" + code + "})");
    function.call(created, created.exports, created, created.module, filename, dirname);

    // Scripts are free to replace the global exports symbol with their own, so we
    // reload it from the module object after compiling the code.
    created.exports = created.module.get("exports");

    created.setLoaded();
    return created;
  }

  private Module compileJsonModule(Folder parent, String fullPath, String code)
      throws ScriptException {
    Bindings module = engine.createBindings();
    Bindings exports = engine.createBindings();
    Module created = new Module(engine, parent, cache, fullPath, module, exports, this, this.main);
    created.exports = parseJson(code);
    created.setLoaded();
    return created;
  }

  private ScriptObjectMirror parseJson(String json) throws ScriptException {
    // Pretty lame way to parse JSON but hey...
    ScriptObjectMirror jsJson = (ScriptObjectMirror) engine.eval("JSON");
    return (ScriptObjectMirror) jsJson.callMember("parse", json);
  }

  private void throwModuleNotFoundException(String module) throws ScriptException {
    ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
    Bindings error = (Bindings) ctor.newObject("Module not found: " + module);
    error.put("code", "MODULE_NOT_FOUND");
    throw new ECMAException(error, null);
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

  private static boolean isPrefixedModuleName(String module) {
    return module.startsWith("/") || module.startsWith("../") || module.startsWith("./");
  }

  private static String[] getFilenamesToAttempt(String filename) {
    return new String[] {filename, filename + ".js", filename + ".json"};
  }

  public NashornScriptEngine getEngine() {
    return engine;
  }

  public ModuleCache getCache() {
    return cache;
  }

  public Module getMainModule() {
    return main;
  }

  public Object getExports() {
    return exports;
  }

  public Bindings getModule() {
    return module;
  }

  public void setExports(Object exports) {
    this.exports = exports;
  }
}
