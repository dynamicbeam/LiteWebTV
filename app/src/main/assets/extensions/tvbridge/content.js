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
    var style = document.createElement('style');
    style.textContent = [
      "::-webkit-scrollbar { display: none !important; width: 0 !important; height: 0 !important; }",
      ".header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; }",
      "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; }",
      ".tv-main-con-l-vid, .c-container, .video-con { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; }",
      "video { width: 100vw !important; height: 100vh !important; object-fit: contain !important; }"
    ].join(' ');
    document.documentElement.appendChild(style);

    var script = document.createElement('script');
    script.textContent = `
      (function() {
        // 确保 LiteWebTV 在全局作用域中
        if (!window.LiteWebTV) {
          window.LiteWebTV = {
            channelNodes: [],
            init: function() {
              console.log('[LiteWebTV] Initializing...');
              this.setupVideoListener();
              setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractFreeChannels(); }, 2000);
              setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractPrograms(); }, 2000);
            },
            setupVideoListener: function() {
              console.log('[LiteWebTV] Setting up video listener');
              document.addEventListener('playing', function(e) {
                if (e.target && e.target.tagName === 'VIDEO') {
                  console.log('[LiteWebTV] Video playing event detected');
                  if (window.TVBridge) window.TVBridge.notifyVideoPlaying();
                }
              }, true);
            },
            extractFreeChannels: function() {
              var results = [];
              this.channelNodes = [];
              var nodes = document.querySelectorAll('.tv-main-con-r-list-left-imga, .tv-main-con-r-list-left-imgb');
              nodes.forEach(function(node) {
                var text = node.innerText || '';
                if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
                  var channelName = text.replace('(VIP)', '').replace('(限免)', '').trim();
                  channelName = channelName.split('\\n')[0].trim();
                  this.channelNodes.push(node);
                  results.push({ name: channelName, domIndex: this.channelNodes.length - 1 });
                }
              }.bind(this));
              console.log('[LiteWebTV] Extracted ' + results.length + ' free channels');
              if (window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
            },
            switchChannel: function(domIndex) {
              console.log('[LiteWebTV] Switching to channel at index: ' + domIndex);
              if (this.channelNodes[domIndex]) {
                this.channelNodes[domIndex].click();
              } else {
                console.warn('[LiteWebTV] Channel index out of range: ' + domIndex);
              }
            },
            extractPrograms: function() {
              var results = [];
              var items = document.querySelectorAll('.tv-zhan-list-b-r-item');
              items.forEach(function(item) {
                var isNow = item.classList.contains('now');
                var timeNode = item.querySelector('div:first-child');
                var titleNode = item.querySelector('.overflow-1');
                if (timeNode && titleNode) {
                  results.push({ time: timeNode.innerText, title: titleNode.innerText, isPlaying: isNow });
                }
              });
              console.log('[LiteWebTV] Extracted ' + results.length + ' programs');
              if (window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
            }
          };
          console.log('[LiteWebTV] Object initialized in page context');
          window.LiteWebTV.init();
        }
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
