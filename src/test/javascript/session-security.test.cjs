const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../main/resources/static');
const app=fs.readFileSync(path.join(root,'app.js'),'utf8');
const registration=fs.readFileSync(path.join(root,'assets/registration.js'),'utf8');

function environment(status=401){
 const calls=[],notices=[];
 const socket={reconnectDelay:5000,deactivate(){calls.push('deactivate');return Promise.resolve();}};
 const state={user:{email:'a@example.test'},data:{user:{email:'a@example.test'}},socket,csrf:'old',routeVersion:0,cart:[{private:true}],adminRows:[1],storeId:7};
 const context=vm.createContext({state,FormData,TextEncoder,JSON,Error,location:{hash:'#account'},clearTimeout(){},setTimeout(){},
  async fetch(url,options){calls.push({url,options});return {ok:status===200,status,async text(){return JSON.stringify({message:'request rejected'});}};},
  closeModal(){calls.push('closeModal');},updateHeader(){},async render(){calls.push('render');},toast(message){notices.push(message);},
  async reloadData(){calls.push('bootstrap');state.csrf='fresh';},
 });
 // Execute the production request/session functions with browser dependencies replaced.
 vm.runInContext(app.slice(app.indexOf('async function api('),app.indexOf('function field(')),context);
 return {context,state,socket,calls,notices};
}

test('401 clears private state, stops reconnect and refreshes CSRF before returning',async()=>{
 const e=environment();
 await assert.rejects(e.context.api('/account'),error=>error.status===401);
 assert.equal(e.state.user,null);assert.equal(e.state.socket,null);assert.equal(e.socket.reconnectDelay,0);
 assert.equal(e.state.data.user,undefined);assert.equal(e.state.cart.length,0);assert.equal(e.state.adminRows.length,0);
 assert.equal(e.state.csrf,'fresh');assert.equal(e.state.storeId,7);assert.equal(e.context.location.hash,'#login');
 assert.equal(e.calls.filter(call=>call==='deactivate').length,1);assert.ok(e.calls.includes('bootstrap'));
});
test('403 does not destroy authentication or redirect',async()=>{
 const e=environment(403);await assert.rejects(e.context.api('/account/profile','PUT',{}),error=>error.status===403);
 assert.equal(e.state.user.email,'a@example.test');assert.equal(e.state.socket,e.socket);assert.equal(e.context.location.hash,'#account');
});
test('successful password form clears login and displays the required Vietnamese message',async()=>{
 const e=environment(200);e.context.document={addEventListener(){}};e.context.formData=()=>({currentPassword:'old-password',password:'new-password'});
 vm.runInContext(app.slice(app.indexOf('async function submit(form)'),app.indexOf("document.addEventListener('submit'")),e.context);
 await e.context.submit({getAttribute(){return 'password-form';}});
 assert.equal(e.state.user,null);assert.equal(e.context.location.hash,'#login');assert.equal(e.socket.reconnectDelay,0);
 assert.equal(e.notices.at(-1),'Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.');
});
test('authentication endpoint errors do not enter an expiry redirect loop',async()=>{
 const e=environment();await assert.rejects(e.context.api('/auth/login','POST',{}));assert.equal(e.state.user.email,'a@example.test');assert.ok(!e.calls.includes('bootstrap'));
});
test('concurrent protected 401 responses share one cleanup',async()=>{
 const e=environment();await Promise.allSettled([e.context.api('/account'),e.context.api('/orders')]);
 assert.equal(e.calls.filter(call=>call==='deactivate').length,1);assert.equal(e.calls.filter(call=>call==='bootstrap').length,1);
});
test('a transport outage with a valid session does not log the user out',async()=>{
 const e=environment();e.context.api=async()=>({csrf:'renewed',user:{email:'a@example.test'}});
 await e.context.checkSocketSession(e.socket);assert.equal(e.state.user.email,'a@example.test');assert.equal(e.state.csrf,'renewed');
});
test('revoked socket stops reconnect and stale socket callbacks are ignored',async()=>{
 const e=environment();e.context.api=async()=>({csrf:'fresh'});
 await e.context.checkSocketSession({});assert.equal(e.state.socket,e.socket);
 await e.context.checkSocketSession(e.socket);assert.equal(e.state.user,null);assert.equal(e.socket.reconnectDelay,0);
});

for(const sameAccount of [true,false])test(`OTP reset ${sameAccount?'cleans target session':'preserves another logged-in account'}`,async()=>{
 const e=environment(200);const target=sameAccount?'A@example.test':'other@example.test';
 const error={hidden:true};
 const form={dataset:{},querySelector(){return error;},getAttribute(){return 'otp-form';},reportValidity(){return true;},elements:{password:{setCustomValidity(){}}}};
 e.context.document={querySelector(){return {reportValidity(){return true;},elements:{email:{value:target}}};}};
 e.context.updateOtpSendButton=()=>{};e.context.api=async path=>path==='/public/bootstrap'?{csrf:'fresh'}:{message:'success'};
 vm.runInContext(registration.slice(registration.indexOf('async function submitOtp('),registration.lastIndexOf("document.addEventListener('input'")),e.context);
 await e.context.submitOtp(form,{purpose:'RESET',otp:'123456',password:'new-password-123'});
 assert.equal(e.context.location.hash,'#login');assert.equal(e.state.csrf,'fresh');
 assert.equal(e.state.user?.email,sameAccount?undefined:'a@example.test');assert.equal(e.state.socket,sameAccount?null:e.socket);
});
