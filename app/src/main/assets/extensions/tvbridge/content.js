(function() {
  console.log('[CS] start');

  try {
    var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
              ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
              "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
              ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
              "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
    var s = document.createElement('style'); s.type='text/css'; s.innerHTML = css;
    (document.head || document.documentElement).appendChild(s);
    console.log('[CS] css ok');
  } catch(e) { console.error('[CS] css fail:', e.message); }

  try {
    var _op = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function() {
      try { var p = _op.call(this); if (p && typeof p.catch === 'function') { p.catch(function(e) { if (e.name === 'NotAllowedError') return; throw e; }); } return p; }
      catch(e) { if (e.name === 'NotAllowedError') return Promise.resolve(); throw e; }
    };
    console.log('[CS] play ok');
  } catch(e) { console.error('[CS] play fail:', e.message); }

  window.TVBridge = window.TVBridge || {
    notifyVideoPlaying: function() { window.postMessage({type:'__TVBRIDGE__',method:'notifyVideoPlaying'},'*'); },
    sendChannelList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendChannelList',data:d},'*'); },
    sendProgramList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendProgramList',data:d},'*'); }
  };
  console.log('[CS] bridge ready');

  var port = null;
  var connected = false;

  function defineLiteWebTV() {
    window.LiteWebTV = {
      channelNodes: [],
      init: function() {
        console.log('[CS] init');
        this.setupVideoListener();
        this.startAutoOptimizer();
        setTimeout(function() { window.LiteWebTV.extractFreeChannels(); }, 3000);
        setTimeout(function() { window.LiteWebTV.extractPrograms(); }, 3000);
      },
      startAutoOptimizer: function() {
        setTimeout(function() { window.LiteWebTV.extractFreeChannels(); }, 7000);
        setTimeout(function() { window.LiteWebTV.extractPrograms(); }, 7000);
        var observer = new MutationObserver(function() {
          console.log('[CS] DOM mutated');
          window.clearTimeout(window._extractTimer);
          window._extractTimer = setTimeout(function() {
            if (window.LiteWebTV) { window.LiteWebTV.extractFreeChannels(); window.LiteWebTV.extractPrograms(); }
          }, 3000);
        });
        if (document.body) observer.observe(document.body, {childList: true, subtree: true});
        else document.addEventListener('DOMContentLoaded', function() { observer.observe(document.body, {childList: true, subtree: true}); });
      },
      setupVideoListener: function() {
        try {
          document.addEventListener('playing', function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
              if (window.TVBridge) window.TVBridge.notifyVideoPlaying();
            }
          }, true);
          console.log('[CS] listener ok');
        } catch(e) { console.error('[CS] listener fail:', e.message); }
      },
      extractFreeChannels: function() {
        console.log('[CS] extractFreeChannels');
        try {
          var nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
          if (nodes.length === 0) { console.log('[CS] chan skip: no nodes'); return; }
          var results = []; var newChannelNodes = [];
          nodes.forEach(function(node) {
            var text = node.innerText || "";
            if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
              var channelName = text.replace('(VIP)', '').replace('(限免)', '').trim();
              channelName = channelName.split('\n')[0].trim();
              newChannelNodes.push(node);
              results.push({ name: channelName, domIndex: results.length });
            }
          }.bind(this));
          console.log('[CS] chan:', results.length, ' | channelNodes:', newChannelNodes.length);
          if (results.length > 0) {
            this.channelNodes = newChannelNodes;
            if (window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
          }
        } catch(e) { console.error('[CS] chan err:', e.message); }
      },
      switchChannel: function(domIndex) {
        console.log('[CS] switchChannel:', domIndex);
        try {
          if (this.channelNodes[domIndex]) this.channelNodes[domIndex].click();
        } catch(e) { console.error('[CS] switch err:', e.message); }
      },
      extractPrograms: function() {
        console.log('[CS] extractPrograms');
        try {
          var results = [];
          var items = document.querySelectorAll('.tv-zhan-list-b-r-item');
          items.forEach(function(item) {
            var isNow = item.classList.contains('now');
            var timeNode = item.querySelector('div:first-child');
            var titleNode = item.querySelector('.overflow-1');
            if (timeNode && titleNode) results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: isNow });
          });
          console.log('[CS] prog:', results.length);
          if (results.length > 0 && window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
        } catch(e) { console.error('[CS] prog err:', e.message); }
      }
    };
  }
  defineLiteWebTV();
  console.log('[CS] lite ready');

  function connect() {
    if (connected) return;
    try {
      console.log('[CS] port connecting');
      port = browser.runtime.connectNative('tvbridge');
      connected = true;
      console.log('[CS] port ok');
      port.onMessage.addListener(function(msg) {
        console.log('[CS] cmd:', msg && msg.command);
        if (msg && msg.command) {
          if (!window.LiteWebTV) { console.warn('[CS] LiteWebTV lost, redefine');
            defineLiteWebTV(); window.LiteWebTV.extractFreeChannels(); window.LiteWebTV.init();
          }
          try { window.eval(msg.command); } catch(e) { console.error('[CS] eval:', e); }
        }
      });
      port.onDisconnect.addListener(function() { connected = false; port = null; console.log('[CS] port disc'); });
    } catch(e) { console.error('[CS] port fail:', e); }
  }
  connect();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function() { connect(); });

  window.addEventListener('message', function(ev) {
    if (ev.data && ev.data.type === '__TVBRIDGE__') {
      console.log('[CS] msg:', ev.data.method);
      try { browser.runtime.sendNativeMessage('tvbridge', ev.data); } catch(e) { console.error('[CS] native msg fail:', e.message); }
    }
  });

  window.LiteWebTV.init();

  console.log('[CS] end');
})();
