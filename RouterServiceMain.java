// Directory structure:
// com.company.router
// ├── models
// │   └── RouteResult.java
// ├── service
// │   └── RouterService.java
// ├── store
// │   ├── RouteTrie.java
// │   └── TrieNode.java
// ├── strategy
// │   ├── MatchStrategy.java
// │   ├── WildcardMatchStrategy.java
// │   └── RecursiveWildcardMatchStrategy.java
// ├── utils
// │   └── PathUtils.java
// ├── exceptions
// │   └── RouteNotFoundException.java
// └── Main.java

// models/RouteResult.java
package models;

public class RouteResult {
    private final String value;

    public RouteResult(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

// exceptions/RouteNotFoundException.java
package exceptions;

public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException(String path) {
        super("No matching route found for path: " + path);
    }
}

// utils/PathUtils.java
package utils;

public class PathUtils {
    public static String[] splitPath(String path) {
        return path.split("/");
    }
}

// strategy/MatchStrategy.java
package strategy;

public interface MatchStrategy {
    boolean match(String patternSegment, String pathSegment);
}

// strategy/WildcardMatchStrategy.java
package strategy;

public class WildcardMatchStrategy implements MatchStrategy {
    @Override
    public boolean match(String patternSegment, String pathSegment) {
        return patternSegment.equals("*") || patternSegment.equals(pathSegment);
    }
}

// strategy/RecursiveWildcardMatchStrategy.java
package strategy;

public class RecursiveWildcardMatchStrategy implements MatchStrategy {
    @Override
    public boolean match(String patternSegment, String pathSegment) {
        return patternSegment.equals("**") || patternSegment.equals("*") || patternSegment.equals(pathSegment);
    }
}

// store/TrieNode.java
package store;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    Map<String, TrieNode> children = new HashMap<>();
    String value = null;

    public boolean hasChild(String part) {
        return children.containsKey(part);
    }

    public TrieNode getChild(String part) {
        return children.get(part);
    }

    public TrieNode getOrCreateChild(String part) {
        return children.computeIfAbsent(part, k -> new TrieNode());
    }
}

// store/RouteTrie.java
package store;

import strategy.MatchStrategy;
import utils.PathUtils;

public class RouteTrie {
    private final TrieNode root = new TrieNode();
    private final MatchStrategy strategy;

    public RouteTrie(MatchStrategy strategy) {
        this.strategy = strategy;
    }

    public void insert(String path, String value) {
        String[] parts = PathUtils.splitPath(path);
        TrieNode current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current = current.getOrCreateChild(part);
        }
        current.value = value;
    }

    public String search(String path) {
        String[] parts = PathUtils.splitPath(path);
        return searchRecursive(root, parts, 0);
    }

    private String searchRecursive(TrieNode node, String[] parts, int index) {
        if (node == null) return null;
        if (index == parts.length) return node.value;

        String part = parts[index];

        for (String key : node.children.keySet()) {
            if (strategy.match(key, part)) {
                String result = searchRecursive(node.getChild(key), parts, key.equals("**") ? index : index + 1);
                if (result != null) return result;
            }
        }

        return null;
    }
}

// service/RouterService.java
package service;

import exceptions.RouteNotFoundException;
import store.RouteTrie;

public class RouterService {
    private final RouteTrie trie;

    public RouterService(RouteTrie trie) {
        this.trie = trie;
    }

    public void addRoute(String path, String value) {
        trie.insert(path, value);
    }

    public String callRoute(String path) {
        String result = trie.search(path);
        if (result == null) throw new RouteNotFoundException(path);
        return result;
    }
}

// Main.java
import service.RouterService;
import store.RouteTrie;
import strategy.MatchStrategy;
import strategy.RecursiveWildcardMatchStrategy;

public class Main {
    public static void main(String[] args) {
        MatchStrategy strategy = new RecursiveWildcardMatchStrategy();
        RouterService router = new RouterService(new RouteTrie(strategy));

        router.addRoute("/foo", "foo");
        router.addRoute("/bar/*/baz", "bar1");
        router.addRoute("/bar/**", "bar2");
        router.addRoute("/x/**/z", "deep");

        System.out.println(router.callRoute("/foo"));         // foo
        System.out.println(router.callRoute("/bar/a/baz"));   // bar1
        System.out.println(router.callRoute("/bar/a/b/c"));   // bar2
        System.out.println(router.callRoute("/x/y/a/b/z"));   // deep

        try {
            System.out.println(router.callRoute("/not/found"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
