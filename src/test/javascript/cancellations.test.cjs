const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/assets/cancellations.js'),'utf8');
const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
function environment(roles='CUSTOMER'){
 const calls=[];const state={user:{id:1,storeId:5},data:{stores:[{id:5,name:'Test'}]},routeVersion:0};
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const context=vm.createContext({state,esc:escape,money:n=>`${n} VND`,date:String,statuses:{NEW:'Mới',CANCELLED:'Đã hủy',PAID:'Đã trả tiền'},has:(...allowed)=>allowed.some(role=>roles.split(',').includes(role)),
  document:{addEventListener(){}},async api(url,method,body){calls.push({url,method,body});return {request:{id:2,status:'REQUESTED',amount:100000,reason:'<script>bad</script>'}};},
  showModal(html){calls.push({html});},content:{querySelector(){return {maxLength:0,elements:{amount:{},refundedAt:{},reference:{},evidence:{},note:{}}};}},
  select:()=>'',textArea:()=>'',field:()=>'',localDate:()=>new Date().toISOString(),requireClient(ok,message){if(!ok)throw Error(message);},
  formData:form=>form.body,closeModal(){calls.push('closed');},async render(){calls.push('render');},toast:message=>calls.push({toast:message}),
 });
 vm.runInContext(source,context);return {context,state,calls};
}
const order={id:9,userId:1,storeId:5,status:'NEW',paymentStatus:'PAID',paymentMethod:'VNPAY'};
test('only owner with a paid online order before preparation can request cancellation',()=>{
 const {context}=environment();assert.equal(context.canRequestCancellation(order),true);
 for(const change of [{userId:2},{status:'PREPARING'},{status:'DELIVERING'},{status:'COMPLETED'},{paymentStatus:'PENDING'},{paymentStatus:'UNPAID'},{paymentMethod:'COD'},{cancellationStatus:'REQUESTED'},{cancellationStatus:'REJECTED'}])assert.equal(context.canRequestCancellation({...order,...change}),false);
});
test('approval is displayed as waiting for manual refund, not already refunded',()=>{
 const {context}=environment('ADMIN');const html=context.cancellationPanel({request:{status:'APPROVED',reason:'why',amount:100000}},order);
 assert.match(html,/Chờ hoàn tiền/);assert.match(html,/hệ thống chưa xác nhận đã trả tiền/);assert.match(html,/cancellation-refund/);assert.doesNotMatch(html,/Đã xác nhận hoàn 100000/);
});
test('proof, rejection reason and customer input are escaped as text',()=>{
 const {context}=environment();const html=context.cancellationPanel({request:{status:'REFUNDED',reason:'<script>bad</script>',decisionNote:'<b>note</b>',decidedAt:'date',evidence:'<img src=x onerror=bad()>',refundReference:'<script>proof</script>',confirmedAmount:100000}},order);
 assert.doesNotMatch(html,/<script>|<img|<b>/);assert.match(html,/&lt;script&gt;/);assert.match(html,/&lt;img/);assert.match(html,/Đã xác nhận hoàn/);
});
test('staff cannot see management controls or evidence in the summary response',()=>{
 const {context}=environment('STAFF');const html=context.cancellationPanel({status:'APPROVED'},order);assert.match(html,/Chờ hoàn tiền/);assert.doesNotMatch(html,/data-action|Bằng chứng/);
});
test('shop vendor branch manager and admin get decision buttons only within scope',()=>{
 for(const role of ['ADMIN','MANAGER','VENDOR']){
  const {context,state}=environment(role);const data={request:{status:'REQUESTED',reason:'why',amount:100000}};
  assert.equal(context.cancellationPanel(data,order).includes('cancellation-decision'),true);
  if(role!=='ADMIN'){state.user.storeId=6;assert.doesNotMatch(context.cancellationPanel(data,order),/cancellation-decision/);}
 }
});
test('management page requests the selected state from the backend',async()=>{
 const e=environment('ADMIN');e.state.cancellationFilter='APPROVED';e.context.api=async url=>{e.calls.push(url);return [];};
 const html=await e.context.cancellationAdminPage();assert.equal(e.calls[0],'/admin/cancellations?status=APPROVED');assert.match(html,/không có yêu cầu/i);
});
test('a stale processed request cannot open another decision form',async()=>{
 const e=environment('ADMIN');e.context.api=async()=>({request:{status:'REFUNDED'}});await assert.rejects(e.context.openCancellationDecision(9),/đã được xử lý/);assert.equal(e.calls.length,0);
});
test('new request, decision and confirmation submit to separate APIs',async()=>{
 for(const [id,url,method] of [['cancellation-request-form','/orders/9/cancellation','POST'],['cancellation-decision-form','/cancellations/2/decision','PUT'],['cancellation-refund-form','/cancellations/2/refund','PUT']]){
  const e=environment('ADMIN');vm.runInContext(app.slice(app.indexOf('async function submit(form)'),app.indexOf("document.addEventListener('submit'")),e.context);
  await e.context.submit({getAttribute(){return id;},body:{orderId:9,id:2,confirmed:true}});
  assert.equal(e.calls[0].url,url);assert.equal(e.calls[0].method,method);assert.ok(e.calls.includes('render'));
 }
});
