(function() {
  var port = null;
  var connected = false;

  function injectBridge() {
    var inject = document.createElement('script');
    inject.textContent = `
      (function() {
        window.TVBridge = {
          notifyVideoPlaying: function() {
            window.postMessage({type: '__TVBRIDGE__', method: 'notifyVideoPlaying'}, '*');
          },
          sendChannelList: function(data) {
            window.postMessage({type: '__TVBRIDGE__', method: 'sendChannelList', data: data}, '*');
          },
          sendProgramList: function(data) {
            window.postMessage({type: '__TVBRIDGE__', method: 'sendProgramList', data: data}, '*');
          }
        };
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
        window.LiteWebTV = {
          channelNodes: [],
          init: function() {
            this.setupVideoListener();
            setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractFreeChannels(); }, 2000);
            setTimeout(function() { if (window.LiteWebTV) window.LiteWebTV.extractPrograms(); }, 2000);
          },
          setupVideoListener: function() {
            document.addEventListener('playing', function(e) {
              if (e.target && e.target.tagName === 'VIDEO') {
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
            if (window.TVBridge) window.TVBridge.sendChannelList(JSON.stringify(results));
          },
          switchChannel: function(domIndex) {
            if (this.channelNodes[domIndex]) {
              this.channelNodes[domIndex].click();
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
            if (window.TVBridge) window.TVBridge.sendProgramList(JSON.stringify(results));
          }
        };
        window.LiteWebTV.init();
      })();
    `;
    document.documentElement.appendChild(script);
  }

  function ensurePort() {
    if (!connected) {
      try {
        port = browser.runtime.connectNative('tvbridge');
        connected = true;

        port.onMessage.addListener(function(msg) {
          if (msg && msg.command) {
            var cmdScript = document.createElement('script');
            cmdScript.textContent = msg.command;
            document.documentElement.appendChild(cmdScript);
          }
        });

        port.onDisconnect.addListener(function() {
          connected = false;
          port = null;
        });
      } catch(e) {
        console.error('TVBridge port connection failed:', e);
      }
    }
  }

  injectBridge();
  injectStylesAndLiteTV();
  ensurePort();

  window.addEventListener('message', function(event) {
    if (event.data && event.data.type === '__TVBRIDGE__') {
      if (connected && port) {
        port.postMessage(event.data);
      } else {
        try {
          browser.runtime.sendNativeMessage('tvbridge', event.data);
        } catch(e) {
          console.error('TVBridge sendNativeMessage failed:', e);
        }
      }
    }
  });

  document.addEventListener('DOMContentLoaded', function() {
    ensurePort();
  });
})();
