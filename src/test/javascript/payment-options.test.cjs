const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const helpers=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/assets/vnpay.js'),'utf8');
const app=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');
function environment(configured){
 const calls=[],state={user:{id:1,name:'Test'},cart:[{available:true}],data:{vnpayConfigured:configured},type:'PICKUP'};
 const context=vm.createContext({state,URL,crypto:{randomUUID:()=> 'test-key'},
  select(name,label,options,selected){return `<select name="${name}">${options.map(([value,text])=>`<option value="${value}"${value===selected?' selected':''}>${text}</option>`).join('')}</select>`;},
  requireClient(ok,message){if(!ok)throw Error(message);},field:()=>'',textArea:()=>'',page:(title,subtitle,html)=>html,
  async refreshCart(){},async api(){return {addresses:[]};},location:{assign(url){calls.push(url);}},
 });
 vm.runInContext(helpers,context);
 vm.runInContext(app.slice(app.indexOf('async function checkoutPage()'),app.indexOf('async function updateCheckoutPreview()')),context);
 return {context,state,calls};
}
for(const configured of [true,false])test(`checkout always shows COD and VNPay, without MoMo (configured=${configured})`,async()=>{
 const {context}=environment(configured);const html=await context.checkoutPage();
 assert.match(html,/value="COD"/);assert.match(html,/value="VNPAY"/);assert.doesNotMatch(html,/MOMO|MoMo/);
 assert.equal(html.includes('VNPay hiện chưa sẵn sàng'),!configured);
});
test('COD works without merchant credentials while VNPay is blocked with an explanation',()=>{
 const {context}=environment(false);assert.doesNotThrow(()=>context.validateCheckoutPayment('COD'));
 assert.throws(()=>context.validateCheckoutPayment('VNPAY'),/chưa sẵn sàng/);
});
test('configured VNPay is allowed and the removed MoMo method is rejected',()=>{
 const {context}=environment(true);assert.doesNotThrow(()=>context.validateCheckoutPayment('VNPAY'));
 assert.throws(()=>context.validateCheckoutPayment('MOMO'),/không hợp lệ/);
});
test('payment redirects only to the VNPay sandbox',async()=>{
 const {context,calls}=environment(true);context.api=async()=>({url:'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1',simulation:false});
 await context.launchPayment(1);assert.equal(calls.length,1);
 context.api=async()=>({url:'https://test-payment.momo.vn/pay',simulation:false});
 await assert.rejects(context.launchPayment(1),/không hợp lệ/);assert.equal(calls.length,1);
});
test('a simulated response cannot be used to confirm payment',async()=>{
 const {context,calls}=environment(true);context.api=async()=>({simulation:true});
 await assert.rejects(context.launchPayment(1),/không hỗ trợ thanh toán giả lập/);assert.equal(calls.length,0);
});
