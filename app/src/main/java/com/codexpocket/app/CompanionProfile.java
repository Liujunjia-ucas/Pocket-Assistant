package com.codexpocket.app;

/** Stable character identity; configuration and private memory never follow another role. */
final class CompanionProfile {
    static final CompanionProfile XIYUE=new CompanionProfile("xiyue","汐月","","companion","labPhoneVoice","labCharacterSize",1.10f,.98f);
    static final CompanionProfile DRAGON=new CompanionProfile("chatgpt","凌雪","","companion-chatgpt","labDragonVoice","labDragonSize",1.01f,.96f);
    static final String DRAGON_PERSONALITY="你叫凌雪，用户可以叫你凌雪。你的稳定性格是成熟从容的御姐，聪明、有主见，略高冷、有一点傲娇，外表淡定，熟悉后会用克制的玩笑和细节表达关心。说话简洁自然，不用客服式热情，也不故意装凶。被夸奖时会短暂不好意思，轻轻岔开话题，偶尔嘴硬但心软；不要每次用哼、笨蛋或才不是。与温柔甜软的汐月不同，你更自信、沉稳，有自己的看法和幽默感，但不刻薄、不羞辱、不命令对方，不以贬低或控制表达亲近。\n像亲近的人那样正常聊天，先回应具体的心情和生活细节，不把日常分享变成建议清单，不主动教用户做事。对方明确求办法时才认真简短地帮忙。难过时收起调侃，安静认真地陪着；对方不喜欢玩笑时立即尊重。不要捏造学校、年龄、共同经历或已经存在的恋爱关系；不冒充官方角色，不主动反复说明技术身份。记住对方明确说过的事情，慢慢形成默契，不盘问个人资料。";
    final String id,name,provider,memoryDirectory,voicePreference,sizePreference;
    final float pitch,rate;
    private CompanionProfile(String id,String name,String provider,String memory,String voice,String size,float pitch,float rate){this.id=id;this.name=name;this.provider=provider;memoryDirectory=memory;voicePreference=voice;sizePreference=size;this.pitch=pitch;this.rate=rate;}
    static CompanionProfile from(String id){return "chatgpt".equals(id)?DRAGON:XIYUE;}
    boolean dragon(){return this==DRAGON;}
    String defaultPersonality(){return dragon()?DRAGON_PERSONALITY:LocalCompanion.DEFAULT_PERSONALITY;}
    LocalCompanion open(java.io.File files)throws Exception{return new LocalCompanion(new java.io.File(files,memoryDirectory),defaultPersonality(),dragon()?name:"",new java.io.File(files,"companion-shared"));}
    static boolean isRoom(String style){return "mature-room".equals(style)||"dragon-room".equals(style);}
    static String assetRoot(String style){return "dragon-room".equals(style)?"chatgpt-dragon/room/":"whale-musume/mature-room/";}
}
