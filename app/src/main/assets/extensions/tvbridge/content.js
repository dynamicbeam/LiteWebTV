(function() {
  console.log('[CS] start');

  var port = null;
  var connected = false;

  // TVBridge
  window.TVBridge = window.TVBridge || {
    notifyVideoPlaying: function() { window.postMessage({type:'__TVBRIDGE__',method:'notifyVideoPlaying'},'*'); },
    sendChannelList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendChannelList',data:d},'*'); },
    sendProgramList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendProgramList',data:d},'*'); },
  };
  console.log('[CS] bridge ready');

  // LiteWebTV
  function defineLiteTV() {
    window.LiteWebTV = {
      channelNodes: [],
      run: function() {
        try {
          var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
                    ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
                    "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
                    ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
                    "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
          var s = document.createElement('style'); s.type='text/css'; s.innerHTML=css;
          (document.head||document.documentElement).appendChild(s);
          console.log('[CS] css ok');
        } catch(e) { console.error('[CS] css fail:', e.message); }
        try {
          var _op = HTMLMediaElement.prototype.play;
          HTMLMediaElement.prototype.play = function() {
            try { var p=_op.call(this); if(p&&typeof p.catch==='function'){p.catch(function(e){if(e.name==='NotAllowedError')return;throw e;});} return p; }
            catch(e) { if(e.name==='NotAllowedError')return Promise.resolve(); throw e; }
          };
          console.log('[CS] play ok');
        } catch(e) { console.error('[CS] play fail:', e.message); }
        try {
          document.addEventListener('playing',function(e){
            if(e.target&&e.target.tagName==='VIDEO') window.TVBridge&&window.TVBridge.notifyVideoPlaying();
          },true);
          console.log('[CS] listener ok');
        } catch(e) { console.error('[CS] listener fail:', e.message); }
      },
      extract: function() {
        console.log('[CS] extract');
        try {
          var cr=[]; this.channelNodes=[];
          var cn=document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
          console.log('[CS] chan nodes:',cn.length,'| a:',document.querySelectorAll('a').length,'| li:',document.querySelectorAll('li').length);
          cn.forEach(function(n){
            var t=n.innerText||'';
            if(t.indexOf('VIP')===-1&&t.indexOf('限免')===-1){
              var c=t.replace('(VIP)','').replace('(限免)','').trim().split('\n')[0].trim();
              if(c.length>0){this.channelNodes.push(n);cr.push({name:c,domIndex:this.channelNodes.length-1});}
            }
          }.bind(this));
          console.log('[CS] chan:',cr.length);
          // 只发送非空数据，防止覆盖已有数据
          if(cr.length>0) window.TVBridge&&window.TVBridge.sendChannelList(JSON.stringify(cr));
        } catch(e) { console.error('[CS] chan err:',e.message); }
        try {
          var pr=[];
          var pi=document.querySelectorAll('.tv-zhan-list-b-r-item');
          console.log('[CS] prog nodes:',pi.length);
          pi.forEach(function(n){
            var now=n.classList.contains('now');
            var tm=n.querySelector('div:first-child');
            var tl=n.querySelector('.overflow-1');
            if(tm&&tl) pr.push({time:tm.innerText,title:tl.innerText,isPlaying:now});
          });
          console.log('[CS] prog:',pr.length);
          if(pr.length>0) window.TVBridge&&window.TVBridge.sendProgramList(JSON.stringify(pr));
        } catch(e) { console.error('[CS] prog err:',e.message); }
        try {
          var dbg={
            title:document.title,
            bodyCls:document.body?document.body.className:'',
            a:document.querySelectorAll('a').length,
            li:document.querySelectorAll('li').length,
            div:document.querySelectorAll('div').length
          };
          console.log('[CS] diag sent');
        } catch(e) { console.error('[CS] diag err:',e.message); }
      }
    };
  }
  defineLiteTV();
  console.log('[CS] lite ready');

  // port
  function connect() {
    if(connected) return;
    try {
      console.log('[CS] port connecting');
      port = browser.runtime.connectNative('tvbridge');
      connected = true;
      console.log('[CS] port ok');
      port.onMessage.addListener(function(msg) {
        console.log('[CS] cmd:', msg&&msg.command);
        if(msg&&msg.command) {
          // SPA导航后 LiteWebTV 可能丢失，自动重建
          if(!window.LiteWebTV) { console.warn('[CS] LiteWebTV lost, redefine'); defineLiteTV(); window.LiteWebTV.run(); }
          try { window.eval(msg.command); } catch(e) { console.error('[CS] eval:',e); }
        }
      });
      port.onDisconnect.addListener(function(){connected=false;port=null;console.log('[CS] port disc');});
    } catch(e) { console.error('[CS] port fail:',e); }
  }
  connect();
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',function(){connect();});

  // postMessage → native (用 sendNativeMessage 避免 port.postMessage 可能的问题)
  window.addEventListener('message', function(ev) {
    if(ev.data&&ev.data.type==='__TVBRIDGE__') {
      console.log('[CS] msg:', ev.data.method);
      try { browser.runtime.sendNativeMessage('tvbridge', ev.data); } catch(e) { console.error('[CS] native msg fail:', e.message); }
    }
  });

  // 立即执行 run
  window.LiteWebTV.run();

  // DOM 就绪后提取 + 监听 SPA 变化
  function onReady() {
    console.log('[CS] DOMContentLoaded');
    window.LiteWebTV.extract();
    // 页面渲染后重新提取（SPA 动态渲染）
    setTimeout(function(){window.LiteWebTV&&window.LiteWebTV.extract();},3000);
    // 监听 DOM 变化，SPA 切换页面时重新提取
    var observer = new MutationObserver(function(){
      console.log('[CS] DOM mutated');
      window.clearTimeout(window._extractTimer);
      window._extractTimer = setTimeout(function(){
        if(window.LiteWebTV){window.LiteWebTV.extract();}
      },1500);
    });
    observer.observe(document.body||document.documentElement,{childList:true,subtree:true});
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded', onReady);
  else onReady();

  console.log('[CS] end');
})();
