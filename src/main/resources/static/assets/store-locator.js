'use strict';

const storeLocatorInstances = new Map();
const locatorLogo = '/assets/uteb-logo.png';
const locatorIcons = {
  pickup: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M5 8h14l1 13H4L5 8Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></svg>',
  delivery: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m3 15 2-7h13l3 7v4h-2m-14 0H3v-4Zm2 0h16M8 19h8"/><circle cx="6.5" cy="18" r="2"/><circle cx="17.5" cy="18" r="2"/></svg>',
  locate: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/><path d="M12 1v4m0 14v4M1 12h4m14 0h4"/></svg>'
};

function locatorCoordinates(s) {
  const lat = Number(s.latitude), lng = Number(s.longitude);
  return s.latitude != null && s.longitude != null && Number.isFinite(lat) && Number.isFinite(lng)
    && Math.abs(lat) <= 85 && Math.abs(lng) <= 180 && !(lat === 0 && lng === 0) ? [lat, lng] : null;
}

function locatorDistance(a, b) {
  const rad = n => n * Math.PI / 180;
  const x = Math.sin(rad(b[0] - a[0]) / 2) ** 2
    + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(rad(b[1] - a[1]) / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, x)));
}

function locatorStores(method, query = '', position = null) {
  const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase();
  const term = normalize(query.trim());
  const rows = (state.data?.stores || []).filter(s => s.active !== false
    && (method === 'DELIVERY' ? s.delivery : s.pickup) && normalize(s.name + ' ' + s.address).includes(term));
  if (position) rows.sort((a, b) => {
    const ac = locatorCoordinates(a), bc = locatorCoordinates(b);
    return (ac ? locatorDistance(position, ac) : Infinity) - (bc ? locatorDistance(position, bc) : Infinity);
  });
  return rows;
}

function storeLocatorView() {
  return `<section class="store-locator" aria-label="Chọn cửa hàng UTEB Burrito">
    <div class="locator-sidebar">
      <div class="locator-heading"><img src="${locatorLogo}" alt="" width="64" height="64"><div><span class="locator-eyebrow">UTEB BURRITO</span><h2>Chọn cửa hàng</h2></div></div>
      <div class="locator-methods" role="group" aria-label="Phương thức nhận món">
        <button type="button" data-locator-action="method" data-value="PICKUP">${locatorIcons.pickup}Đến lấy</button>
        <button type="button" data-locator-action="method" data-value="DELIVERY">${locatorIcons.delivery}Giao tận nơi</button>
      </div>
      <p class="locator-intro">Chọn một UTEB cho bữa ngon của bạn.</p>
      <label class="locator-search"><span class="visually-hidden">Tìm cửa hàng theo tên hoặc địa chỉ</span><input type="search" placeholder="Tên cửa hàng hoặc địa chỉ" autocomplete="off"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="10" cy="10" r="7"/><path d="m15 15 6 6"/></svg></label>
      <button class="locator-nearby" type="button" data-locator-action="locate">${locatorIcons.locate}<span>Dùng vị trí của tôi</span></button>
      <div class="locator-results-heading"><h3>Cửa hàng gợi ý</h3><span class="locator-count" role="status" aria-live="polite"></span></div>
      <div class="locator-results"></div>
      <p class="locator-location-status" role="status" aria-live="polite"></p>
    </div>
    <div class="locator-map-panel"><div class="locator-map" aria-label="Bản đồ các chi nhánh UTEB Burrito"></div>
      <div class="locator-map-caption"><img src="${locatorLogo}" alt="" width="32" height="32"><span>Tìm UTEB. Chọn bữa ngon.</span></div>
      <button class="locator-map-fit" type="button" data-locator-action="fit" aria-label="Xem tất cả cửa hàng trên bản đồ">${locatorIcons.locate}</button>
      <p class="locator-map-status" role="status" hidden></p>
    </div>
  </section>`;
}

function locatorCard(s, method, position) {
  const coords = locatorCoordinates(s);
  const distance = position && coords ? `<span class="locator-distance">${locatorDistance(position, coords).toLocaleString('vi-VN', {maximumFractionDigits: 1})} km</span>` : '';
  const directions = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(s.address);
  return `<article class="locator-card ${s.id === state.storeId ? 'is-selected' : ''}" data-store-id="${s.id}">
    <div class="locator-card-title"><button type="button" class="locator-store-name" data-locator-action="focus" data-id="${s.id}">${esc(s.name)}</button>${distance}</div>
    ${s.id === state.storeId ? '<span class="locator-selected-label">Cửa hàng đang chọn</span>' : ''}
    <p class="locator-address">${esc(s.address)}</p><p class="locator-hours">Giờ phục vụ: ${esc(s.hours)}</p>
    ${!coords ? '<p class="locator-hours">Chi nhánh chưa có tọa độ trên bản đồ.</p>' : ''}
    <div class="locator-card-actions"><button type="button" class="btn btn-primary" data-action="select-store" data-id="${s.id}" data-value="${method}">Chọn cửa hàng này</button><a href="${directions}" target="_blank" rel="noopener noreferrer">Chỉ đường ↗</a></div>
  </article>`;
}

function disposeStoreLocators(scope) {
  for (const [root, controller] of storeLocatorInstances) if (root === scope || scope.contains(root)) {
    controller.alive = false;
    controller.map?.remove();
    storeLocatorInstances.delete(root);
  }
}

function mountStoreLocator(container) {
  const root = container.querySelector('.store-locator');
  if (!root || storeLocatorInstances.has(root)) return;
  const c = {root, alive: true, method: state.type, query: '', position: null, map: null, markers: new Map()};
  storeLocatorInstances.set(root, c);
  const status = root.querySelector('.locator-map-status');
  if (window.L) {
    c.map = L.map(root.querySelector('.locator-map'), {zoomControl: false}).setView([10.81, 106.72], 11);
    L.control.zoom({position: 'bottomright'}).addTo(c.map);
    attachLocatorTiles(c, status);
  } else {
    status.hidden = false;
    status.textContent = 'Chưa tải được bản đồ. Bạn vẫn có thể chọn cửa hàng bên trái.';
  }
  refreshStoreLocator(c);
  root.addEventListener('input', e => {
    if (e.target.matches('input[type="search"]')) { c.query = e.target.value; refreshStoreLocator(c); }
  });
  // Leaflet stops popup clicks before they reach the document action handler.
  root.addEventListener('click', e => {
    const choice = e.target.closest('.locator-popup [data-action="select-store"]');
    if (choice) {
      e.stopPropagation();
      void selectStore(choice.dataset.id, choice.dataset.value).catch(error => toast(error.message));
    }
  }, true);
  root.addEventListener('click', e => {
    const button = e.target.closest('[data-locator-action]');
    if (!button) return;
    if (button.dataset.locatorAction === 'method') { c.method = button.dataset.value; refreshStoreLocator(c); }
    if (button.dataset.locatorAction === 'focus') focusLocatorStore(c, Number(button.dataset.id));
    if (button.dataset.locatorAction === 'fit') fitLocatorStores(c);
    if (button.dataset.locatorAction === 'locate') locateUserStores(c, button);
  });
  requestAnimationFrame(() => { if (c.alive) { c.map?.invalidateSize(); fitLocatorStores(c); } });
}

function attachLocatorTiles(c, status) {
  const credit = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors';
  const providers = [
    {url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', attribution: credit},
    {url: 'https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png', attribution: credit + ' · <a href="https://www.openstreetmap.fr/" target="_blank" rel="noopener noreferrer">OSM France</a>'}
  ];
  function load(index) {
    if (!c.alive) return;
    const provider = providers[index];
    const tiles = L.tileLayer(provider.url, {maxZoom: 19, attribution: provider.attribution}).addTo(c.map);
    let failed = false;
    tiles.on('tileerror', () => {
      if (failed || !c.alive) return;
      failed = true;
      if (index + 1 < providers.length) { tiles.remove(); load(index + 1); }
      else { status.hidden = false; status.textContent = 'Chưa tải được nền bản đồ. Bạn vẫn có thể chọn cửa hàng từ danh sách.'; }
    });
    tiles.on('tileload', () => { if (c.alive) status.hidden = true; });
  }
  load(0);
}

function refreshStoreLocator(c) {
  if (!c.alive) return;
  c.rows = locatorStores(c.method, c.query, c.position);
  c.root.querySelectorAll('[data-locator-action="method"]').forEach(b => {
    b.classList.toggle('active', b.dataset.value === c.method);
    b.setAttribute('aria-pressed', String(b.dataset.value === c.method));
  });
  c.root.querySelector('.locator-count').textContent = `${c.rows.length} chi nhánh`;
  c.root.querySelector('.locator-results-heading h3').textContent = c.position ? 'Gần bạn nhất' : 'Cửa hàng gợi ý';
  c.root.querySelector('.locator-results').innerHTML = c.rows.map(s => locatorCard(s, c.method, c.position)).join('')
    || '<div class="locator-empty"><h3>Chưa tìm thấy cửa hàng</h3><p>Thử tên hoặc địa chỉ khác, hoặc đổi phương thức nhận món.</p></div>';
  if (!c.map) return;
  for (const marker of c.markers.values()) marker.remove();
  c.markers.clear();
  for (const s of c.rows) {
    const coords = locatorCoordinates(s);
    if (!coords) continue;
    const icon = L.divIcon({className: 'uteb-map-marker', html: `<span class="uteb-map-pin ${s.id === state.storeId ? 'is-selected' : ''}"><img src="${locatorLogo}" alt=""></span>`, iconSize: [52, 62], iconAnchor: [26, 60], popupAnchor: [0, -54]});
    const marker = L.marker(coords, {icon, title: s.name, alt: s.name, keyboard: true}).addTo(c.map);
    marker.bindPopup(`<div class="locator-popup"><strong>${esc(s.name)}</strong><p>${esc(s.address)}</p><span>${esc(s.hours)}</span><button type="button" class="btn btn-primary" data-action="select-store" data-id="${s.id}" data-value="${c.method}">Chọn cửa hàng này</button></div>`);
    marker.on('click', () => highlightLocatorCard(c, s.id));
    c.markers.set(s.id, marker);
  }
  fitLocatorStores(c);
}

function fitLocatorStores(c) {
  if (!c.map || !c.markers.size) return;
  c.map.fitBounds(L.latLngBounds([...c.markers.values()].map(m => m.getLatLng())), {padding: [64, 64], maxZoom: 15});
}

function highlightLocatorCard(c, id) {
  c.root.querySelectorAll('.locator-card').forEach(card => card.classList.toggle('is-focused', Number(card.dataset.storeId) === id));
  c.root.querySelector(`.locator-card[data-store-id="${id}"]`)?.scrollIntoView({behavior: 'smooth', block: 'nearest'});
}

function focusLocatorStore(c, id) {
  highlightLocatorCard(c, id);
  const marker = c.markers.get(id);
  if (marker) { c.map.setView(marker.getLatLng(), 15); marker.openPopup(); }
}

function locateUserStores(c, button) {
  const status = c.root.querySelector('.locator-location-status');
  if (!navigator.geolocation) { status.textContent = 'Trình duyệt chưa hỗ trợ vị trí. Bạn có thể chọn trực tiếp trong danh sách.'; return; }
  button.disabled = true;
  status.textContent = 'Đang tìm cửa hàng gần bạn…';
  navigator.geolocation.getCurrentPosition(pos => {
    if (!c.alive) return;
    button.disabled = false;
    c.position = [pos.coords.latitude, pos.coords.longitude];
    refreshStoreLocator(c);
    if (c.map) {
      c.userMarker?.remove();
      c.userMarker = L.circleMarker(c.position, {radius: 7, color: '#451400', fillColor: '#fff', fillOpacity: 1, weight: 3}).addTo(c.map).bindTooltip('Vị trí của bạn');
    }
    status.textContent = 'Đã xếp theo khoảng cách đường thẳng từ vị trí của bạn.';
  }, () => {
    if (!c.alive) return;
    button.disabled = false;
    status.textContent = 'Chưa lấy được vị trí. Bạn vẫn có thể chọn cửa hàng trong danh sách.';
  }, {timeout: 10000, maximumAge: 60000});
}
