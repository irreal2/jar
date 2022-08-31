package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Misc;
import com.github.catvod.utils.okhttp.OKCallBack;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.util.Iterator;

import okhttp3.Call;

public class XBiubiu extends Spider {

    /**
     * 分类配置
     */
    private JSONObject cateConfig;

    /**
     * 筛选配置
     */
    boolean isFilter = false;

    /**
     * 拉取首页推荐
     */
    boolean isHome = false;

    @Override
    public void init(Context context) {
        super.init(context);
    }

    public void init(Context context, String extend) {
        super.init(context, extend);
        this.ext = extend;
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            fetchRule();
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            String[] cates = getRuleVal("fenlei", "").split("#");
            if (getRuleVal("fenlei").isEmpty()) {
                cates = getCate().split("#");
            }
            for (String cate : cates) {
                String[] info = cate.split("\\$");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type_name", info[0]);
                jsonObject.put("type_id", info[1]);
                classes.put(jsonObject);
            }
            result.put("class", classes);

            String filterName = "";
            if (rule.optJSONObject("筛选") != null || !getRuleVal("筛选").isEmpty()) {
                filterName = "筛选";
            } else if  (rule.optJSONObject("filter") != null) {
                filterName = "filter";
            } else if  (rule.optJSONObject("filterdata") != null) {
                filterName = "filterdata";
            }
            JSONObject filterJson = rule.optJSONObject(filterName);
            String strFilter = getRuleVal(filterName);
            isFilter = strFilter.equals("汉字") || strFilter.equals("拼音") || strFilter.equals("数字") || strFilter.equals("自定义") || filterJson != null;
            if (filter && isFilter) {
                if (filterJson != null) {
                    result.put("filters", filterJson);
                } else {
                    switch (strFilter) {
                        case "汉字": filterJson = new JSONObject(cnFilter);
                        case "拼音": filterJson = new JSONObject(pyFilter);
                        case "数字": filterJson = new JSONObject(numFilter);
                    }
                    result.put("filters", filterJson);
                //    result.put("filters", getFilterData());
                }
            }
            return result.toString();
        } catch (
                Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    protected HashMap<String, String> getHeaders(String url) {
        HashMap<String, String> headers = new HashMap<>();
        String ua = getRuleVal("ua", Misc.UaWinChrome).trim();
        if (ua.isEmpty())
            ua = Misc.UaWinChrome;
        headers.put("User-Agent", ua);
        return headers;
    }

    @Override
    public String homeVideoContent() {
        try {
            fetchRule();
            if (getRuleVal("shouye").equals("0")) {
            } else {
                isHome = true;
                JSONObject result =  category("", "", false, new HashMap<>());
                isHome = false;
                return result.toString();
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

//获取分类页网址
    protected String categoryUrl(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String cateUrl = getRuleVal("分类页");
        if (cateUrl.contains("||")) {
            cateUrl = cateUrl.split("||")[0];
        }
        if (filter && isFilter && extend != null && extend.size() > 0) {
            for (Iterator<String> it = extend.keySet().iterator(); it.hasNext(); ) {
                String key = it.next();
                String value = extend.get(key);
                if (value.length() > 0) {
                    cateUrl = cateUrl.replace("{" + key + "}", URLEncoder.encode(value));
                }
            }
        }
        cateUrl = cateUrl.replace("{cateId}", tid).replace("{catePg}", pg);
        Matcher m = Pattern.compile("\\{(.*?)\\}").matcher(cateUrl);
        while (m.find()) {
            String n = m.group(0).replace("{", "").replace("}", "");
            cateUrl = cateUrl.replace(m.group(0), "").replace("/" + n + "/", "");
        }
        return cateUrl;
    }

    private JSONObject category(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            fetchRule();
            String webUrl;
            String cateUrl = getRuleVal("分类页");
            if (isHome) {
                webUrl = getRuleVal("url");
            } else {
                if (tid.equals("空"))
                    tid = "";
                String qishiye = getRuleVal("qishiye", "nil");
                if (qishiye.equals("空"))
                    pg = "";
                else if (!qishiye.equals("nil")) {
                    pg = String.valueOf(Integer.parseInt(pg) - 1 + Integer.parseInt(qishiye));
                }
                if (getRuleVal("fenlei").isEmpty()) {
                    if (cateUrl.contains("||") && pg.equals("1") && cateUrl.split("||")[1].startsWith("http") && qishiye.equals("nil")) {
                       webUrl = cateUrl.split("||")[1].replace("{cateId}", tid);
                    } else {
                        webUrl = categoryUrl(tid, pg, filter, extend);
                    }
                } else {
                    webUrl = getRuleVal("url") + tid + pg + getRuleVal("houzhui");
                }
            }
            String html = fetch(webUrl);
            html = removeUnicode(html);
            String parseContent = html;
            boolean shifouercijiequ = getRuleVal("shifouercijiequ").equals("1");
            if (shifouercijiequ) {
                String jiequqian = getRuleVal("jiequqian");
                String jiequhou = getRuleVal("jiequhou");
                parseContent = subContent(html, jiequqian, jiequhou).get(0);
            }
            String jiequshuzuqian = getRuleVal("jiequshuzuqian");
            String jiequshuzuhou = getRuleVal("jiequshuzuhou");
            JSONArray videos = new JSONArray();
            ArrayList<String> jiequContents = subContent(parseContent, jiequshuzuqian, jiequshuzuhou);
            for (int i = 0; i < jiequContents.size(); i++) {
                try {
                    String jiequContent = jiequContents.get(i);
                    String title = removeHtml(subContent(jiequContent, getRuleVal("biaotiqian"), getRuleVal("biaotihou")).get(0));
                    String pic = "";
                    String tupianqian = getRuleVal("tupianqian").toLowerCase();
                    if (tupianqian.startsWith("http://") || tupianqian.startsWith("https://")) {
                        pic = getRuleVal("tupianqian");
                    } else {
                        pic = subContent(jiequContent, getRuleVal("tupianqian"), getRuleVal("tupianhou")).get(0);
                    }
                    pic = Misc.fixUrl(webUrl, pic);
                    String link = subContent(jiequContent, getRuleVal("lianjieqian"), getRuleVal("lianjiehou")).get(0);
                    link = getRuleVal("ljqianzhui").isEmpty() ? (link + getRuleVal("ljhouzhui")) : (getRuleVal("ljqianzhui")) + link + getRuleVal("ljhouzhui");
                    String remark = !getRuleVal("fubiaotiqian").isEmpty() && !getRuleVal("fubiaotihou").isEmpty() ?
                            removeHtml(subContent(jiequContent, getRuleVal("fubiaotiqian"), getRuleVal("fubiaotihou")).get(0)) : "";
                    JSONObject v = new JSONObject();
                    v.put("vod_id", title + "$$$" + pic + "$$$" + link);
                    v.put("vod_name", title);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", remark);
                    videos.put(v);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            JSONObject result = new JSONObject();
            if (!isHome) {
                result.put("page", pg);
                result.put("pagecount", Integer.MAX_VALUE);
                result.put("limit", 90);
                result.put("total", Integer.MAX_VALUE);
            }
            result.put("list", videos);
            return result;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    private static String removeUnicode(String str) {
        Pattern pattern = Pattern.compile("(\\\\u(\\w{4}))");
        Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            String full = matcher.group(1);
            String ucode = matcher.group(2);
            char c = (char) Integer.parseInt(ucode, 16);
            str = str.replace(full, c + "");
        }
        return str;
    }

    String removeHtml(String text) {
        return Jsoup.parse(text).text();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JSONObject obj = category(tid, pg, filter, extend);
        return obj != null ? obj.toString() : "";
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            fetchRule();
            String[] idInfo = ids.get(0).split("\\$\\$\\$");
            String webUrl = (idInfo[2].startsWith("http") || idInfo[2].startsWith("magnet")) ? idInfo[2] : getRuleVal("url") + idInfo[2];
            String html = fetch(webUrl);
            String parseContent = html;
            boolean bfshifouercijiequ = getRuleVal("bfshifouercijiequ").equals("1");
            if (bfshifouercijiequ) {
                String jiequqian = getRuleVal("bfjiequqian");
                String jiequhou = getRuleVal("bfjiequhou");
                parseContent = subContent(html, jiequqian, jiequhou).get(0);
            }

            ArrayList<String> playList = new ArrayList<>();
            boolean isMagnet = false;
            boolean playDirect = getRuleVal("直接播放").equals("1");
            if (!playDirect) {
                String jiequshuzuqian = getRuleVal("bfjiequshuzuqian");
                String jiequshuzuhou = getRuleVal("bfjiequshuzuhou");
                boolean bfyshifouercijiequ = getRuleVal("bfyshifouercijiequ").equals("1");
                ArrayList<String> jiequContents = subContent(parseContent, jiequshuzuqian, jiequshuzuhou);
                for (int i = 0; i < jiequContents.size(); i++) {
                    try {
                        String jiequContent = jiequContents.get(i);
                        String parseJqContent = bfyshifouercijiequ ? subContent(jiequContent, getRuleVal("bfyjiequqian"), getRuleVal("bfyjiequhou")).get(0) : jiequContent;
                        ArrayList<String> lastParseContents = subContent(parseJqContent, getRuleVal("bfyjiequshuzuqian"), getRuleVal("bfyjiequshuzuhou"));
                        List<String> vodItems = new ArrayList<>();
                        for (int j = 0; j < lastParseContents.size(); j++) {
                            String title = subContent(lastParseContents.get(j), getRuleVal("bfbiaotiqian"), getRuleVal("bfbiaotihou")).get(0);
                            String link = subContent(lastParseContents.get(j), getRuleVal("bflianjieqian"), getRuleVal("bflianjiehou")).get(0);
                            String bfqianzhui = getRuleVal("bfqianzhui");
                            if (!bfqianzhui.isEmpty()) {
                                link = bfqianzhui + link;
                            }
                            vodItems.add(title + "$" + link);
                            if (link.startsWith("magnet")) {
                                isMagnet = true;
                                break;
                            }
                        }
                        playList.add(TextUtils.join("#", vodItems));
                        if (isMagnet) {
                            break;
                            }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }		  
                }

            } else {
                playList.add(idInfo[0] + "$" + idInfo[2]);
                if (idInfo[2].startsWith("magnet")) {
                    isMagnet = true;
                }																		  
            }
		   
            String cover = idInfo[1], title = idInfo[0], desc = "", category = "", area = "", year = "", remark = "", director = "", actor = "";

            if (!getRuleVal("leixinqian").isEmpty() && !getRuleVal("leixinhou").isEmpty()) {
                try {
                    category = subContent(html, getRuleVal("leixinqian"), getRuleVal("leixinhou")).get(0).replaceAll("\\s+", "").replaceAll("\\&[a-zA-Z]{1,10};", "").replaceAll("<[^>]*>", "").replaceAll("[(/>)<]", "");
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            if (!getRuleVal("niandaiqian").isEmpty() && !getRuleVal("niandaihou").isEmpty()) {
                try {
                    year = subContent(html, getRuleVal("niandaiqian"), getRuleVal("niandaihou")).get(0).replaceAll("\\s+", "").replaceAll("\\&[a-zA-Z]{1,10};", "").replaceAll("<[^>]*>", "").replaceAll("[(/>)<]", "");
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            if (!getRuleVal("zhuangtaiqian").isEmpty() && !getRuleVal("zhuangtaihou").isEmpty()) {
                try {
                    remark = subContent(html, getRuleVal("zhuangtaiqian"), getRuleVal("zhuangtaihou")).get(0);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            if (!getRuleVal("zhuyanqian").isEmpty() && !getRuleVal("zhuyanhou").isEmpty()) {
                try {
                    actor = subContent(html, getRuleVal("zhuyanqian"), getRuleVal("zhuyanhou")).get(0).replaceAll("\\s+", "");
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            if (!getRuleVal("daoyanqian").isEmpty() && !getRuleVal("daoyanhou").isEmpty()) {
                try {
                    director = subContent(html, getRuleVal("daoyanqian"), getRuleVal("daoyanhou")).get(0).replaceAll("\\s+", "");
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            if (!getRuleVal("juqingqian").isEmpty() && !getRuleVal("juqinghou").isEmpty()) {
                try {
                    desc = subContent(html, getRuleVal("juqingqian"), getRuleVal("juqinghou")).get(0);
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }			 
            JSONObject vod = new JSONObject();
            vod.put("vod_id", ids.get(0));
            vod.put("vod_name", title);
            vod.put("vod_pic", cover);
            vod.put("type_name", category);
            vod.put("vod_year", year);
            vod.put("vod_area", area);
            vod.put("vod_remarks", remark);
            vod.put("vod_actor", actor);
            vod.put("vod_director", director);
            vod.put("vod_content", desc);
	       ArrayList<String> playFrom = new ArrayList<>();
           String xlparseContent = html;
           if (((getRuleVal("线路名标题后").isEmpty() || getRuleVal("线路名标题后").equals("空")) && (getRuleVal("xlbiaotihou").isEmpty() || getRuleVal("xlbiaotihou").equals("空"))) ){
               for (int i = 0; i < playList.size(); i++) {
                   playFrom.add("播放列表" + (i + 1));
               }
           }else{
               String xlshifouercijiequ = "xlshifouercijiequ";
               String xljiequqian = "xljiequqian";
               String xljiequhou = "xljiequhou";
               String xljiequshuzuqian = "xljiequshuzuqian";
               String xljiequshuzuhou = "xljiequshuzuhou";
               String xlbiaotiqian = "xlbiaotiqian";
               String xlbiaotihou = "xlbiaotihou";
               if (!getRuleVal("线路名标题前").isEmpty() && !getRuleVal("线路名标题后").equals("空")) {
                   xlshifouercijiequ = "线路名是否二次截取";
                   xljiequqian = "线路名截取前";
                   xljiequhou = "线路名截取后";
                   xljiequshuzuqian = "线路名截取数组前";
                   xljiequshuzuhou = "线路名截取数组后";
                   xlbiaotiqian = "线路名标题前";
                   xlbiaotihou = "线路名标题后";
               }
               if (getRuleVal(xlshifouercijiequ).equals("1")) {
                   xljiequqian = getRuleVal(xljiequqian);
                   xljiequhou = getRuleVal(xljiequhou);
                   xlparseContent = subContent(html, xljiequqian, xljiequhou).get(0);
               }

               xljiequshuzuqian = getRuleVal(xljiequshuzuqian);
               xljiequshuzuhou = getRuleVal(xljiequshuzuhou);
               ArrayList<String> xljiequContents = subContent(xlparseContent, xljiequshuzuqian, xljiequshuzuhou);
               for (int i = 0; i < playList.size(); i++) {
                   try {
                       String xltitle = subContent(xljiequContents.get(i), getRuleVal(xlbiaotiqian), getRuleVal(xlbiaotihou)).get(0);                     
                       playFrom.add(xltitle);
                   } catch (Throwable th) {
                       th.printStackTrace();
                       break;
                   }
               }           
           
           }
            String vod_play_from = TextUtils.join("$$$", playFrom);
            String vod_play_url = TextUtils.join("$$$", playList);
            vod.put("vod_play_from", vod_play_from);
            vod.put("vod_play_url", vod_play_url);

            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            fetchRule();
            String webUrl = (id.startsWith("http") || id.startsWith("magnet")) ? id : getRuleVal("url") + id;
            JSONObject result = new JSONObject();
            String user = getRuleVal("User");
            result.put("parse", 1);
            result.put("playUrl", "");
            if (!user.isEmpty()) {
               JSONObject headers = new JSONObject();
               user = user.contains("$") ? user.split("$")[1] : user;
               headers.put("User-Agent",user);
               result.put("header",headers.toString());
               System.out.println(result);
            }
            result.put("url", webUrl);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            fetchRule();
            boolean ssmoshiJson = getRuleVal("ssmoshi").equals("0");
            String webUrlTmp = getRuleVal("url") + getRuleVal("sousuoqian") + key + getRuleVal("sousuohou");
            String webUrl = webUrlTmp.split(";")[0];
            String webContent = webUrlTmp.contains(";post") ? fetchPost(webUrl) : fetch(webUrl);
            JSONObject result = new JSONObject();
            JSONArray videos = new JSONArray();
            if (ssmoshiJson) {
                JSONObject data = new JSONObject(webContent);
                JSONArray vodArray = data.getJSONArray("list");
                for (int j = 0; j < vodArray.length(); j++) {
                    JSONObject vod = vodArray.getJSONObject(j);
                    String name = vod.optString(getRuleVal("jsname")).trim();
                    String id = vod.optString(getRuleVal("jsid")).trim();
                    String pic = vod.optString(getRuleVal("jspic")).trim();
                    pic = Misc.fixUrl(webUrl, pic);
                    String link = getRuleVal("sousuohouzhui") + id;
                    link = getRuleVal("ssljqianzhui").isEmpty() ? (link + getRuleVal("ssljhouzhui")) : (getRuleVal("ssljqianzhui")) + link + getRuleVal("ssljhouzhui");
                    JSONObject v = new JSONObject();
                    v.put("vod_id", name + "$$$" + pic + "$$$" + link);
                    v.put("vod_name", name);
                    v.put("vod_pic", pic);
                    v.put("vod_remarks", "");
                    videos.put(v);
                }
            } else {
                String parseContent = webContent;
                boolean shifouercijiequ = getRuleVal("sousuoshifouercijiequ").equals("1");
                if (shifouercijiequ) {
                    String jiequqian = getRuleVal("ssjiequqian");
                    String jiequhou = getRuleVal("ssjiequhou");
                    parseContent = subContent(webContent, jiequqian, jiequhou).get(0);
                }
                String jiequshuzuqian = getRuleVal("ssjiequshuzuqian");
                String jiequshuzuhou = getRuleVal("ssjiequshuzuhou");
                ArrayList<String> jiequContents = subContent(parseContent, jiequshuzuqian, jiequshuzuhou);
                for (int i = 0; i < jiequContents.size(); i++) {
                    try {
                        String jiequContent = jiequContents.get(i);
                        String title = subContent(jiequContent, getRuleVal("ssbiaotiqian"), getRuleVal("ssbiaotihou")).get(0);
                        String pic = subContent(jiequContent, getRuleVal("sstupianqian"), getRuleVal("sstupianhou")).get(0);
                        pic = Misc.fixUrl(webUrl, pic);
                        String link = subContent(jiequContent, getRuleVal("sslianjieqian"), getRuleVal("sslianjiehou")).get(0);
                        link = getRuleVal("ssljqianzhui").isEmpty() ? (link + getRuleVal("ssljhouzhui")) : (getRuleVal("ssljqianzhui")) + link + getRuleVal("ssljhouzhui");
                        String remark = "";
                        if (!getRuleVal("ssfubiaotiqian").isEmpty() && !getRuleVal("ssfubiaotihou").isEmpty()) {
                            try {
                                remark = subContent(jiequContent, getRuleVal("ssfubiaotiqian"), getRuleVal("ssfubiaotihou")).get(0).replaceAll("\\s+", "").replaceAll("\\&[a-zA-Z]{1,10};", "").replaceAll("<[^>]*>", "").replaceAll("[(/>)<]", "");
                            } catch (Exception e) {
                                SpiderDebug.log(e);
                            }
                        }			 
                        JSONObject v = new JSONObject();
                        v.put("vod_id", title + "$$$" + pic + "$$$" + link);
                        v.put("vod_name", title);
                        v.put("vod_pic", pic);
                        v.put("vod_remarks", remark);
                        videos.put(v);
                    } catch (Throwable th) {
                        th.printStackTrace();
                        break;
                    }
                }
            }
            result.put("list", videos);
            return result.toString();
        } catch (
                Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    protected String ext = null;
    protected JSONObject rule = null;

    protected void fetchRule() {
        if (rule == null) {
            if (ext != null) {
                try {
                    if (ext.startsWith("http")) {
                        String json = OkHttpUtil.string(ext, null);
                        rule = new JSONObject(json);
                    } else {
                        rule = new JSONObject(ext);
                    }
                } catch (JSONException e) {
                }
            }
        }
    }

    protected String fetch(String webUrl) {
        SpiderDebug.log(webUrl);
        return OkHttpUtil.string(webUrl, getHeaders(webUrl)).replaceAll("\r|\n", "");
    }

    protected String fetchPost(String webUrl) {
        SpiderDebug.log(webUrl);
        OKCallBack.OKCallBackString callBack = new OKCallBack.OKCallBackString() {
            @Override
            protected void onFailure(Call call, Exception e) {

            }

            @Override
            protected void onResponse(String response) {
            }
        };
        OkHttpUtil.post(OkHttpUtil.defaultClient(), webUrl, callBack);
        return callBack.getResult().replaceAll("\r|\n", "");
    }

    private String getRuleVal(String key) {
        return getRuleVal(key, "");
    }

    private String getRuleVal(String key, String defaultVal) {
        String v = rule.optString(key);
        if (v.isEmpty() || v.equals("空"))
            return defaultVal;
        return v;
    }

    private String getCate() {
        String cate = getRuleVal("分类");
        String numCate = "电影$1#连续剧$2#综艺$3#动漫$4";
        String pyCate = "电影$dianying#连续剧$lianxuju#综艺$zongyi#动漫$dongman";
        String enCate = "电影$mov#连续剧$tv#综艺$zongyi#动漫$acg";
        String suffix = "", type = cate;
        try {
            if (cate.contains("$") && !cate.contains("||")) {
                return cate;
            } else if (cate.contains("||")) {
                type = cate.split("\\|\\|")[0];
                suffix = "#" + cate.split("\\|\\|")[1];
            }
            switch (type) {
                case "数字": return numCate + suffix;
                case "拼音": return pyCate + suffix;
                case "英文": return enCate + suffix;
            }
            return numCate;
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return numCate;
    }
/*
    private JSONObject getFilterData() {
        try {
            String cnClass = "电影$1#连续剧$2#综艺$3#动漫$4";
            String enClass = "电影$mov#连续剧$tv#综艺$zongyi#动漫$acg";
            String numClass = "电影$1#连续剧$2#综艺$3#动漫$4";
            String pyClass = "电影$dianying#连续剧$lianxuju#综艺$zongyi#动漫$dongman";
            String data = getRuleVal("小类");
            String suffix = "", cateTxt;
            String[] data = subClass.split("\\|\\|");
            boolean appointType = data[0].equals("汉字") || data[0].equals("数字") || data[0].equals("拼音") || data[0].equals("英文");
            if (data.contains("$") && !appointType) {
                cateTxt = data;
            } else if (cate.contains("||")) {
                type = cate.split("\\|\\|")[0];
                suffix = "#" + cate.split("\\|\\|")[1];
            }
            switch (type) {
                case "数字": cateTxt = numCate + suffix;
                case "拼音": return pyCate + suffix;
                case "英文": return enCate + suffix;
            }
            return JOSNObject(filterString);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return new JOSNObject(filterString);
    }
*/
    private ArrayList<String> subContent(String content, String startFlag, String endFlag) {
        ArrayList<String> result = new ArrayList<>();
        if (startFlag.isEmpty() && endFlag.isEmpty()) {
            result.add(content);
            return result;
        }
        try {
            Pattern pattern = Pattern.compile(escapeExprSpecialWord(startFlag) + "([\\S\\s]*?)" + escapeExprSpecialWord(endFlag));
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                result.add(matcher.group(1).trim());
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return result;
    }

    String escapeExprSpecialWord(String regexStr) {
        if (!regexStr.isEmpty()) {
            String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String key : fbsArr) {
                if (regexStr.contains(key)) {
                    regexStr = regexStr.replace(key, "\\" + key);
                }
            }
        }
        return regexStr;
    }

    //修复软件不支持的格式无法嗅探的问题
    @Override 
    public boolean manualVideoCheck() { 
        fetchRule();
        return !getRuleVal("嗅探词").isEmpty() || !getRuleVal("过滤词").isEmpty(); 
    } 
     
    @Override 
    public boolean isVideoFormat(String url) {
        fetchRule();
        url = url.toLowerCase();
        String[] videoFormatList = getRuleVal("嗅探词",".m3u8#.mp4#.flv#.mp3").split("#");
        String[] videoSniffList = getRuleVal("过滤词","=http#=https#=https%3a%2f#=http%3a%2f#.jpg#.png#.ico#.gif#.js").split("#");
        for (String format : videoFormatList) {
            if (url.contains(format)) {
                for (String sniff : videoSniffList) {
                    if (url.contains(sniff)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private final String cnFilter = "{\"1\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"古装\",\"v\":\"古装\"},{\"n\":\"战争\",\"v\":\"战争\"},{\"n\":\"爱情\",\"v\":\"爱情\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"科幻\",\"v\":\"科幻\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"历史\",\"v\":\"历史\"},{\"n\":\"悬疑\",\"v\":\"悬疑\"},{\"n\":\"恐怖\",\"v\":\"恐怖\"},{\"n\":\"经典\",\"v\":\"经典\"},{\"n\":\"冒险\",\"v\":\"冒险\"},{\"n\":\"武侠\",\"v\":\"武侠\"},{\"n\":\"儿童\",\"v\":\"儿童\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"法国\",\"v\":\"法国\"},{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"俄罗斯\",\"v\":\"俄罗斯\"},{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"},{\"n\":\"2003\",\"v\":\"2003\"},{\"n\":\"2002\",\"v\":\"2002\"},{\"n\":\"2001\",\"v\":\"2001\"},{\"n\":\"2000\",\"v\":\"2000\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"2\":[{\"key\":\"cateId\",\"name\":\"分类\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国产剧\",\"v\":\"13\"},{\"n\":\"港台剧\",\"v\":\"15\"},{\"n\":\"日韩剧\",\"v\":\"44\"},{\"n\":\"欧美剧\",\"v\":\"45\"}]},{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"古装\",\"v\":\"古装\"},{\"n\":\"战争\",\"v\":\"战争\"},{\"n\":\"喜剧\",\"v\":\"喜剧\"},{\"n\":\"家庭\",\"v\":\"家庭\"},{\"n\":\"犯罪\",\"v\":\"犯罪\"},{\"n\":\"动作\",\"v\":\"动作\"},{\"n\":\"奇幻\",\"v\":\"奇幻\"},{\"n\":\"剧情\",\"v\":\"剧情\"},{\"n\":\"历史\",\"v\":\"历史\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"法国\",\"v\":\"法国\"},{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"丹麦\",\"v\":\"丹麦\"},{\"n\":\"瑞典\",\"v\":\"瑞典\"},{\"n\":\"巴西\",\"v\":\"巴西\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"俄罗斯\",\"v\":\"俄罗斯\"},{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"比利时\",\"v\":\"比利时\"},{\"n\":\"爱尔兰\",\"v\":\"爱尔兰\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"},{\"n\":\"2003\",\"v\":\"2003\"},{\"n\":\"2002\",\"v\":\"2002\"},{\"n\":\"2001\",\"v\":\"2001\"},{\"n\":\"2000\",\"v\":\"2000\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"3\":[{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"},{\"n\":\"2003\",\"v\":\"2003\"},{\"n\":\"2002\",\"v\":\"2002\"},{\"n\":\"2001\",\"v\":\"2001\"},{\"n\":\"2000\",\"v\":\"2000\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"4\":[{\"key\":\"class\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国产\",\"v\":\"国产\"},{\"n\":\"日本\",\"v\":\"日本\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"},{\"n\":\"2003\",\"v\":\"2003\"},{\"n\":\"2002\",\"v\":\"2002\"},{\"n\":\"2001\",\"v\":\"2001\"},{\"n\":\"2000\",\"v\":\"2000\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"time\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}";

    private final String pyFilter = "{\"dianying\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"动作片\",\"v\":\"dongzuopian\"},{\"n\":\"喜剧片\",\"v\":\"xijupian\"},{\"n\":\"爱情片\",\"v\":\"aiqingpian\"},{\"n\":\"科幻片\",\"v\":\"kehuanpian\"},{\"n\":\"恐怖片\",\"v\":\"kongbupian\"},{\"n\":\"剧情片\",\"v\":\"juqingpian\"},{\"n\":\"战争片\",\"v\":\"zhanzhengpian\"},{\"n\":\"动画片\",\"v\":\"donghua\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"法国\",\"v\":\"法国\"},{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"俄罗斯\",\"v\":\"俄罗斯\"},{\"n\":\"其它\",\"v\":\"其它\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"dianshiju\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆剧\",\"v\":\"guochanju\"},{\"n\":\"港台剧\",\"v\":\"gangtaiju\"},{\"n\":\"日韩剧\",\"v\":\"rihanju\"},{\"n\":\"欧美剧\",\"v\":\"oumeiju\"},{\"n\":\"其他剧\",\"v\":\"qitaju\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"zongyi\":[{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"欧美\",\"v\":\"欧美\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"dongman\":[{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"欧美\",\"v\":\"欧美\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"},{\"n\":\"2011\",\"v\":\"2011\"},{\"n\":\"2010\",\"v\":\"2010\"},{\"n\":\"2009\",\"v\":\"2009\"},{\"n\":\"2008\",\"v\":\"2008\"},{\"n\":\"2007\",\"v\":\"2007\"},{\"n\":\"2006\",\"v\":\"2006\"},{\"n\":\"2005\",\"v\":\"2005\"},{\"n\":\"2004\",\"v\":\"2004\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}";

    private final String numFilter = "{\"1\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"1\"},{\"n\":\"动作片\",\"v\":\"6\"},{\"n\":\"喜剧片\",\"v\":\"7\"},{\"n\":\"爱情片\",\"v\":\"8\"},{\"n\":\"科幻片\",\"v\":\"9\"},{\"n\":\"恐怖片\",\"v\":\"10\"},{\"n\":\"剧情片\",\"v\":\"11\"},{\"n\":\"战争片\",\"v\":\"12\"},{\"n\":\"奇幻片\",\"v\":\"30\"},{\"n\":\"纪录片\",\"v\":\"31\"},{\"n\":\"动画片\",\"v\":\"33\"},{\"n\":\"惊悚片\",\"v\":\"34\"},{\"n\":\"冒险片\",\"v\":\"36\"},{\"n\":\"悬疑片\",\"v\":\"37\"},{\"n\":\"犯罪片\",\"v\":\"38\"},{\"n\":\"其他片\",\"v\":\"39\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"大陆\",\"v\":\"大陆\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"法国\",\"v\":\"法国\"},{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"德国\",\"v\":\"德国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"印度\",\"v\":\"印度\"},{\"n\":\"意大利\",\"v\":\"意大利\"},{\"n\":\"西班牙\",\"v\":\"西班牙\"},{\"n\":\"加拿大\",\"v\":\"加拿大\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"2\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"2\"},{\"n\":\"国产剧\",\"v\":\"13\"},{\"n\":\"香港剧\",\"v\":\"14\"},{\"n\":\"台湾剧\",\"v\":\"15\"},{\"n\":\"韩国剧\",\"v\":\"16\"},{\"n\":\"日本剧\",\"v\":\"20\"},{\"n\":\"欧美剧\",\"v\":\"21\"},{\"n\":\"泰国剧\",\"v\":\"61\"},{\"n\":\"海外剧\",\"v\":\"22\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"内地\",\"v\":\"内地\"},{\"n\":\"韩国\",\"v\":\"韩国\"},{\"n\":\"香港\",\"v\":\"香港\"},{\"n\":\"台湾\",\"v\":\"台湾\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"美国\",\"v\":\"美国\"},{\"n\":\"泰国\",\"v\":\"泰国\"},{\"n\":\"英国\",\"v\":\"英国\"},{\"n\":\"新加坡\",\"v\":\"新加坡\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"3\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"3\"},{\"n\":\"大陆综艺\",\"v\":\"23\"},{\"n\":\"港台综艺\",\"v\":\"24\"},{\"n\":\"国外综艺\",\"v\":\"25\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"内地\",\"v\":\"内地\"},{\"n\":\"港台\",\"v\":\"港台\"},{\"n\":\"日韩\",\"v\":\"日韩\"},{\"n\":\"欧美\",\"v\":\"欧美\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}],\"4\":[{\"key\":\"cateId\",\"name\":\"类型\",\"value\":[{\"n\":\"全部\",\"v\":\"4\"},{\"n\":\"国产动漫\",\"v\":\"26\"},{\"n\":\"日本动漫\",\"v\":\"27\"},{\"n\":\"欧美动漫\",\"v\":\"28\"},{\"n\":\"其他动漫\",\"v\":\"29\"}]},{\"key\":\"area\",\"name\":\"地区\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"国产\",\"v\":\"国产\"},{\"n\":\"日本\",\"v\":\"日本\"},{\"n\":\"欧美\",\"v\":\"欧美\"},{\"n\":\"其他\",\"v\":\"其他\"}]},{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2022\",\"v\":\"2022\"},{\"n\":\"2021\",\"v\":\"2021\"},{\"n\":\"2020\",\"v\":\"2020\"},{\"n\":\"2019\",\"v\":\"2019\"},{\"n\":\"2018\",\"v\":\"2018\"},{\"n\":\"2017\",\"v\":\"2017\"},{\"n\":\"2016\",\"v\":\"2016\"},{\"n\":\"2015\",\"v\":\"2015\"},{\"n\":\"2014\",\"v\":\"2014\"},{\"n\":\"2013\",\"v\":\"2013\"},{\"n\":\"2012\",\"v\":\"2012\"}]},{\"key\":\"by\",\"name\":\"排序\",\"value\":[{\"n\":\"时间\",\"v\":\"\"},{\"n\":\"人气\",\"v\":\"hits\"},{\"n\":\"评分\",\"v\":\"score\"}]}]}";
}