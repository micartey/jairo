package me.clientastisch.micartey;

import lombok.Getter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class MicarteyDependency {

    @Getter private final ClassLoader classLoader;

    public MicarteyDependency(List<URL> urls, ClassLoader parent) {
        this.classLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]), parent);
    }

    public MicarteyDependency(List<URL> urls) {
        this(urls, MicarteyDependency.class.getClassLoader());
    }

}
