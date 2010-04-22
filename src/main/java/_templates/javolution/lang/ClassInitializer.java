/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2006 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package _templates.javolution.lang;

import java.util.Enumeration;

import _templates.java.io.File;
import _templates.java.util.zip.ZipEntry;
import _templates.java.util.zip.ZipFile;
import _templates.javolution.context.LogContext;

/**
 * <p> This utility class allows for initialization of all classes
 *     at startup to avoid initialization delays at an innapropriate time.</p>
 *     
 * <p> Note: Users might want to disable logging when initializing run-time 
 *     classes  at start-up because of the presence of old classes (never used) 
 *     in the jar files for which initialization fails. For example:[code]
 *     public static main(String[] args) {
 *         LogContext.enter(LogContext.NULL); // Temporarely disables logging errors and warnings.
 *         try { 
 *             ClassInitializer.initializeAll();  // Initializes bootstrap, extensions and classpath classes.
 *         } finally {
 *             LogContext.exit(LogContext.NULL); // Goes back to default logging.
 *         }
 *         ...
 *     }[/code]</p>
 *    
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 5.5, April 21, 2010
 */
public class ClassInitializer {

    /**
     * Default constructor (private for utility class)
     */
    private ClassInitializer() {
    }

    /**
     * Initializes all runtime and classpath classes.
     * 
     * @see #initializeRuntime()
     * @see #initializeClassPath()
     */
    public static void initializeAll() {
        initializeRuntime();
        initializeClassPath();
    }

    /**
     * Initializes runtime classes (bootstrap classes in <code>
     * System.getProperty("sun.boot.class.path"))</code>  and the 
     * extension <code>.jar</code> in <code>lib/ext</code> directory). 
     */
    public static void initializeRuntime() {
        String bootPath = System.getProperty("sun.boot.class.path");
        String pathSeparator = System.getProperty("path.separator");
        if ((bootPath == null) || (pathSeparator == null)) {
            LogContext.warning("Cannot initialize boot path through system properties");
            return;
        }
        initialize(bootPath, pathSeparator);
        String javaHome = System.getProperty("java.home");
        String fileSeparator = System.getProperty("file.separator");
        if ((javaHome == null) || (fileSeparator == null)) {
            LogContext.warning("Cannot initialize extension library through system properties");
            return;
        }
        File extDir = new File(javaHome + fileSeparator + "lib" + fileSeparator + "ext");
        if (!extDir.getClass().getName().equals("java.io.File")) {
            LogContext.warning("Extension classes initialization not supported for J2ME build");
            return;
        }
        if (extDir.isDirectory()) {
            File[] files = extDir.listFiles();
            for (int i = 0; i < files.length; i++) {
                String path = files[i].getPath();
                if (path.endsWith(".jar") || path.endsWith(".zip")) {
                    initializeJar(path);
                }
            }
        } else {
            LogContext.warning(extDir + " is not a directory");
        }
    }

    /**
     * Initializes all classes in current classpath.
     */
    public static void initializeClassPath() {
        String classPath = System.getProperty("java.class.path");
        String pathSeparator = System.getProperty("path.separator");
        if ((classPath == null) || (pathSeparator == null)) {
            LogContext.warning("Cannot initialize classpath through system properties");
            return;
        }
        initialize(classPath, pathSeparator);
    }

    private static void initialize(String classPath, String pathSeparator) {
        LogContext.info("Initialize classpath: " + classPath);
        while (classPath.length() > 0) {
            String name;
            int index = classPath.indexOf(pathSeparator);
            if (index < 0) {
                name = classPath;
                classPath = "";
            } else {
                name = classPath.substring(0, index);
                classPath = classPath.substring(index + pathSeparator.length());
            }
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                initializeJar(name);
            } else {
                initializeDir(name);
            }
        }
    }

    /**
     * Initializes the specified class.
     * 
     * @param cls the class to initialize.
     */
    public static void initialize(Class cls) {
       try {
           /* @JVM-1.4+@
           Class.forName(cls.getName(), true, cls.getClassLoader());
           if (true) return;
           /**/
           Class.forName(cls.getName()); // J2ME
       }  catch (ClassNotFoundException e) {
           LogContext.error(e);
       }
    }

    /**
     * Initializes the class with the specified name.
     * 
     * @param className the name of the class to initialize.
     */
    public static void initialize(String className) {
        try {
            Class cls = Reflection.getInstance().getClass(className);
            if (cls == null) {
                LogContext.warning("Class + " + className + " not found");
            }
        } catch (Throwable error) {
            LogContext.error(error);
        }
    }

    /**
     * Initializes all the classes in the specified jar file.
     * 
     * @param jarName the jar filename. 
     */
    public static void initializeJar(String jarName) {
        try {
            LogContext.info("Initialize Jar file: " + jarName);
            ZipFile jarFile = new ZipFile(jarName);
            if (!jarFile.getClass().getName().equals("java.util.zip.ZipFile")) {
                LogContext.warning("Initialization of classes in jar file not supported for J2ME build");
                return;
            }
            Enumeration e = jarFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) e.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6);
                    className = className.replace('/', '.');
                    ClassInitializer.initialize(className);
                }
            }
        } catch (Exception e) {
            LogContext.error(e);
        }
    }

    /**
     * Initializes all the classes in the specified directory.
     * 
     * @param dirName the name of the directory containing the classes to
     *         initialize. 
     */
    public static void initializeDir(String dirName) {
        LogContext.info("Initialize Directory: " + dirName);
        File file = new File(dirName);
        if (!file.getClass().getName().equals("java.io.File")) {
            LogContext.warning("Initialization of classes in directory not supported for J2ME build");
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                initialize("", files[i]);
            }
        } else {
            LogContext.warning(dirName + " is not a directory");
        }
    }

    private static void initialize(String prefix, File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            String newPrefix = (prefix.length() == 0) ? name : prefix + "." + name;
            for (int i = 0; i < files.length; i++) {
                initialize(newPrefix, files[i]);
            }
        } else {
            if (name.endsWith(".class")) {
                String className = prefix + "." + name.substring(0, name.length() - 6);
                ClassInitializer.initialize(className);
            }
        }
    }
}
