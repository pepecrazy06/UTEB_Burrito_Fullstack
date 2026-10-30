const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').resolve(__dirname,'../../main/resources/static/assets/ai-menu.js'),'utf8');
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function context(extra={}){const c=vm.createContext({state:{storeId:1},esc:escape,money:n=>n+'đ',...extra});vm.runInContext(source,c);return c;}
test('AI results escape model and catalog text instead of rendering injected HTML',()=>{
 const c=context();const html=c.aiMenuResults({message:'<script>bad</script>',criteria:['<img onerror=bad>'],items:[{name:'<b>Food</b>',image:'x" onerror="bad',category:'Bowl',price:89000,calories:370,customization:'<svg onload=bad>',reason:'<script>x</script>'}]});
 assert.ok(!html.includes('<script>'));assert.ok(!html.includes('<svg'));assert.ok(html.includes('&lt;b&gt;Food'));assert.ok(html.includes('x&quot; onerror=&quot;bad'));
});
test('stale suggestion from another branch cannot open the builder',async()=>{
 let opened=false;const c=context({requireClient:(ok,text)=>{if(!ok)throw Error(text);},openBuilder:()=>{opened=true;}});
 c.state.aiMenu={result:{items:[{storeId:2,productId:4}]}};
 await assert.rejects(c.chooseAiMenu(0),/Cửa hàng đã thay đổi/);assert.equal(opened,false);
});
test('accepting a suggestion preserves the exact ingredient IDs in the builder',async()=>{
 let opened;const p={id:1,storeId:1,available:true,stock:3};
 const c=context({requireClient:(ok,text)=>{if(!ok)throw Error(text);},reloadData:async()=>{},product:()=>p,openBuilder:(...args)=>{opened=args;},calculateBuilder:()=>({price:89000,kcal:370}),toast:()=>{}});
 c.state.data={ingredients:[{id:4,available:true},{id:7,available:true}]};c.state.aiMenu={result:{items:[{storeId:1,productId:1,ingredientIds:[4,7],price:89000,calories:370}]}};
 await c.chooseAiMenu(0);assert.equal(opened[0],1);assert.equal(opened[2],'cart');assert.deepEqual(Array.from(opened[3]),[4,7]);
});
test('missing ingredients stop accepting an outdated suggestion',async()=>{
 let opened=false;const c=context({requireClient:(ok,text)=>{if(!ok)throw Error(text);},reloadData:async()=>{},product:()=>({id:1,storeId:1,stock:2,available:true}),openBuilder:()=>{opened=true;}});
 c.state.data={ingredients:[{id:4,available:false}]};c.state.aiMenu={result:{items:[{storeId:1,productId:1,ingredientIds:[4]}]}};
 await assert.rejects(c.chooseAiMenu(0),/Nguyên liệu vừa thay đổi/);assert.equal(opened,false);
});

function chatContext(api){
 const c=context({api,store:()=>({name:'UTEB Võ Văn Ngân'}),FormData:function(form){return {get:()=>form.message};},requireClient:(ok,text)=>{if(!ok)throw Error(text);}});
 c.aiState().available=true;return c;
}
test('chat keeps successful messages and passes recent user context for a follow-up',async()=>{
 const calls=[];const c=chatContext(async(path,method,body)=>{calls.push(body);return {message:'Các món phù hợp',items:[],criteria:[]};});
 await c.submitAiMenu({message:'Thèm gà'});await c.submitAiMenu({message:'Không cơm nữa'});
 assert.deepEqual(Array.from(calls[1].previousMessages),['Thèm gà']);assert.equal(c.aiState().turns.length,2);
 assert.equal(c.aiState().turns[0].user,'Thèm gà');assert.equal(c.aiState().turns[1].result.message,'Các món phù hợp');
 const html=c.aiChatTranscript(c.aiState());assert.ok(html.includes('Thèm gà'));assert.ok(html.includes('Không cơm nữa'));
});
test('closing the chat during an AI request preserves the eventual response',async()=>{
 let respond;const c=chatContext(()=>new Promise(resolve=>{respond=resolve;}));
 const pending=c.submitAiMenu({message:'Thèm bò'});assert.equal(c.aiState().busy,true);
 c.closeAiChat();respond({message:'Bowl bò',items:[],criteria:[]});await pending;
 assert.equal(c.aiState().turns[0].result.message,'Bowl bò');assert.equal(c.aiState().busy,false);
});
test('changing branch discards a response that belongs to the previous branch',async()=>{
 let respond;const c=chatContext(()=>new Promise(resolve=>{respond=resolve;}));
 const pending=c.submitAiMenu({message:'Thèm bò'});c.state.storeId=2;c.aiState();
 respond({message:'Old branch result',items:[],criteria:[]});await pending;
 assert.equal(c.aiState().turns.length,0);assert.equal(c.aiState().result,null);assert.equal(c.aiState().storeId,2);
});
test('unavailable AI cannot send but does not clear the prepared message',async()=>{
 let called=false;const c=chatContext(async()=>{called=true;});c.aiState().available=false;c.aiState().draft='Mình ăn chay';
 await assert.rejects(c.submitAiMenu({message:'Mình ăn chay'}),/chưa sẵn sàng/);
 assert.equal(called,false);assert.equal(c.aiState().draft,'Mình ăn chay');assert.equal(c.aiState().turns.length,0);
});
test('each older recommendation opens its own ingredients rather than the latest response',async()=>{
 let selected;
 const c=context({requireClient:(ok,text)=>{if(!ok)throw Error(text);},reloadData:async()=>{},product:id=>({id,storeId:1,available:true,stock:2}),openBuilder:(id,edit,target,ids)=>{selected={id,ids};},calculateBuilder:()=>({price:89000,kcal:370}),toast:()=>{}});
 c.state.data={ingredients:[{id:4,available:true}]};
 c.state.aiMenu={storeId:1,turns:[{id:1,result:{items:[{storeId:1,productId:10,ingredientIds:[4],price:89000,calories:370}]}}],result:{items:[{storeId:1,productId:20,ingredientIds:[],price:89000,calories:370}]}};
 await c.chooseAiMenu(0,1);assert.equal(selected.id,10);assert.deepEqual(Array.from(selected.ids),[4]);
});
test('follow-up carries both the assistant reply and ordered dish names',async()=>{
 const calls=[];const c=chatContext(async(path,method,body)=>{calls.push(body);return {message:'Hai lựa chọn hợp khẩu vị',items:[{name:'Bowl Gà',customization:'Gà nướng, phô mai'}],criteria:[]};});
 await c.submitAiMenu({message:'Thèm gà với phô mai'});await c.submitAiMenu({message:'Món đó có cay không?'});
 assert.equal(calls[1].conversation[0].answer,'Hai lựa chọn hợp khẩu vị');assert.match(calls[1].conversation[0].dishes[0],/Bowl Gà.*phô mai/);
});
test('group result shows total price and transfers quantity to the normal builder',async()=>{
 let opened;const c=context({requireClient:(ok,message)=>{if(!ok)throw Error(message);},reloadData:async()=>{},product:()=>({id:1,storeId:1,available:true,stock:3}),openBuilder:(...args)=>{opened=args;},calculateBuilder:()=>({price:89000,kcal:700}),toast:()=>{}});
 c.state.data={ingredients:[{id:4,available:true}]};c.state.aiMenu={storeId:1,result:{items:[{productId:1,storeId:1,name:'Gà',quantity:2,price:89000,totalPrice:178000,calories:700,showCalories:false,ingredientIds:[4]}]}};
 const html=c.aiMenuResults(c.state.aiMenu.result);assert.match(html,/178000đ \/ 2 phần/);assert.doesNotMatch(html,/700 kcal/);await c.chooseAiMenu(0);assert.equal(opened[4],2);
});
test('a group recommendation cannot be accepted after stock falls below the requested portions',async()=>{
 let opened=false;const c=context({requireClient:(ok,message)=>{if(!ok)throw Error(message);},reloadData:async()=>{},product:()=>({id:1,storeId:1,available:true,stock:1}),openBuilder:()=>{opened=true;}});
 c.state.data={ingredients:[]};c.state.aiMenu={storeId:1,result:{items:[{productId:1,storeId:1,quantity:2,ingredientIds:[]}]}};
 await assert.rejects(c.chooseAiMenu(0),/tồn kho/);assert.equal(opened,false);
});
test('long factual replies remain visible but are bounded in the next conversation request',async()=>{
 const calls=[];const full='Thông tin chi nhánh '.repeat(160);const c=chatContext(async(path,method,body)=>{calls.push(body);return {message:full,items:[],criteria:[]};});
 await c.submitAiMenu({message:'Danh sách các chi nhánh'});await c.submitAiMenu({message:'Giờ mở cửa?'});
 assert.equal(c.aiState().turns[0].result.message,full);assert.equal(calls[1].conversation[0].answer.length,2000);
});
