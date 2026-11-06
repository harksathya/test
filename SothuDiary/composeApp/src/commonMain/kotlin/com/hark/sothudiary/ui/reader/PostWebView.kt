package com.hark.sothudiary.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** True where an embedded browser exists (Android WebView). */
expect val platformHasWebView: Boolean

/**
 * An embedded browser showing [url]. Cookies persist, so a login to Instagram / Facebook done here once is kept.
 * Each time [extractTick] changes, the page is read with [READER_JS] and the JSON string is passed to [onExtracted].
 */
@Composable
expect fun PostWebView(
    url: String,
    modifier: Modifier,
    extractTick: Int,
    onExtracted: (String?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
)

/** Reads caption, author, image and visible comments from an Instagram / Facebook page (best effort). */
const val READER_JS = """
(function(){
 function meta(p){var m=document.querySelector('meta[property="'+p+'"]')||document.querySelector('meta[name="'+p+'"]');return m?m.getAttribute('content'):null;}
 function txt(e){return e?((e.innerText||e.textContent||'')+'').trim():'';}
 var host=location.host, out={title:meta('og:title'),caption:null,author:null,image:meta('og:image'),comments:[]};
 var ogd=meta('og:description');
 function addC(a,t){ if(!t||t.length<2||out.comments.length>=15) return; if(out.caption&&out.caption.indexOf(t)>=0) return;
   for(var i=0;i<out.comments.length;i++){ if(out.comments[i].text===t) return; } out.comments.push({author:a||'',text:t}); }
 try{
 if(host.indexOf('instagram')>=0){
   var h1=document.querySelector('article h1, main h1, div[role="dialog"] h1, h1[dir="auto"]');
   if(h1) out.caption=txt(h1);
   var a=document.querySelector('article header a[href^="/"]:not([href*="/explore/"]), header a[role="link"]');
   if(a) out.author=txt(a);
   var img=document.querySelector('article img[srcset], main img[srcset], article video[poster]');
   if(img) out.image=img.currentSrc||img.src||img.getAttribute('poster')||out.image;
   document.querySelectorAll('ul li, ul > div > div').forEach(function(li){
     var u=li.querySelector('h3 a, h3, a[href^="/"] span');
     var best=''; li.querySelectorAll('span[dir="auto"], span._ap3a, div > span').forEach(function(s){var v=txt(s); if(v.length>best.length && v.length<1500) best=v;});
     var un=txt(u); if(un && best && best!==un) addC(un,best);
   });
 } else if(host.indexOf('facebook')>=0){
   var sel=['div[data-ad-preview="message"]','div[data-ad-comet-preview="message"]','div[data-testid="post_message"]','.story_body_container ._5rgt','.story_body_container','div[data-gt] p','article p'];
   for(var i=0;i<sel.length && !out.caption;i++){var t=txt(document.querySelector(sel[i])); if(t.length>15) out.caption=t;}
   var h=document.querySelector('h2 a, h3 a, strong a, header a'); if(h) out.author=txt(h);
   document.querySelectorAll('[aria-label^="Comment by"], div[data-sigil="comment"], div[data-sigil="comment-body"]').forEach(function(c){
     var name=''; var lab=c.getAttribute('aria-label'); if(lab){ name=lab.replace(/^Comment by\s*/,'').replace(/\s+(\d|a |an |about ).*$/,''); }
     var body=c.querySelector('div[dir="auto"], [data-sigil="comment-body"]')||c;
     if(!name){ var n=c.querySelector('a, strong'); name=txt(n); }
     var t=txt(body); if(name && t.indexOf(name)===0) t=t.substring(name.length).trim(); addC(name,t);
   });
 }
 }catch(e){}
 if(!out.caption && ogd) out.caption=ogd;
 if(!out.caption){ var best=''; document.querySelectorAll('h1,p,span[dir="auto"],div[dir="auto"]').forEach(function(e){var t=txt(e); if(t.length>best.length && t.length<4000) best=t;}); if(best.length>30) out.caption=best; }
 out.login = /login|accounts\/login|checkpoint/.test(location.href) || !!document.querySelector('input[name="password"], input[name="pass"], input[type="password"]');
 out.url=location.href;
 return JSON.stringify(out);
})()
"""
