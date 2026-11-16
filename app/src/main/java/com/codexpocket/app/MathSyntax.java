package com.codexpocket.app;

import java.util.regex.Pattern;

/** Detect math early, including a formula whose closing delimiter is still streaming. */
final class MathSyntax {
    private static final Pattern MATH = Pattern.compile("(?s)\\\\[\\[(]|\\$\\$|\\$[^\\s$][^\\n$]*\\$|(?m)^\\s*```(?:math|latex|tex)\\b|\\\\begin\\{(?:align|equation|gather)");
    static boolean containsMath(String text) { return MATH.matcher(text).find(); }
}
