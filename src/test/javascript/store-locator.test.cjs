const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/assets/store-locator.js'),'utf8');
function environment(){
  const state={storeId:1,data:{stores:[
    {id:1,name:'UTEB Thủ Đức',address:'Võ Văn Ngân',active:true,pickup:true,delivery:true,latitude:10.85,longitude:106.77},
    {id:2,name:'UTEB Quận 1',address:'Nguyễn Huệ',active:true,pickup:true,delivery:false,latitude:10.77,longitude:106.70},
    {id:3,name:'Chi nhánh ẩn',address:'Thủ Đức',active:false,pickup:true,delivery:true,latitude:10.86,longitude:106.77},
    {id:4,name:'UTEB chưa định vị',address:'Thành phố',active:true,pickup:false,delivery:true,latitude:0,longitude:0}
  ]}};
  const context=vm.createContext({state,esc:v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))});
  vm.runInContext(source,context);return {context,state};
}
test('stores appear immediately and are filtered by active status and pickup/delivery support',()=>{
  const {context:c}=environment();
  assert.deepEqual(Array.from(c.locatorStores('PICKUP'),s=>s.id),[1,2]);
  assert.deepEqual(Array.from(c.locatorStores('DELIVERY'),s=>s.id),[1,4]);
});
test('Vietnamese search accepts accents and unaccented names or addresses',()=>{
  const {context:c}=environment();
  for(const q of ['thu duc','Thủ Đức','vo van ngan'])assert.deepEqual(Array.from(c.locatorStores('PICKUP',q),s=>s.id),[1]);
  assert.equal(c.locatorStores('PICKUP','khong ton tai').length,0);
});
test('nearest sorting uses great-circle distance and leaves stores without coordinates last',()=>{
  const {context:c}=environment();
  assert.deepEqual(Array.from(c.locatorStores('PICKUP','',[10.77,106.70]),s=>s.id),[2,1]);
  assert.deepEqual(Array.from(c.locatorStores('DELIVERY','',[10.77,106.70]),s=>s.id),[1,4]);
  assert.equal(c.locatorDistance([10,106],[10,106]),0);
});
test('missing, zero, infinite and out-of-range positions are excluded from map markers',()=>{
  const {context:c}=environment();
  for(const s of [{},{latitude:null,longitude:106},{latitude:0,longitude:0},{latitude:91,longitude:106},{latitude:10,longitude:181},{latitude:'NaN',longitude:106}])assert.equal(c.locatorCoordinates(s),null);
  assert.deepEqual(Array.from(c.locatorCoordinates({latitude:10.85,longitude:106.77})),[10.85,106.77]);
});
test('store names and addresses are escaped and selection keeps the chosen fulfilment method',()=>{
  const {context:c,state}=environment();
  const s={...state.data.stores[0],name:'<img onerror=alert(1)>',address:'<script>bad</script>',hours:'10–22'};
  const html=c.locatorCard(s,'DELIVERY',null);
  assert.doesNotMatch(html,/<script>|<img onerror/);
  assert.match(html,/&lt;img/);assert.match(html,/data-value="DELIVERY"/);
  assert.match(html,/Cửa hàng đang chọn/);
});
test('locator has suggestions, a branded map and both delivery methods without requiring geolocation',()=>{
  const {context:c}=environment();const html=c.storeLocatorView();
  assert.match(html,/Cửa hàng gợi ý/);assert.match(html,/uteb-logo\.png/);
  assert.match(html,/locator-map/);assert.match(html,/data-value="PICKUP"/);assert.match(html,/data-value="DELIVERY"/);
});
test('tile failure switches provider once and preserves a usable list if both providers fail',()=>{
  const {context:c}=environment(),layers=[];
  c.L={tileLayer(url,options){const handlers={};const layer={url,options,handlers,removed:false,addTo(){layers.push(this);return this;},on(event,fn){handlers[event]=fn;return this;},remove(){this.removed=true;}};return layer;}};
  const status={hidden:true},controller={alive:true,map:{}};
  c.attachLocatorTiles(controller,status);
  layers[0].handlers.tileerror();layers[0].handlers.tileerror();
  assert.equal(layers.length,2);assert.equal(layers[0].removed,true);
  assert.match(layers[1].options.attribution,/OSM France/);
  layers[1].handlers.tileerror();assert.equal(status.hidden,false);assert.match(status.textContent,/danh sách/);
  layers[1].handlers.tileload();assert.equal(status.hidden,true);
});
