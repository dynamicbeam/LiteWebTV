(function() {
  try {
    var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
              ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
              "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
              ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
              "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
    var s = document.createElement('style'); s.type = 'text/css'; s.innerHTML = css;
    (document.head || document.documentElement).appendChild(s);
  } catch(e) { console.error('[CS] css fail:', e.message); }

  try {
    var _op = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function() {
      try {
        var p = _op.call(this);
        if (p && typeof p.catch === 'function') {
          p.catch(function(e) { if (e.name === 'NotAllowedError') return; throw e; });
        }
        return p;
      } catch(e) {
        if (e.name === 'NotAllowedError') return Promise.resolve();
        throw e;
      }
    };
  } catch(e) { console.error('[CS] play fail:', e.message); }

  window.TVBridge = window.TVBridge || {
    notifyVideoPlaying: function() { window.postMessage({type:'__TVBRIDGE__',method:'notifyVideoPlaying'},'*'); },
    sendChannelList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendChannelList',data:d},'*'); },
    sendProgramList: function(d) { window.postMessage({type:'__TVBRIDGE__',method:'sendProgramList',data:d},'*'); }
  };

  var port = null;
  var connected = false;

  function defineLiteWebTV() {
    if (window.LiteWebTV && window.LiteWebTV._defined) return;
    window.LiteWebTV = {
      _defined: true,
      channelNodes: [],
      init: function() {
        this.setupVideoListener();
        this.startAutoOptimizer();
      },
      startAutoOptimizer: function() {
        setTimeout(function() {
          window.LiteWebTV.extractFreeChannels();
          window.LiteWebTV.extractPrograms();
        }, 3000);

        var observer = new MutationObserver(function() {
          window.clearTimeout(window._extractTimer);
          window._extractTimer = setTimeout(function() {
            if (window.LiteWebTV) {
              window.LiteWebTV.extractFreeChannels();
              window.LiteWebTV.extractPrograms();
            }
          }, 3000);
        });
        if (document.body) {
          observer.observe(document.body, {childList: true, subtree: true});
        } else {
          document.addEventListener('DOMContentLoaded', function() {
            observer.observe(document.body, {childList: true, subtree: true});
          });
        }
      },
      setupVideoListener: function() {
        try {
          document.addEventListener('playing', function(e) {
            if (e.target && e.target.tagName === 'VIDEO' && window.TVBridge) {
              window.TVBridge.notifyVideoPlaying();
            }
          }, true);
        } catch(e) { console.error('[CS] listener fail:', e.message); }
      },
      extractFreeChannels: function() {
        try {
          var results = []; this.channelNodes = [];
          var nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
          nodes.forEach(function(node) {
            var text = node.innerText || "";
            if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
              var channelName = text.replace('(VIP)', '').replace('(限免)', '').trim();
              channelName = channelName.split('\n')[0].trim();
              this.channelNodes.push(node);
              results.push({ name: channelName, domIndex: this.channelNodes.length - 1 });
            }
          }.bind(this));
          if (results.length > 0 && window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
        } catch(e) { console.error('[CS] chan err:', e.message); }
      },
      switchChannel: function(domIndex) {
        try {
          var n = this.channelNodes[domIndex];
          if (n && n.isConnected !== false) {
            n.click();
          } else {
            var nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
            var idx = -1;
            nodes.forEach(function(node) {
              var text = node.innerText || '';
              if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
                idx++;
                if (idx === domIndex) node.click();
              }
            });
          }
        } catch(e) { console.error('[CS] switch err:', e.message); }
      },
      extractPrograms: function() {
        try {
          var results = [];
          var items = document.querySelectorAll('.tv-zhan-list-b-r-item');
          items.forEach(function(item) {
            var isNow = item.classList.contains('now');
            var timeNode = item.querySelector('div:first-child');
            var titleNode = item.querySelector('.overflow-1');
            if (timeNode && titleNode) results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: isNow });
          });
          if (results.length > 0 && window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
        } catch(e) { console.error('[CS] prog err:', e.message); }
      }
    };
  }
  defineLiteWebTV();

  function connect() {
    if (connected) return;
    try {
      port = browser.runtime.connectNative('tvbridge');
      connected = true;
      port.onMessage.addListener(function(msg) {
        var cmd = msg && msg.command;
        if (!cmd) return;
        var ltv = window.LiteWebTV;
        if (!ltv) {
          defineLiteWebTV();
          if (window.LiteWebTV && !window.LiteWebTV._inited) {
            window.LiteWebTV._inited = true;
            window.LiteWebTV.init();
          }
          window.LiteWebTV.extractFreeChannels();
          ltv = window.LiteWebTV;
        }
        if (!ltv) { console.warn('[CS] redefine failed'); return; }
        try {
          if (cmd.indexOf('extractFreeChannels') > -1) ltv.extractFreeChannels();
          else if (cmd.indexOf('extractPrograms') > -1) ltv.extractPrograms();
          else if (cmd.indexOf('switchChannel') > -1) {
            var m = cmd.match(/switchChannel\((\d+)\)/);
            if (m) ltv.switchChannel(parseInt(m[1]));
          } else { console.warn('[CS] unknown cmd:', cmd); }
        } catch(e) { console.error('[CS] cmd err:', e); }
      });
      port.onDisconnect.addListener(function() { connected = false; port = null; });
    } catch(e) { console.error('[CS] port fail:', e); }
  }
  connect();
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function() { connect(); });

  window.addEventListener('message', function(ev) {
    if (ev.data && ev.data.type === '__TVBRIDGE__') {
      try { browser.runtime.sendNativeMessage('tvbridge', ev.data); } catch(e) { console.error('[CS] native msg fail:', e.message); }
    }
  });

  if (!window.LiteWebTV._inited) {
    window.LiteWebTV._inited = true;
    window.LiteWebTV.init();
  }
})();