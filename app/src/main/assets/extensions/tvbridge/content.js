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
      // 尝试隐藏不需要的元素（支持多种变体）
      "[class*='header'], [class*='footer'], [class*='nav'], .header-fixed, .max-footer, .tv-main-con-r, .tv-zhan, .public { display: none !important; opacity: 0 !important; pointer-events: none !important; }",
      // 重置核心容器
      "html, body, #app, .comPadding, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l, [class*='container'], [class*='wrapper'] { margin: 0 !important; padding: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; overflow: hidden !important; background-color: #000 !important; }",
      // 视频容器优先级最高
      ".tv-main-con-l-vid, .c-container, .video-con, [class*='video'], [class*='player'], [id*='player'] { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; margin: 0 !important; padding: 0 !important; background-color: #000 !important; }",
      // video 标签全屏
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
            lastChannelCount: 0,
            lastProgramCount: 0,
            retryCount: 0,
            maxRetries: 5,
            
            init: function() {
              console.log('[LiteWebTV] Initializing...');
              this.setupVideoListener();
              
              // 立即尝试提取数据
              setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractFreeChannels(); }, 1000);
              setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractPrograms(); }, 1500);
              
              // 定期重试，直到成功获取数据
              setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.retryExtraction(); }, 3000);
            },
            
            retryExtraction: function() {
              this.retryCount++;
              if (this.retryCount > this.maxRetries) {
                console.warn('[LiteWebTV] Max retries reached');
                return;
              }
              
              console.log('[LiteWebTV] Retry extraction attempt ' + this.retryCount);
              this.extractFreeChannels();
              this.extractPrograms();
              
              // 如果数据还是为空，继续重试
              if ((this.lastChannelCount === 0 || this.lastProgramCount === 0) && this.retryCount < this.maxRetries) {
                setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.retryExtraction(); }, 2000);
              }
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
              
              // 尝试多种选择器以适应不同版本的网页
              var selectors = [
                '.tv-main-con-r-list-left-imga',
                '.tv-main-con-r-list-left-imgb',
                '[class*="tv-main-con-r-list"]',
                '.channel-item',
                'div[data-channel-id]'
              ];
              
              var nodes = [];
              for (var i = 0; i < selectors.length && nodes.length === 0; i++) {
                try {
                  nodes = document.querySelectorAll(selectors[i]);
                  if (nodes.length > 0) {
                    console.log('[LiteWebTV] Found ' + nodes.length + ' channel nodes using selector: ' + selectors[i]);
                  }
                } catch (e) {
                  console.warn('[LiteWebTV] Selector failed: ' + selectors[i]);
                }
              }
              
              if (nodes.length === 0) {
                console.warn('[LiteWebTV] No channel nodes found. Available selectors tried: ' + selectors.join(', '));
                console.log('[LiteWebTV] DOM 结构可能已改变，检查网页是否加载完成');
                if (window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
                return;
              }
              
              nodes.forEach(function(node) {
                var text = node.innerText || '';
                if (text.indexOf('VIP') === -1 && text.indexOf('限免') === -1) {
                  var channelName = text.replace('(VIP)', '').replace('(限免)', '').trim();
                  channelName = channelName.split('\\n')[0].trim();
                  if (channelName.length > 0) {
                    this.channelNodes.push(node);
                    results.push({ name: channelName, domIndex: this.channelNodes.length - 1 });
                  }
                }
              }.bind(this));
              
              this.lastChannelCount = results.length;
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
              
              // 尝试多种选择器以适应不同版本的网页
              var selectors = [
                '.tv-zhan-list-b-r-item',
                '.program-item',
                '[class*="program"]',
                'div[data-program-id]',
                '.schedule-item'
              ];
              
              var items = [];
              for (var i = 0; i < selectors.length && items.length === 0; i++) {
                try {
                  items = document.querySelectorAll(selectors[i]);
                  if (items.length > 0) {
                    console.log('[LiteWebTV] Found ' + items.length + ' program items using selector: ' + selectors[i]);
                  }
                } catch (e) {
                  console.warn('[LiteWebTV] Selector failed: ' + selectors[i]);
                }
              }
              
              if (items.length === 0) {
                console.warn('[LiteWebTV] No program items found. Available selectors tried: ' + selectors.join(', '));
                console.log('[LiteWebTV] DOM 结构可能已改变，检查网页是否加载完成');
                if (window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
                return;
              }
              
              items.forEach(function(item) {
                try {
                  var isNow = item.classList.contains('now') || item.classList.contains('playing') || item.classList.contains('active');
                  var timeNode = item.querySelector('div:first-child') || item.querySelector('[class*="time"]');
                  var titleNode = item.querySelector('.overflow-1') || item.querySelector('[class*="title"]') || item.querySelector('span:first-of-type');
                  
                  if (timeNode && titleNode) {
                    var time = (timeNode.innerText || '').trim();
                    var title = (titleNode.innerText || '').trim();
                    if (title.length > 0) {
                      results.push({ time: time, title: title, isPlaying: isNow });
                    }
                  }
                } catch (e) {
                  console.warn('[LiteWebTV] Error parsing program item:', e);
                }
              });
              
              this.lastProgramCount = results.length;
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
