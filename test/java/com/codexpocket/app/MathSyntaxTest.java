package com.codexpocket.app;

public class MathSyntaxTest {
    public static void main(String[] args) {
        String[] math = {"行内 $x^2$", "\\(\\frac{1}{2}\\)", "\\[x+y\\]", "$$x+y$$", "```latex\n\\int_0^1 x dx\n```", "```math\na+b\n```", "```tex\na+b\n```", "\\begin{aligned}a&=b\\end{aligned}", "\\(未结束的公式"};
        for (String source : math) if (!MathSyntax.containsMath(source)) throw new AssertionError("Missing mathematical reply: " + source);
        for (String source : new String[]{"普通聊天", "hello world", "public int value = 42;", "单独的 $ 符号"}) if (MathSyntax.containsMath(source)) throw new AssertionError("Unexpected math view: " + source);
        System.out.println("Native formula detection: 13 cases passed.");
    }
}
