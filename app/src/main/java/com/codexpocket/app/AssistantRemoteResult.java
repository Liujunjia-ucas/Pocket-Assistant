package com.codexpocket.app;

import org.json.*;
import java.util.*;

/** Observe only the acknowledged turn, never a similarly worded earlier request. */
final class AssistantRemoteResult {
    static JSONObject read(JSONObject work,JSONObject snapshot)throws Exception{
        JSONObject t=snapshot.optJSONObject("thread");String turn=work.optString("turn");if(t==null||turn.isEmpty()||!"windows".equals(work.optString("route"))||!t.optString("id").equals(work.optString("chat"))||!Arrays.asList("running","approval").contains(work.optString("state")))return null;
        JSONArray items=t.optJSONArray("items"),approvals=snapshot.optJSONArray("approvals");String summary="";boolean found=false;for(int i=0;items!=null&&i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&turn.equals(item.optString("turnId"))){found=true;if("agentMessage".equals(item.optString("type")))summary=item.optString("text");}}
        boolean active=turn.equals(t.optString("activeTurn"));if(!found&&!active)return null;
        String state=active&&approvals!=null&&approvals.length()>0?"approval":active?"running":"complete";
        if(!active&&!turn.equals(t.optString("lastCompletedTurn"))&&summary.isEmpty())return null;
        if(!t.optString("error").isEmpty()&&(active||turn.equals(t.optString("lastCompletedTurn")))){state="failed";summary=t.optString("error");}
        if(!active&&turn.equals(t.optString("lastCompletedTurn"))&&"interrupted".equals(t.optString("status"))){state="interrupted";summary="电脑任务已停止，请打开工作区查看已保存的内容";}
        if(state.equals("complete")&&summary.isEmpty())summary="电脑已结束本次处理，请打开工作区查看文件和执行结果";
        summary=summary.substring(0,Math.min(1500,summary.length()));if(state.equals(work.optString("state"))&&summary.equals(work.optString("summary")))return null;
        return new JSONObject().put("state",state).put("chat",t.getString("id")).put("summary",summary);
    }
}
