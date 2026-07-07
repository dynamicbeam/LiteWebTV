(function() {
  console.log('[CS] start');

  var port = null;
  var connected = false;

  // TVBridge
  window.TVBridge = window.TVBridge || {
    notifyVideoPlaying: function() { window.postMessage({type:'__TVBRIDGE__',method:'notifyVideoPlaying'},'*'); },
    sendChannelList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendChannelList',data:d},'*'); },
    sendProgramList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendProgramList',data:d},'*'); },
    sendDiagnosticInfo: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendDiagnosticInfo',data:d},'*'); }
  };
  console.log('[CS] bridge ready');

  // LiteWebTV
  window.LiteWebTV = {
    channelNodes: [],
    run: function() {
      console.log('[CS] run');
      // CSS
      try {
        var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
                  ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
                  "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
                  ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
                  "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
        var s = document.createElement('style');
        s.type = 'text/css';
        s.innerHTML = css;
        (document.head || document.documentElement).appendChild(s);
        console.log('[CS] css ok');
      } catch(e) { console.error('[CS] css fail:', e.message); }

      // play override
      try {
        var _op = HTMLMediaElement.prototype.play;
        HTMLMediaElement.prototype.play = function() {
          try { var p = _op.call(this); if(p&&typeof p.catch==='function'){p.catch(function(e){if(e.name==='NotAllowedError')return;throw e;});} return p; }
          catch(e) { if(e.name==='NotAllowedError')return Promise.resolve(); throw e; }
        };
        console.log('[CS] play ok');
      } catch(e) { console.error('[CS] play fail:', e.message); }

      // video listener
      try {
        document.addEventListener('playing', function(e) {
          if(e.target && e.target.tagName==='VIDEO') window.TVBridge && window.TVBridge.notifyVideoPlaying();
        }, true);
        console.log('[CS] listener ok');
      } catch(e) { console.error('[CS] listener fail:', e.message); }

      console.log('[CS] run done');
    },
    extract: function() {
      console.log('[CS] extract');
      // channels
      try {
        var cr = []; this.channelNodes = [];
        var cn = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
        console.log('[CS] chan nodes:', cn.length, '| a:', document.querySelectorAll('a').length, '| li:', document.querySelectorAll('li').length);
        cn.forEach(function(n) {
          var t = n.innerText||'';
          if(t.indexOf('VIP')===-1&&t.indexOf('限免')===-1) {
            var c = t.replace('(VIP)','').replace('(限免)','').trim().split('\n')[0].trim();
            if(c.length>0){this.channelNodes.push(n);cr.push({name:c,domIndex:this.channelNodes.length-1});}
          }
        }.bind(this));
        console.log('[CS] chan:', cr.length);
        window.TVBridge && window.TVBridge.sendChannelList(JSON.stringify(cr));
      } catch(e) { console.error('[CS] chan err:', e.message); }
      // programs
      try {
        var pr = [];
        var pi = document.querySelectorAll('.tv-zhan-list-b-r-item');
        console.log('[CS] prog nodes:', pi.length);
        pi.forEach(function(n) {
          var now = n.classList.contains('now');
          var tm = n.querySelector('div:first-child');
          var tl = n.querySelector('.overflow-1');
          if(tm&&tl) pr.push({time:tm.innerText,title:tl.innerText,isPlaying:now});
        });
        console.log('[CS] prog:', pr.length);
        window.TVBridge && window.TVBridge.sendProgramList(JSON.stringify(pr));
      } catch(e) { console.error('[CS] prog err:', e.message); }
      // diagnostic
      try {
        var dbg = {
          title: document.title,
          bodyCls: document.body ? document.body.className : '',
          a: document.querySelectorAll('a').length,
          li: document.querySelectorAll('li').length,
          div: document.querySelectorAll('div').length,
          texts: Array.from(document.querySelectorAll('a')).slice(0,8).map(function(x){return(x.innerText||'').trim()}).filter(function(x){return x.length>0}),
          classes: Array.from(document.querySelectorAll('[class]')).slice(0,5).map(function(x){return x.className.substring(0,60)})
        };
        window.TVBridge && window.TVBridge.sendDiagnosticInfo(JSON.stringify(dbg));
        console.log('[CS] diag sent');
      } catch(e) { console.error('[CS] diag err:', e.message); }
    }
  };
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
          if(!window.LiteWebTV) { console.warn('[CS] LiteWebTV missing, reinit'); window.LiteWebTV={channelNodes:[],extractFreeChannels:function(){},switchChannel:function(){},extractPrograms:function(){}}; }
          try { window.eval(msg.command); } catch(e) { console.error('[CS] eval:',e); }
        }
      });
      port.onDisconnect.addListener(function(){connected=false;port=null;console.log('[CS] port disc');});
    } catch(e) { console.error('[CS] port fail:',e); }
  }
  connect();
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',function(){connect();});

  // postMessage → port
  window.addEventListener('message', function(ev) {
    if(ev.data&&ev.data.type==='__TVBRIDGE__') {
      console.log('[CS] msg:', ev.data.method, 'port:', !!port, 'conn:', connected);
      if(connected&&port) {
        try { port.postMessage(ev.data); console.log('[CS] port.send ok'); }
        catch(e) { console.error('[CS] port.send fail:', e.message); }
      }
    }
  });

  // 立即执行 run (注入CSS/覆写play/设置监听器)
  window.LiteWebTV.run();

  // DOM 就绪后提取数据
  function onReady() {
    console.log('[CS] DOMContentLoaded');
    window.LiteWebTV.extract();
    setTimeout(function(){window.LiteWebTV&&window.LiteWebTV.extract();}, 3000);
    setTimeout(function(){window.LiteWebTV&&window.LiteWebTV.extract();}, 8000);
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded', onReady);
  else onReady();

  console.log('[CS] end');
})();
