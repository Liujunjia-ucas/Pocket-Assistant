package com.codexpocket.app;

/** One palette shared by native controls and the offline formula renderer. */
final class ThemePalette {
    final boolean dark;
    final String colorTheme;
    static final String[] COLOR_IDS={"sage","ocean","violet","rose","amber","slate","cyan","teal","lime","lemon","coral","ruby","magenta","indigo","sky","coffee"};
    static final String[] COLOR_NAMES={"鲜绿","亮蓝","活力紫","玫红","橘橙","石墨蓝","湖水青","薄荷绿","青柠绿","柠檬黄","珊瑚橙","樱桃红","亮粉紫","靛蓝","晴空蓝","焦糖棕"};
    private static final int[] LIGHT={0xff00783f,0xff0060ce,0xff7537e4,0xffbe1c5b,0xffad4800,0xff343f56,0xff006d7b,0xff006f60,0xff4e7100,0xff846000,0xffb9321f,0xffb91b36,0xffa51e9e,0xff4147ce,0xff0069a8,0xff87502b};
    private static final int[] DARK={0xff38e58b,0xff57adff,0xffaf83ff,0xffff79b2,0xffffaa42,0xffb4c3e8,0xff35dee8,0xff30dfbf,0xffaddf39,0xffffdc45,0xffff8970,0xffff6980,0xfff778ee,0xff9297ff,0xff47c9ff,0xffe8b27d};
    static String normalizeColor(String id){for(String key:COLOR_IDS)if(key.equals(id))return key;return COLOR_IDS[0];}
    static int colorIndex(String id){for(int i=0;i<COLOR_IDS.length;i++)if(COLOR_IDS[i].equals(id))return i;return 0;}
    static String colorName(String id){return COLOR_NAMES[colorIndex(id)];}
    int accentStyle() {
        switch(colorTheme) {
            case "ocean": return dark?R.style.PocketAccent_ocean_dark:R.style.PocketAccent_ocean_light;
            case "violet": return dark?R.style.PocketAccent_violet_dark:R.style.PocketAccent_violet_light;
            case "rose": return dark?R.style.PocketAccent_rose_dark:R.style.PocketAccent_rose_light;
            case "amber": return dark?R.style.PocketAccent_amber_dark:R.style.PocketAccent_amber_light;
            case "slate": return dark?R.style.PocketAccent_slate_dark:R.style.PocketAccent_slate_light;
            case "cyan": return dark?R.style.PocketAccent_cyan_dark:R.style.PocketAccent_cyan_light;
            case "teal": return dark?R.style.PocketAccent_teal_dark:R.style.PocketAccent_teal_light;
            case "lime": return dark?R.style.PocketAccent_lime_dark:R.style.PocketAccent_lime_light;
            case "lemon": return dark?R.style.PocketAccent_lemon_dark:R.style.PocketAccent_lemon_light;
            case "coral": return dark?R.style.PocketAccent_coral_dark:R.style.PocketAccent_coral_light;
            case "ruby": return dark?R.style.PocketAccent_ruby_dark:R.style.PocketAccent_ruby_light;
            case "magenta": return dark?R.style.PocketAccent_magenta_dark:R.style.PocketAccent_magenta_light;
            case "indigo": return dark?R.style.PocketAccent_indigo_dark:R.style.PocketAccent_indigo_light;
            case "sky": return dark?R.style.PocketAccent_sky_dark:R.style.PocketAccent_sky_light;
            case "coffee": return dark?R.style.PocketAccent_coffee_dark:R.style.PocketAccent_coffee_light;
            default: return dark?R.style.PocketAccent_sage_dark:R.style.PocketAccent_sage_light;
        }
    }
    private static int blend(int a,int b,float t){int r=Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),v=Math.round((a&255)*(1-t)+(b&255)*t);return 0xff000000|(r<<16)|(g<<8)|v;}
    final int bg, panel, border, text, muted, accent, onAccent, error, chip,
        selected, userBubble, code, warning, warningText, attachment, success, errorSurface;
    static boolean isDark(String mode, boolean systemDark) {
        return "dark".equals(mode) || (!"light".equals(mode) && systemDark);
    }
    ThemePalette(boolean dark){this(dark,"sage");}
    ThemePalette(boolean dark,String colorTheme) {
        this.dark=dark;this.colorTheme=normalizeColor(colorTheme);int index=colorIndex(this.colorTheme);int tint=dark?DARK[index]:LIGHT[index];
        bg=blend(dark?0xff10131b:0xffffffff,tint,dark?.035f:.035f);
        panel=blend(dark?0xff1a1f2b:0xffffffff,tint,dark?.025f:.008f);
        border=blend(dark?0xff343b4d:0xffe5e7ee,tint,.20f);
        text=dark?0xfff2f5ff:0xff202839;
        muted=dark?0xffbac5d8:0xff5d6777;
        accent=tint;
        onAccent=dark?0xff111827:0xffffffff;
        error=dark?0xffffb19e:0xffb64c39;
        chip=blend(panel,tint,dark?.10f:.055f);
        selected=blend(panel,tint,dark?.14f:.035f);
        userBubble=blend(panel,tint,dark?.15f:.09f);
        code=blend(panel,tint,.065f);
        warning=dark?0xff342d20:0xfffff6e7;
        warningText=dark?0xffefc484:0xff936026;
        attachment=blend(panel,tint,.08f);
        success=dark?0xff83d9b5:0xff308464;
        errorSurface=dark?0xff3b2725:0xffffede8;
    }
}
