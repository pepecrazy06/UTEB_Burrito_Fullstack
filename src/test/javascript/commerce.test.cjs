const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/assets/commerce.js','utf8');
function setup(){
 const state={storeId:1,category:'ALL',search:'gà & bò',sort:'favorite',page:2,data:{stores:[{id:1,name:'Shop A',deliveryFee:15000}],categories:[{name:'Chung',storeId:null},{name:'Riêng',storeId:1},{name:'Khác',storeId:2}],carriers:[{id:11,name:'Giao A',storeId:1,fee:22000},{id:22,name:'Giao B',storeId:2,fee:5000}],promotions:[],products:[],productStats:{7:{sold:12,favorites:3,rating:4.5,reviews:2}}}};
 const c=vm.createContext({state,URLSearchParams,Date,Number,document:{addEventListener(){}},esc:v=>String(v??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;'),money:v=>String(v??0),checkoutStores:()=>state.data.stores,select:(name,label,options)=>`${name}:${options.map(([v,l])=>v+'='+l).join('|')}`,requireClient:(ok,m)=>{if(!ok)throw Error(m);},product:()=>null,field:()=>'',textArea:()=>'',content:{querySelector:()=>({})}});
 vm.runInContext(source,c);return c;
}
test('catalog request keeps selected shop category ranking and page safely encoded',()=>{const c=setup(),q=new URLSearchParams(c.catalogParams());assert.equal(q.get('q'),'gà & bò');assert.equal(q.get('sort'),'favorite');assert.equal(q.get('storeId'),'1');assert.equal(q.get('size'),'20');assert.equal(q.get('page'),'2');});
test('category tabs exclude another shop private categories',()=>{const c=setup();assert.deepEqual(Array.from(c.catalogCategories(),x=>x.name),['Chung','Riêng']);});
test('shipping selector lists only carriers of each checkout shop',()=>{const c=setup(),html=c.carrierFields();assert.match(html,/Giao A/);assert.doesNotMatch(html,/Giao B/);assert.match(html,/carrier_1/);});
test('order filters include pickup return and refund stages and keep selected value',()=>{const html=setup().orderStatusFilter('user-order-filter','RETURNING');for(const state of ['PICKED_UP','RETURN_REQUESTED','RETURNING','RETURNED','REFUNDED'])assert.ok(html.includes(state));assert.match(html,/value="RETURNING" selected/);});
test('return instructions are escaped and customer send button appears only after approval',()=>{const c=setup(),r={kind:'RETURN',status:'APPROVED',returnInstructions:'<script>unsafe</script>',reason:'Lỗi món'};let html=c.refundPanel(r,{id:1},true);assert.match(html,/&lt;script>/);assert.match(html,/data-action="return-sent"/);html=c.refundPanel({...r,status:'REFUNDED'},{id:1},true);assert.doesNotMatch(html,/data-action="return-sent"/);assert.doesNotMatch(c.refundPanel(r,{id:1},false),/data-action="return-sent"/);});
test('return admin cannot skip from approval straight to refunded',()=>{const c=setup();c.state.adminRows=[{id:1,kind:'RETURN',status:'APPROVED'}];assert.throws(()=>c.openRefundDecision(1),/chờ khách gửi/);});
test('voucher choices exclude expired and exhausted vouchers',()=>{const c=setup();c.state.data.promotions=[{code:'GOOD',name:'Valid',active:true,used:0,usageLimit:2,startsAt:'2020-01-01',endsAt:'2099-01-01'},{code:'USED',name:'Used',active:true,used:2,usageLimit:2,startsAt:'2020-01-01',endsAt:'2099-01-01'},{code:'OLD',name:'Expired',active:true,used:0,usageLimit:2,startsAt:'2020-01-01',endsAt:'2021-01-01'}];const html=c.checkoutVouchers({});assert.match(html,/GOOD/);assert.doesNotMatch(html,/USED|OLD/);});
test('detail page displays every returned review page and records viewing for authenticated customer',async()=>{const c=setup(),calls=[];c.state.user={id:1};c.api=async(path,method,body)=>{calls.push({path,method,body});return {product:{id:7,name:'Món A',category:'Burrito',price:60000,stock:3,calories:400,image:'/assets/food.png'},store:{id:1,name:'Shop A'},stats:{rating:4.5,reviews:10,sold:12,favorites:3},reviews:[{text:'review 9'},{text:'review 10'}],reviewPages:2,reviewPage:2};};c.page=(title,subtitle,html)=>html;c.reviewCard=r=>r.text;const html=await c.productDetailPage(7,2);assert.match(html,/review 10/);assert.match(html,/#product\/7\/2/);assert.ok(calls.some(x=>x.path==='/marks/7'&&x.body.kind==='RECENT'));});
test('async route finishes loading before scrolling to the new product title',async()=>{
 const app=fs.readFileSync('src/main/resources/static/app.js','utf8');let handler,finish;const calls=[];
 const c=vm.createContext({window:{addEventListener:(name,callback)=>{handler=callback;},scrollTo:()=>calls.push('scroll')},closeModal(){},setMobileMenu(){},render:()=>new Promise(resolve=>{finish=resolve;})});
 vm.runInContext(app.slice(app.indexOf("window.addEventListener('hashchange'"),app.indexOf('(async()=>{try{await reloadData()')),c);
 const pending=handler();assert.deepEqual(calls,[]);finish();await pending;assert.deepEqual(calls,['scroll']);
});
