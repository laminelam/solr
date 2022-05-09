/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.common;

import org.apache.solr.common.util.StrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses the list of modules the user has requested in solr.xml, property solr.modules or
 * environment SOLR_MODULES. Then resolves the lib folder for each, so they can be added to class
 * path.
 */
public class ModuleUtils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String MODULES_FOLDER_NAME = "modules";
  private static final Pattern validModNamesPattern = Pattern.compile("[\\w\\d-_]+");

  /**
   * Returns a path to a module's lib folder
   *
   * @param moduleName name of module
   * @return the path to the module's lib folder
   */
  public static Path getModuleLibPath(Path solrInstallDirPath, String moduleName) {
    return getModulesPath(solrInstallDirPath).resolve(moduleName).resolve("lib");
  }

  /**
   * Finds list of ZK ACL module names requested by system property or environment variable
   *
   * @return set of raw volume names from sysprop and/or env.var
   */
  static Set<String> resolveFromSyspropOrEnv() {
    // Fall back to sysprop and env.var if nothing configured through solr.xml
    Set<String> mods = new HashSet<>();
    String modulesFromProps = System.getProperty("solrj.modules");
    if (!StringUtils.isEmpty(modulesFromProps)) {
      mods.addAll(StrUtils.splitSmart(modulesFromProps, ',', true));
    }
    String modulesFromEnv = System.getenv("SOLRJ_MODULES");
    if (!StringUtils.isEmpty(modulesFromEnv)) {
      mods.addAll(StrUtils.splitSmart(modulesFromEnv, ',', true));
    }

    return mods.stream().map(String::trim).collect(Collectors.toSet());
  }

  /** Returns true if a module name is valid and exists in the system */
  public static boolean moduleExists(Path solrInstallDirPath, String moduleName) {
    if (!isValidName(moduleName)) return false;
    Path modPath = getModulesPath(solrInstallDirPath).resolve(moduleName);
    return Files.isDirectory(modPath);
  }

  /** Returns nam of all existing modules */
  public static Set<String> listAvailableModules(Path solrInstallDirPath) {
    try (var moduleFilesStream = Files.list(getModulesPath(solrInstallDirPath))) {
      return moduleFilesStream
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .collect(Collectors.toSet());
    } catch (IOException e) {
      log.warn("Found no modules in {}", getModulesPath(solrInstallDirPath), e);
      return Collections.emptySet();
    }
  }

  /**
   * Parses comma separated string of module names, in practice found in solr.xml. If input string
   * is empty (nothing configured) or null (e.g. tag not present in solr.xml), we continue to
   * resolve from system property <code>-Dsolr.modules</code> and if still empty, fall back to
   * environment variable <code>SOLR_MODULES</code>.
   *
   * @param modulesFromString raw string of comma-separated module names
   * @return a set of module
   */
  public static Collection<String> resolveModulesFromStringOrSyspropOrEnv(
      String modulesFromString) {
    Collection<String> moduleNames;
    if (modulesFromString != null && !modulesFromString.isBlank()) {
      moduleNames = StrUtils.splitSmart(modulesFromString, ',', true);
    } else {
      // If nothing configured in solr.xml, check sysprop and environment
      moduleNames = resolveFromSyspropOrEnv();
    }
    return moduleNames.stream().map(String::trim).collect(Collectors.toSet());
  }

  /** Returns true if module name is valid */
  public static boolean isValidName(String moduleName) {
    return validModNamesPattern.matcher(moduleName).matches();
  }

  /** Returns path for modules directory, given the solr install dir path */
  public static Path getModulesPath(Path solrInstallDirPath) {
    return solrInstallDirPath.resolve(MODULES_FOLDER_NAME);
  }


  public static List<URL> getURLs(Path libDir) throws IOException {
    return getURLs(libDir, entry -> true);
  }

  public static List<URL> getURLs(Path libDir, DirectoryStream.Filter<Path> filter)
          throws IOException {
    List<URL> urls = new ArrayList<>();
    try (DirectoryStream<Path> directory = Files.newDirectoryStream(libDir, filter)) {
      for (Path element : directory) {
        urls.add(element.toUri().normalize().toURL());
      }
    }
    return urls;
  }


  // Adds modules to shared classpath

  public static void initModules(ModulesClassLoader resourceLoader) {
    Path solrInstallDir = getInstallDir();
    log.debug("solrInstallDir: {}", solrInstallDir);

    var moduleNames = ModuleUtils.resolveFromSyspropOrEnv();
    for (String m : moduleNames) {
      if (!ModuleUtils.moduleExists(solrInstallDir, m)) {
        log.error(
                "No module with name {}, available modules are {}",
                m,
                ModuleUtils.listAvailableModules(solrInstallDir));
        // Fail-fast if user requests a non-existing module
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "No module with name " + m);
      }
      Path moduleLibPath = ModuleUtils.getModuleLibPath(solrInstallDir, m);
      if (Files.exists(moduleLibPath)) {
        try {
          List<URL> urls = ModuleUtils.getURLs(moduleLibPath);
          resourceLoader.addToClassLoader(urls);
          if (log.isInfoEnabled()) {
            log.info("Added module {}. libPath={} with {} libs", m, moduleLibPath, urls.size());
          }
          if (log.isDebugEnabled()) {
            log.debug("Libs loaded from {}: {}", moduleLibPath, urls);
          }
        } catch (IOException e) {
          throw new SolrException(
                  SolrException.ErrorCode.SERVER_ERROR, "Couldn't load libs for module " + m + ": " + e, e);
        }
      } else {
        throw new SolrException(
                SolrException.ErrorCode.SERVER_ERROR, "Module lib folder " + moduleLibPath + " not found.");
      }
    }
  }

  private static Path getInstallDir() {
    String server_dir = Paths.get("").toAbsolutePath().toString();
    String install_dir = server_dir;
    int ptr = server_dir.lastIndexOf("server");
    if (ptr > 0) {
      install_dir = server_dir.substring(0, ptr);
      if (install_dir.endsWith("/")) {
        install_dir += "/";
      }
    }
    return Paths.get(install_dir);
  }
}
