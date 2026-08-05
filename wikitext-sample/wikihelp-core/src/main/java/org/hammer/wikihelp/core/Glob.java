package org.hammer.wikihelp.core;

import java.util.regex.Pattern;

public final class Glob {
    private Glob() {
    }

    public static boolean matches(String glob, String value) {
        if (glob == null || glob.isBlank()) {
            return true;
        }
        return Pattern.compile(toRegex(glob), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(value == null ? "" : value)
                .matches();
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                    regex.append(followedBySlash ? "(?:.*/)?" : ".*");
                    i += followedBySlash ? 2 : 1;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append('.');
            } else {
                if ("\\.^$|()[]{}+".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return regex.append('$').toString();
    }
}
