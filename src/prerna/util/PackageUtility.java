package prerna.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import prerna.reactor.IReactor;
import prerna.util.Constants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PackageUtility {
	
	private static final Logger classLogger = LogManager.getLogger(PackageUtility.class);

	private final static char DOT = '.';
	private final static char SLASH = '/';
	private final static String CLASS_SUFFIX = ".class";
	private final static String BAD_PACKAGE_ERROR = "Unable to get resources from path '%s'. Are you sure the given '%s' package exists?";

	public static List<Class<?>> getReactors(String javaPackage) {
		List<Class<?>> classes = PackageUtility.find(javaPackage);
		for (int i = 0; i < classes.size(); i++) {
			Class<?> aClass = classes.get(i);
			boolean validReactor = false;
			// System.out.println(aClass.getSuperclass());
			// //Create an object of the class type
			try {
				Constructor constructor = aClass.getConstructor();
				constructor.newInstance();
				// System.out.println(aClass.getName());
				if (IReactor.class.isAssignableFrom(aClass)) {
					validReactor = true;
				}
			} catch (InstantiationException e) {
				// classLogger.error(Constants.STACKTRACE, e);
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				classLogger.error(Constants.STACKTRACE, e);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				classLogger.error(Constants.STACKTRACE, e);
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				classLogger.error(Constants.STACKTRACE, e);
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				classLogger.error(Constants.STACKTRACE, e);
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				classLogger.error(Constants.STACKTRACE, e);
			}
			if (!validReactor) {
				classes.remove(i);
				System.out.println("This class does not implement IReactor " + aClass.getName());
			}
		}
		return classes;

	}

	private final static List<Class<?>> find(final String scannedPackage) {
		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final String scannedPath = scannedPackage.replace(DOT, SLASH);
		final Enumeration<URL> resources;
		try {
			resources = classLoader.getResources(scannedPath);
		} catch (IOException e) {
			classLogger.error(Constants.STACKTRACE, e);
			throw new IllegalArgumentException(String.format(BAD_PACKAGE_ERROR, scannedPath, scannedPackage));
		}
		final List<Class<?>> classes = new LinkedList<Class<?>>();
		while (resources.hasMoreElements()) {
			final File file = new File(resources.nextElement().getFile());
			classes.addAll(find(file, scannedPackage));
		}
		return classes;
	}

	private final static List<Class<?>> find(final File file, final String scannedPackage) {
		final List<Class<?>> classes = new LinkedList<Class<?>>();
		if (file.isDirectory()) {
			for (File nestedFile : file.listFiles()) {
				classes.addAll(find(nestedFile, scannedPackage));
			}
			// File names with the $1, $2 holds the anonymous inner classes, we
			// are not interested on them.
		} else if (file.getName().endsWith(CLASS_SUFFIX) && !file.getName().contains("$")) {

			final int beginIndex = 0;
			final int endIndex = file.getName().length() - CLASS_SUFFIX.length();
			final String className = file.getName().substring(beginIndex, endIndex);
			try {
				final String resource = scannedPackage + DOT + className;
				classes.add(Class.forName(resource));
			} catch (ClassNotFoundException ignore) {
			}
		}
		return classes;
	}

}
