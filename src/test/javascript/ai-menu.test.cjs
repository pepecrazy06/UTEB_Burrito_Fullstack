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
