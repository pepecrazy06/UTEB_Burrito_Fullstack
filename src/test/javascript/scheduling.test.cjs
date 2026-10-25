const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const directory=path.resolve(__dirname,'../../main/resources/static');
const helpers=fs.readFileSync(path.join(directory,'assets/scheduling.js'),'utf8');
const app=fs.readFileSync(path.join(directory,'app.js'),'utf8');
const now=Date.parse('2026-10-25T01:00:00Z');
class FixedDate extends Date{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}}
function environment(){
 const values={type:'PICKUP',scheduleMode:'SCHEDULE',scheduledAt:'2026-10-26T11:00',contactName:'Khách kiểm thử',phone:'0901234567',address:'123 Test Street',addressId:'',voucher:'TEST5',note:'Giữ ghi chú',paymentMethod:'COD'};
 const elements=Object.fromEntries(Object.entries(values).map(([name,value])=>[name,{value,type:'text',parentElement:{hidden:false},setAttribute(){}}]));
 const form={elements,dataset:{},getAttribute(){return 'checkout-form';}};
 const hours={innerHTML:''},error={textContent:'',hidden:true},preview={innerHTML:''},delivery={hidden:false},calls=[];
 const state={user:{id:1},storeId:10,data:{stores:[{id:10,name:'Chi nhánh A',hours:'10:00–22:00'},{id:20,name:'Chi nhánh B',hours:'12:00–21:00'}]},cart:[{line:{storeId:10}}],checkoutKey:'test-checkout-key-0001'};
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const context=vm.createContext({state,Date:FixedDate,Intl,esc:escape,money:String,location:{hash:'#checkout'},
  document:{querySelector(selector){return {'#checkout-form':form,'#checkout-hours':hours,'#checkout-error':error,'#checkout-preview':preview,'#delivery-fields':delivery}[selector]||null;},addEventListener(){}},
  formData(f){return Object.fromEntries(Object.entries(f.elements).filter(([,input])=>!input.disabled).map(([name,input])=>[name,input.value]));},
  async api(url,method,body){calls.push({url,method,body});return {parts:[{store:state.data.stores[0],subtotal:100000,discount:0,deliveryFee:0}],total:100000};},
  async refreshCart(){calls.push('refreshCart');},async render(){calls.push('render');},async launchPayment(){calls.push('launchPayment');},toast(){},
 });
 vm.runInContext(helpers,context);
 vm.runInContext(app.slice(app.indexOf('async function updateCheckoutPreview()'),app.indexOf('function loginRequired()')),context);
 return {context,state,form,hours,error,preview,delivery,calls};
}
test('date bounds use Vietnam time even when the host timezone differs',()=>{
 const e=environment();assert.equal(e.context.vietnamDateTime(now),'2026-10-25T08:00');e.context.syncScheduleFields();
 assert.equal(e.form.elements.scheduledAt.min,'2026-10-25T08:31');assert.equal(e.form.elements.scheduledAt.max,'2026-11-01T07:59');
});
test('schedule mode requires a date; switching ASAP does not erase the previous choice',()=>{
 const e=environment();e.context.syncScheduleFields();assert.equal(e.form.elements.scheduledAt.required,true);assert.equal(e.form.elements.scheduledAt.parentElement.hidden,false);
 e.form.elements.scheduleMode.value='ASAP';e.context.syncScheduleFields();assert.equal(e.form.elements.scheduledAt.required,false);assert.equal(e.form.elements.scheduledAt.disabled,true);assert.equal(e.form.elements.scheduledAt.parentElement.hidden,true);assert.equal(e.form.elements.scheduledAt.value,'2026-10-26T11:00');
});
test('draft restores contact, address, payment, voucher, note and selected schedule',()=>{
 const e=environment();e.context.captureCheckoutDraft();for(const input of Object.values(e.form.elements))input.value='';e.context.restoreCheckoutDraft();
 for(const [name,value]of Object.entries(e.state.checkoutDraft.fields))assert.equal(e.form.elements[name].value,value);
});
test('another account cannot restore a private checkout draft',()=>{
 const e=environment();e.context.captureCheckoutDraft();e.state.user={id:2};e.form.elements.contactName.value='Other customer';e.context.restoreCheckoutDraft();assert.equal(e.form.elements.contactName.value,'Other customer');
});
test('hidden ASAP date is disabled for native validation but still survives a draft restore',()=>{
 const e=environment();e.form.elements.scheduledAt.value='2020-01-01T00:00';e.form.elements.scheduleMode.value='ASAP';e.context.syncScheduleFields();e.context.captureCheckoutDraft();
 assert.equal(e.form.elements.scheduledAt.disabled,true);assert.equal(e.state.checkoutDraft.fields.scheduledAt,'2020-01-01T00:00');
 e.form.elements.scheduledAt.value='';e.context.restoreCheckoutDraft();e.form.elements.scheduleMode.value='SCHEDULE';e.context.syncScheduleFields();
 assert.equal(e.form.elements.scheduledAt.value,'2020-01-01T00:00');assert.equal(e.form.elements.scheduledAt.disabled,false);
});
test('header method change works for guests and updates an authenticated checkout draft',async()=>{
 const e=environment();e.context.localStorage={setItem(){}};e.context.updateHeader=()=>{};
 vm.runInContext(app.slice(app.indexOf('async function actions(button)'),app.indexOf('function requireClient')),e.context);
 e.state.user=null;await e.context.actions({dataset:{action:'method',value:'DELIVERY'}});assert.equal(e.state.type,'DELIVERY');
 e.state.user={id:1};e.context.captureCheckoutDraft();await e.context.actions({dataset:{action:'method',value:'PICKUP'}});
 assert.equal(e.state.checkoutDraft.fields.type,'PICKUP');assert.equal(e.form.elements.type.value,'PICKUP');
});
test('hours come from actual cart branches, not the menu/header selection',()=>{
 const e=environment();e.state.storeId=20;e.context.showCheckoutHours();assert.match(e.hours.innerHTML,/Chi nhánh A/);assert.doesNotMatch(e.hours.innerHTML,/Chi nhánh B/);
 e.state.cart=[{line:{storeId:20}}];e.context.showCheckoutHours();assert.match(e.hours.innerHTML,/Chi nhánh B/);assert.doesNotMatch(e.hours.innerHTML,/Chi nhánh A/);
});
test('changing cart branches revalidates without deleting entered information',async()=>{
 const e=environment();await e.context.updateCheckoutPreview();e.state.cart=[{line:{storeId:20}}];e.context.api=async()=>{throw Error('Chi nhánh B chỉ nhận từ 12:00 đến 21:00.');};
 await e.context.updateCheckoutPreview();assert.match(e.error.textContent,/Chi nhánh B/);assert.equal(e.form.elements.contactName.value,'Khách kiểm thử');assert.equal(e.form.elements.scheduledAt.value,'2026-10-26T11:00');assert.equal(e.state.cart.length,1);
});
test('server preview errors are inline, escaped and cleared after a valid retry',async()=>{
 const e=environment();e.context.api=async()=>{throw Error('<script>invalid time</script>');};await e.context.updateCheckoutPreview();
 assert.equal(e.error.hidden,false);assert.equal(e.error.textContent,'<script>invalid time</script>');assert.doesNotMatch(e.preview.innerHTML,/<script>/);
 e.context.api=async()=>({parts:[{store:e.state.data.stores[0],subtotal:100000,discount:0,deliveryFee:0}],total:100000});await e.context.updateCheckoutPreview();assert.equal(e.error.hidden,true);
});
test('a stale preview response cannot overwrite the latest valid selection',async()=>{
 const e=environment();const responses=[];e.context.api=()=>new Promise((resolve,reject)=>responses.push({resolve,reject}));
 const old=e.context.updateCheckoutPreview();e.form.elements.scheduledAt.value='2026-10-26T12:00';const fresh=e.context.updateCheckoutPreview();
 responses[1].resolve({parts:[],total:123});await fresh;responses[0].reject(Error('old invalid time'));await old;
 assert.equal(e.error.hidden,true);assert.match(e.preview.innerHTML,/123/);
});
test('a preview started before submitting cannot clear a checkout rejection',async()=>{
 const e=environment();let finishPreview;
 e.context.api=async url=>url==='/checkout/preview'?new Promise(resolve=>{finishPreview=resolve;}):Promise.reject(Error('Giờ hẹn không còn hợp lệ.'));
 const preview=e.context.updateCheckoutPreview();vm.runInContext(app.slice(app.indexOf('async function submit(form)'),app.indexOf("document.addEventListener('submit'")),e.context);
 await assert.rejects(e.context.submit(e.form),/không còn hợp lệ/);finishPreview({parts:[],total:100000});await preview;
 assert.equal(e.error.textContent,'Giờ hẹn không còn hợp lệ.');assert.equal(e.error.hidden,false);
});
test('invalid checkout preserves draft, cart, key and payment without starting payment',async()=>{
 const e=environment();e.form.elements.paymentMethod.value='VNPAY';e.context.api=async()=>{throw Error('Chi nhánh chưa mở cửa. Vui lòng chọn lại.');};
 vm.runInContext(app.slice(app.indexOf('async function submit(form)'),app.indexOf("document.addEventListener('submit'")),e.context);
 await assert.rejects(e.context.submit(e.form),/chọn lại/);assert.equal(e.error.hidden,false);assert.equal(e.state.cart.length,1);assert.equal(e.state.checkoutKey,'test-checkout-key-0001');assert.equal(e.form.elements.paymentMethod.value,'VNPAY');assert.equal(e.form.elements.note.value,'Giữ ghi chú');assert.ok(e.state.checkoutDraft);assert.ok(!e.calls.includes('launchPayment'));assert.ok(!e.calls.includes('render'));
});
test('valid checkout submits the literal Vietnam time and clears draft only after success',async()=>{
 const e=environment();e.context.api=async(url,method,body)=>{e.calls.push({url,method,body});return [{id:42}];};
 vm.runInContext(app.slice(app.indexOf('async function submit(form)'),app.indexOf("document.addEventListener('submit'")),e.context);
 await e.context.submit(e.form);assert.equal(e.calls[0].body.scheduledAt,'2026-10-26T11:00');assert.equal(e.calls[0].body.paymentMethod,'COD');assert.equal(e.state.checkoutDraft,null);assert.equal(e.form.dataset.completed,'true');e.context.captureCheckoutDraft();assert.equal(e.state.checkoutDraft,null);assert.equal(e.context.location.hash,'#order/42');
});
