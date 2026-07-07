// background.js
// Blocks image requests and known tracker scripts like trace.min.js via webRequest

const blockedUrls = [
  '\\.(png|jpg|jpeg|svg)$',
  'trace.min.js'
].map(pat => new RegExp(pat, 'i'));

function shouldBlock(url) {
  try {
    for (let r of blockedUrls) {
      if (r.test(url)) return true;
    }
  } catch (e) {
    console.error('[tvbridge:background] shouldBlock error', e);
  }
  return false;
}

browser.webRequest.onBeforeRequest.addListener(
  function(details) {
    const url = details.url || '';
    if (shouldBlock(url)) {
      console.log('[tvbridge:background] Blocking request:', url);
      // Return blocking response
      return { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);

console.log('[tvbridge:background] background script initialized');
