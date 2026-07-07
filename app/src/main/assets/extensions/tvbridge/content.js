(function() {
  var port = null;
  var connected = false;
  var connectionAttempts = 0;
  var maxConnectionAttempts = 3;

  function injectBridge() {
    var inject = document.createElement('script');
    inject.textContent = `
      (function() {
        // 以全局方式注册 TVBridge，确保在 GeckoView 中可访问
        if (!window.TVBridge) {
          window.TVBridge = {
            notifyVideoPlaying: function() {
              console.log('[TVBridge] Sending: notifyVideoPlaying');
              window.postMessage({type: '__TVBRIDGE__', method: 'notifyVideoPlaying'}, '*');
            },
            sendChannelList: function(data) {
              console.log('[TVBridge] Sending: sendChannelList with ' + data.length + ' channels');
              window.postMessage({type: '__TVBRIDGE__', method: 'sendChannelList', data: data}, '*');
            },
            sendProgramList: function(data) {
              console.log('[TVBridge] Sending: sendProgramList with ' + data.length + ' programs');
              window.postMessage({type: '__TVBRIDGE__', method: 'sendProgramList', data: data}, '*');
            }
          };
          console.log('[TVBridge] Bridge object initialized in page context');
        }
      })();
    `;
    document.documentElement.appendChild(inject);
  }

  function injectStylesAndLiteTV() {
    var script = document.createElement('script');
    script.textContent = `
      (function() {
        try {
          // Suppress NotAllowedError from video.play() (GeckoView autoplay policy)
          var _origPlay = HTMLMediaElement.prototype.play;
          HTMLMediaElement.prototype.play = function() {
            try {
              var _p = _origPlay.call(this);
              if (_p && typeof _p.catch === 'function') {
                _p.catch(function(e) {
                  if (e.name === 'NotAllowedError') return;
                  throw e;
                });
              }
              return _p;
            } catch(e) {
              if (e.name === 'NotAllowedError') return Promise.resolve();
              throw e;
            }
          };
          var css = "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; } " +
                    ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; } " +
                    "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; } " +
                    ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; } " +
                    "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }";
          var style = document.createElement('style'); style.type = 'text/css'; style.innerHTML = css; document.head.appendChild(style);
          window.LiteWebTV = {
            channelNodes: [],
            init: function() { this.setupVideoListener(); this.startAutoOptimizer(); setTimeout(() => this.extractFreeChannels(), 2000); setTimeout(() => this.extractPrograms(), 2000); },
            setupVideoListener: function() { document.addEventListener('playing', function(e) { if(e.target && e.target.tagName === 'VIDEO') { if(window.TVBridge) window.TVBridge.notifyVideoPlaying(); } }, true); },
            startAutoOptimizer: function() { setInterval(() => { let muteBtn = document.querySelector('.voice.off'); if (muteBtn && muteBtn.style.display !== 'none') { muteBtn.click(); } let qualityItems = document.querySelectorAll('.bei-list .item'); qualityItems.forEach(item => { if(item.innerText.includes('1080P') && !item.classList.contains('active')) { item.click(); } }); }, 3000); },
            extractFreeChannels: function() { let results = []; this.channelNodes = []; let nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb'); nodes.forEach((node) => { let text = node.innerText || ''; if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) { let channelName = text.replace('(VIP)', '').replace('(限免)', '').trim(); channelName = channelName.split('\\n')[0].trim(); this.channelNodes.push(node); results.push({ name: channelName, domIndex: this.channelNodes.length - 1 }); } }); if(window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results)); },
            switchChannel: function(domIndex) { if(this.channelNodes[domIndex]) { this.channelNodes[domIndex].click(); } },
            extractPrograms: function() { let results = []; let items = document.querySelectorAll('.tv-zhan-list-b-r-item'); items.forEach(item => { let isNow = item.classList.contains('now'); let timeNode = item.querySelector('div:first-child'); let titleNode = item.querySelector('.overflow-1'); if (timeNode && titleNode) { results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: isNow }); } }); if(window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results)); }
          };
          window.LiteWebTV.init();
        } catch (e) { console.error('LiteWebTV JS Injection Error: ' + e.message); }
      })();
    `;
    document.documentElement.appendChild(script);
  }

  function ensurePort() {
    if (!connected && connectionAttempts < maxConnectionAttempts) {
      try {
        connectionAttempts++;
        console.log('[ContentScript] Attempting to connect native port (attempt ' + connectionAttempts + ')');
        port = browser.runtime.connectNative('tvbridge');
        connected = true;
        console.log('[ContentScript] Native port connected successfully');

        port.onMessage.addListener(function(msg) {
          console.log('[ContentScript] Received message from native:', msg);
          if (msg && msg.command) {
            try {
              // 使用 eval 在主页面上下文中执行命令（比脚本标签更可靠）
              console.log('[ContentScript] Executing command: ' + msg.command.substring(0, 100));
              window.eval(msg.command);
            } catch (e) {
              console.error('[ContentScript] Error executing command:', e);
            }
          }
        });

        port.onDisconnect.addListener(function() {
          console.log('[ContentScript] Native port disconnected');
          connected = false;
          port = null;
        });
      } catch(e) {
        console.error('[ContentScript] Failed to connect native port:', e);
        connected = false;
      }
    } else if (!connected) {
      console.warn('[ContentScript] Max connection attempts reached');
    }
  }

  injectBridge();
  injectStylesAndLiteTV();
  ensurePort();

  // 监听页面脚本通过 postMessage 发送的消息
  window.addEventListener('message', function(event) {
    if (event.data && event.data.type === '__TVBRIDGE__') {
      console.log('[ContentScript] Received postMessage:', event.data.method);
      
      if (connected && port) {
        try {
          port.postMessage(event.data);
        } catch (e) {
          console.error('[ContentScript] Error posting message to port:', e);
        }
      } else {
        // 如果端口未连接，尝试直接发送原生消息
        try {
          console.log('[ContentScript] Port not connected, attempting sendNativeMessage');
          browser.runtime.sendNativeMessage('tvbridge', event.data);
        } catch(e) {
          console.error('[ContentScript] TVBridge sendNativeMessage failed:', e);
        }
      }
    }
  });

  // 确保 DOM 加载后端口已准备好
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      console.log('[ContentScript] DOMContentLoaded triggered, ensuring port');
      ensurePort();
    });
  } else {
    // DOM 已经加载过了
    console.log('[ContentScript] DOM already loaded, ensuring port');
    ensurePort();
  }
})();
