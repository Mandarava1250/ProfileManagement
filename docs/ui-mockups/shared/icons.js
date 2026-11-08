/* ===========================================================
   共用图标精灵 —— 24×24 线性图标，stroke=currentColor
   用法：<svg class="ic"><use href="#i-menu"/></svg>
   以经典脚本注入，file:// 直接双击打开也能用
   =========================================================== */
(function () {
  var P = {
    'i-menu':   '<path d="M4 7h16M4 12h16M4 17h16"/>',
    'i-search': '<circle cx="11" cy="11" r="6.5"/><path d="M15.8 15.8L20 20"/>',
    'i-plus':   '<path d="M12 5v14M5 12h14"/>',
    'i-filter': '<path d="M4 6.5h16M7 12h10M10 17.5h4"/>',
    'i-check':  '<path d="M5 12.5l4.6 4.6L19 6.8"/>',
    'i-trash':  '<path d="M5 7h14M9.5 7V4.8h5V7M7 7l1 12.2h8L17 7"/>',
    'i-edit':   '<path d="M4.5 19.5h4L19 9a2.1 2.1 0 0 0-3-3L5.5 16.5l-1 3z"/>',
    'i-left':   '<path d="M14.5 5.5L8 12l6.5 6.5"/>',
    'i-right':  '<path d="M9.5 5.5L16 12l-6.5 6.5"/>',
    'i-down':   '<path d="M6 9.5l6 6 6-6"/>',
    'i-up':     '<path d="M6 14.5l6-6 6 6"/>',
    'i-cal':    '<rect x="3.5" y="5.2" width="17" height="15" rx="3"/><path d="M8 3v4M16 3v4M3.5 10.2h17"/>',
    'i-clock':  '<circle cx="12" cy="12" r="8"/><path d="M12 7.4V12l3.4 2"/>',
    'i-wallet': '<path d="M3.5 8.5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v1"/><path d="M3.5 8.5v8a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-13"/><circle cx="16.6" cy="13.6" r="1.1"/>',
    'i-chart':  '<path d="M4 20V11M9.3 20V5M14.7 20v-6M20 20v-9M3 20h18"/>',
    'i-chartline':'<path d="M4 15.5l4.6-4.8 3.4 3 6-6.7"/><path d="M14.6 7h3.4v3.4"/>',
    'i-home':   '<path d="M4 10.8L12 4l8 6.8V19a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19v-8.2z"/>',
    'i-school': '<path d="M12 4l8.5 4.2L12 12.4 3.5 8.2 12 4z"/><path d="M6.8 10.4v4.8c0 1.6 2.3 2.9 5.2 2.9s5.2-1.3 5.2-2.9v-4.8"/>',
    'i-user':   '<circle cx="12" cy="8.2" r="3.6"/><path d="M4.8 20c0-4 3.2-6.2 7.2-6.2s7.2 2.2 7.2 6.2"/>',
    'i-gear':   '<circle cx="12" cy="12" r="3.1"/><path d="M12 2.6v3M12 18.4v3M2.6 12h3M18.4 12h3M5.4 5.4l2.1 2.1M16.5 16.5l2.1 2.1M18.6 5.4l-2.1 2.1M7.5 16.5l-2.1 2.1"/>',
    'i-more':   '<circle cx="5.2" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="18.8" cy="12" r="1.5" fill="currentColor" stroke="none"/>',
    'i-pin':    '<path d="M12 3.5l4.6 5.4-1.9 1.1v4.4l-2.7 2.6-2.7-2.6V10L7.4 8.9 12 3.5z"/><path d="M12 17v3.5"/>',
    'i-tag':    '<path d="M4 4.5h6.8l9.2 9.2-6.8 6.8L4 11.3V4.5z"/><circle cx="7.8" cy="8.3" r="1.3"/>',
    'i-folder': '<path d="M3.5 7.2h5.4l2 2.2h9.6v8.3a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7.2z"/>',
    'i-flag':   '<path d="M6 20.5V4h10.5l-2 3.8 2 3.8H6"/>',
    'i-sun':    '<circle cx="12" cy="12" r="4"/><path d="M12 2.6v2.2M12 19.2v2.2M2.6 12h2.2M19.2 12h2.2M5.4 5.4l1.6 1.6M17 17l1.6 1.6M18.6 5.4L17 7M7 17l-1.6 1.6"/>',
    'i-moon':   '<path d="M20 14.6A8.6 8.6 0 0 1 9.4 4a8.6 8.6 0 1 0 10.6 10.6z"/>',
    'i-palette':'<path d="M12 3.4a8.6 8.6 0 0 0 0 17.2c1.4 0 2-1 2-1.8 0-1.4-1.2-1.7-1.2-2.8 0-.9.8-1.5 1.8-1.5h1.5a4.5 4.5 0 0 0 4.5-4.5c0-3.7-3.7-6.6-8.6-6.6z"/><circle cx="7.8" cy="11" r="1.1" fill="currentColor" stroke="none"/><circle cx="11" cy="7.4" r="1.1" fill="currentColor" stroke="none"/><circle cx="15.6" cy="8.8" r="1.1" fill="currentColor" stroke="none"/>',
    'i-swap':   '<path d="M7 8.4h12l-3.2-3.2M17 15.6H5l3.2 3.2"/>',
    'i-jar':    '<path d="M7 8.6h10v10.1a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V8.6z"/><path d="M5.8 5.4h12.4v3.2H5.8z"/><path d="M10 12.4h4"/>',
    'i-down2':  '<path d="M12 4v10.5M7.8 10.4L12 14.6l4.2-4.2M5 19.4h14"/>',
    'i-up2':    '<path d="M12 19.4V8.9M7.8 13L12 8.8l4.2 4.2M5 4.6h14"/>',
    'i-target': '<circle cx="12" cy="12" r="8.2"/><circle cx="12" cy="12" r="4.2"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/>',
    'i-list':   '<path d="M5 7h14M5 12h14M5 17h9"/>',
    'i-spark':  '<path d="M12 3.6l1.7 4.9 4.9 1.7-4.9 1.7L12 16.8l-1.7-4.9L5.4 10.2l4.9-1.7L12 3.6z"/>',
    'i-book':   '<path d="M4.6 4.6h9.8a2.6 2.6 0 0 1 2.6 2.6v12.2H7.2a2.6 2.6 0 0 1-2.6-2.6V4.6z"/><path d="M7.2 19.4h9.8"/>',
    'i-bell':   '<path d="M6.5 10.4a5.5 5.5 0 0 1 11 0c0 4.4 1.6 5.6 1.6 5.6H4.9s1.6-1.2 1.6-5.6z"/><path d="M10.2 19a2 2 0 0 0 3.6 0"/>',
    'i-shield': '<path d="M12 3.4l7 2.4v5.4c0 4.2-3 7.4-7 8.9-4-1.5-7-4.7-7-8.9V5.8l7-2.4z"/>',
    'i-camera': '<path d="M3.6 8.6h3.3l1.4-2.4h7.4l1.4 2.4h3.3v9.6a1.6 1.6 0 0 1-1.6 1.6H5.2a1.6 1.6 0 0 1-1.6-1.6V8.6z"/><circle cx="12" cy="13.2" r="3.4"/>',
    'i-repeat': '<path d="M4 10.4A4.4 4.4 0 0 1 8.4 6H18M18 6l-2.8-2.8M18 6l-2.8 2.8"/><path d="M20 13.6a4.4 4.4 0 0 1-4.4 4.4H6M6 18l2.8 2.8M6 18l2.8-2.8"/>',
    'i-drag':   '<circle cx="9" cy="6" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="6" r="1.4" fill="currentColor" stroke="none"/><circle cx="9" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="9" cy="18" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="18" r="1.4" fill="currentColor" stroke="none"/>'
  };
  var out = '<svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">';
  for (var k in P) {
    out += '<symbol id="' + k + '" viewBox="0 0 24 24">' + P[k] + '</symbol>';
  }
  out += '</svg>';
  document.addEventListener('DOMContentLoaded', function () {
    document.body.insertAdjacentHTML('afterbegin', out);
  });
})();
